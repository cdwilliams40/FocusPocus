package com.infinicada.focuspocus.insights

import com.infinicada.focuspocus.BlockEvent
import com.infinicada.focuspocus.FocusSession
import com.infinicada.focuspocus.TrialEngine
import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.model.LedgerKind
import com.infinicada.focuspocus.model.ManaDay
import com.infinicada.focuspocus.model.ManaLedgerEntry
import com.infinicada.focuspocus.model.PactIntention
import com.infinicada.focuspocus.model.Perk
import com.infinicada.focuspocus.model.WellDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

/** The ranges Insights offers; [days] counts today. */
enum class InsightsRange(val days: Int) { TODAY(1), WEEK(7), MONTH(30) }

/** What started a focus session, as Insights groups it. */
enum class FocusSource { MANUAL, RITUAL, TALISMAN }

/** Coarse 4-bucket time-of-day breakdown for Well draws and focus minutes. */
enum class TimeOfDayBucket {
    MORNING,    // 05:00–11:59
    AFTERNOON,  // 12:00–16:59
    EVENING,    // 17:00–21:59
    LATE_NIGHT; // 22:00–04:59

    companion object {
        fun forHour(hour: Int): TimeOfDayBucket = when (hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..21 -> EVENING
            else -> LATE_NIGHT
        }
    }
}

/**
 * Everything the Insights screen shows for one range, computed from stored
 * history without Android, so it unit-tests. Per-day lists run oldest first
 * and line up with [days].
 */
data class InsightsReport(
    val days: List<String>,
    val well: Well,
    val opens: Opens,
    val focus: Focus,
    val mana: Mana
) {
    data class Well(
        val minutesPerDay: List<Int>,
        val totalMinutes: Int,
        val pacts: Int,
        val refundedMinutes: Int,
        val dryOpens: Int,
        val pours: Int,
        /** Package → minutes, most first. */
        val perApp: List<Pair<String, Int>>,
        val substituteWins: Int = 0,
        val intentions: Map<PactIntention, Int> = emptyMap(),
        val minutesByTimeOfDay: Map<TimeOfDayBucket, Int> = emptyMap(),
        /** Total Well minutes in the immediately preceding equal-length period, if any data existed. */
        val previousPeriodMinutes: Int? = null
    ) {
        val averagePactMinutes: Int get() = if (pacts == 0) 0 else (totalMinutes + refundedMinutes) / pacts
        val deltaMinutes: Int? get() = previousPeriodMinutes?.let { totalMinutes - it }
    }

    data class Opens(
        val totalOpens: Int,
        val opensPerDay: List<Int>,
        /** Package → opens, most opened first. */
        val perApp: List<Pair<String, Int>>
    )

    data class Focus(
        /** Per day, minutes by [FocusSource] ordinal. */
        val minutesPerDay: List<IntArray>,
        val minutesBySource: Map<FocusSource, Int>,
        val sessions: Int,
        val blockedAttempts: Int,
        /** Package → blocked attempts, most first, top five. */
        val mostBlocked: List<Pair<String, Int>>,
        /** Newest first, at most ten. */
        val recentSessions: List<FocusSession>,
        val minutesByTimeOfDay: Map<TimeOfDayBucket, Int> = emptyMap(),
        /** Total focus minutes in the immediately preceding equal-length period, if any data existed. */
        val previousPeriodMinutes: Int? = null
    ) {
        val totalMinutes: Int get() = minutesBySource.values.sum()
        val averageSessionMinutes: Int get() = if (sessions == 0) 0 else totalMinutes / sessions
        val deltaMinutes: Int? get() = previousPeriodMinutes?.let { totalMinutes - it }
    }

    data class Mana(
        val earnedPerDay: List<Long>,
        val earned: Long,
        val spent: Long,
        val timedSessions: Long,
        val openEndedSessions: Long,
        val productive: Long,
        val trials: Long,
        val substitutes: Long = 0L,
        val pourMana: Long,
        val breakMana: Long,
        val trialsCompleted: Int,
        /** Each pour in range with what it cost, newest first. */
        val pours: List<ManaLedgerEntry>
    )

    companion object {
        private val BASIC: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

        /** The "yyyyMMdd" keys of [range], oldest first, ending on [today]. */
        fun daysFor(range: InsightsRange, today: String): List<String> {
            val end = LocalDate.parse(today, BASIC)
            return (range.days - 1 downTo 0).map { end.minusDays(it.toLong()).format(BASIC) }
        }

        /** The immediately preceding equal-length period before [days]. */
        fun previousPeriodDays(days: List<String>): List<String> {
            if (days.isEmpty()) return emptyList()
            val first = try {
                LocalDate.parse(days.first(), BASIC)
            } catch (_: Exception) {
                return emptyList()
            }
            return (days.size downTo 1).map { first.minusDays(it.toLong()).format(BASIC) }
        }

        fun sourceOf(session: FocusSession): FocusSource = when (session.source) {
            FocusSession.SOURCE_RITUAL -> FocusSource.RITUAL
            FocusSession.SOURCE_TALISMAN -> FocusSource.TALISMAN
            // Sessions from before 2.0 didn't record a source.
            else -> FocusSource.MANUAL
        }

        fun build(
            days: List<String>,
            wellDays: Map<String, WellDay>,
            manaDays: Map<String, ManaDay>,
            openStats: Map<String, Map<String, AppOpenStats>>,
            sessions: List<FocusSession>,
            blockEvents: List<BlockEvent>,
            ledger: List<ManaLedgerEntry>
        ): InsightsReport {
            val inRange = days.toSet()
            val priorDays = previousPeriodDays(days)
            val priorSet = priorDays.toSet()

            val wellInRange = days.map { wellDays[it] ?: WellDay() }
            val perApp = HashMap<String, Long>()
            val intentionCounts = HashMap<PactIntention, Int>()
            val wellByBucket = HashMap<TimeOfDayBucket, Int>()
            wellInRange.forEach { day ->
                day.perApp.orEmpty().forEach { (pkg, ms) -> perApp.merge(pkg, ms, Long::plus) }
                day.intentions.orEmpty().forEach { (name, count) ->
                    val parsed = PactIntention.entries.find { it.name == name }
                    if (parsed != null && count > 0) {
                        intentionCounts.merge(parsed, count, Int::plus)
                    }
                }
                day.drawsByHour.orEmpty().forEach { (hour, mins) ->
                    if (mins > 0) {
                        wellByBucket.merge(TimeOfDayBucket.forHour(hour), mins, Int::plus)
                    }
                }
            }
            val priorWellHasData = priorDays.any { it in wellDays }
            val priorWellMinutes = if (priorWellHasData) {
                minutes(priorDays.sumOf { wellDays[it]?.drawnMillis ?: 0L })
            } else null

            val well = Well(
                minutesPerDay = wellInRange.map { minutes(it.drawnMillis) },
                totalMinutes = minutes(wellInRange.sumOf { it.drawnMillis }),
                pacts = wellInRange.sumOf { it.draws },
                refundedMinutes = minutes(wellInRange.sumOf { it.refundedMillis }),
                dryOpens = wellInRange.sumOf { it.dryBlocks },
                pours = wellInRange.sumOf { it.pours },
                perApp = perApp.map { (pkg, ms) -> pkg to minutes(ms) }
                    .filter { it.second > 0 }
                    .sortedByDescending { it.second },
                substituteWins = wellInRange.sumOf { it.substituteWins },
                intentions = intentionCounts,
                minutesByTimeOfDay = wellByBucket,
                previousPeriodMinutes = priorWellMinutes
            )

            val opensInRange = days.map { openStats[it].orEmpty() }
            val opensPerApp = HashMap<String, Int>()
            opensInRange.forEach { day ->
                day.forEach { (pkg, s) ->
                    opensPerApp.merge(pkg, s.opens, Int::plus)
                }
            }
            val opens = Opens(
                totalOpens = opensPerApp.values.sum(),
                opensPerDay = opensInRange.map { day -> day.values.sumOf { it.opens } },
                perApp = opensPerApp.map { (pkg, count) -> pkg to count }
                    .filter { it.second > 0 }
                    .sortedByDescending { it.second }
            )

            val sessionsInRange = sessions.filter { TrialEngine.dateKeyOf(it.endTimeMillis) in inRange }
            val priorSessions = sessions.filter { TrialEngine.dateKeyOf(it.endTimeMillis) in priorSet }
            val index = days.withIndex().associate { (i, d) -> d to i }
            val perDay = List(days.size) { IntArray(FocusSource.entries.size) }
            val focusByBucket = HashMap<TimeOfDayBucket, Int>()
            sessionsInRange.forEach { s ->
                val i = index[TrialEngine.dateKeyOf(s.endTimeMillis)] ?: return@forEach
                perDay[i][sourceOf(s).ordinal] += s.durationMinutes
                val startHour = Calendar.getInstance().apply { timeInMillis = s.startTimeMillis }
                    .get(Calendar.HOUR_OF_DAY)
                focusByBucket.merge(TimeOfDayBucket.forHour(startHour), s.durationMinutes, Int::plus)
            }
            val blocks = blockEvents.filter {
                it.blockerName != BlockEvent.PACT_GATE_BLOCKER_NAME &&
                    TrialEngine.dateKeyOf(it.timestamp) in inRange
            }
            val focus = Focus(
                minutesPerDay = perDay,
                minutesBySource = FocusSource.entries.associateWith { src -> perDay.sumOf { it[src.ordinal] } },
                sessions = sessionsInRange.size,
                blockedAttempts = blocks.size,
                mostBlocked = blocks.groupingBy { it.packageName }.eachCount().toList()
                    .sortedByDescending { it.second }.take(5),
                recentSessions = sessionsInRange.sortedByDescending { it.endTimeMillis }.take(10),
                minutesByTimeOfDay = focusByBucket,
                previousPeriodMinutes = if (priorSessions.isNotEmpty()) priorSessions.sumOf { it.durationMinutes } else null
            )

            val manaInRange = days.map { manaDays[it] ?: ManaDay() }
            val ledgerInRange = ledger.filter { it.dateKey in inRange }
            val mana = Mana(
                earnedPerDay = manaInRange.map { it.earned },
                earned = manaInRange.sumOf { it.earned },
                spent = manaInRange.sumOf { it.spent },
                timedSessions = manaInRange.sumOf { it.timedSession },
                openEndedSessions = manaInRange.sumOf { it.openEndedSession },
                productive = manaInRange.sumOf { it.productive },
                trials = manaInRange.sumOf { it.trials },
                substitutes = manaInRange.sumOf { it.substitute },
                pourMana = manaInRange.sumOf { it.pours },
                breakMana = manaInRange.sumOf { it.breaks },
                trialsCompleted = ledgerInRange.count { it.kind == LedgerKind.TRIAL },
                pours = ledgerInRange
                    .filter { it.kind == LedgerKind.PERK && it.refId == Perk.WELL_CHANNEL.name }
                    .sortedByDescending { it.timestampMillis }
            )

            return InsightsReport(days, well, opens, focus, mana)
        }

        private fun minutes(millis: Long): Int = (millis / 60_000L).toInt()
    }
}
