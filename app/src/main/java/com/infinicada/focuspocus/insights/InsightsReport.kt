package com.infinicada.focuspocus.insights

import com.infinicada.focuspocus.BlockEvent
import com.infinicada.focuspocus.FocusSession
import com.infinicada.focuspocus.TrialEngine
import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.model.LedgerKind
import com.infinicada.focuspocus.model.ManaDay
import com.infinicada.focuspocus.model.ManaLedgerEntry
import com.infinicada.focuspocus.model.Perk
import com.infinicada.focuspocus.model.WellDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The ranges Insights offers; [days] counts today. */
enum class InsightsRange(val days: Int) { TODAY(1), WEEK(7), MONTH(30) }

/** What started a focus session, as Insights groups it. */
enum class FocusSource { MANUAL, RITUAL, TALISMAN }

/**
 * Everything the Insights screen shows for one range, computed from stored
 * history without Android, so it unit-tests. Per-day lists run oldest first
 * and line up with [days].
 */
data class InsightsReport(
    val days: List<String>,
    val well: Well,
    val reflexes: Reflexes,
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
        val perApp: List<Pair<String, Int>>
    ) {
        val averagePactMinutes: Int get() = if (pacts == 0) 0 else (totalMinutes + refundedMinutes) / pacts
    }

    data class AppOpens(val packageName: String, val opens: Int, val reflexOpens: Int)

    data class Reflexes(
        val opens: Int,
        val reflexOpens: Int,
        val reflexesPerDay: List<Int>,
        /** Most opened first. */
        val perApp: List<AppOpens>
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
        val recentSessions: List<FocusSession>
    ) {
        val totalMinutes: Int get() = minutesBySource.values.sum()
        val averageSessionMinutes: Int get() = if (sessions == 0) 0 else totalMinutes / sessions
    }

    data class Mana(
        val earnedPerDay: List<Long>,
        val earned: Long,
        val spent: Long,
        val timedSessions: Long,
        val openEndedSessions: Long,
        val productive: Long,
        val trials: Long,
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

            val wellInRange = days.map { wellDays[it] ?: WellDay() }
            val perApp = HashMap<String, Long>()
            wellInRange.forEach { day -> day.perApp.orEmpty().forEach { (pkg, ms) -> perApp.merge(pkg, ms, Long::plus) } }
            val well = Well(
                minutesPerDay = wellInRange.map { minutes(it.drawnMillis) },
                totalMinutes = minutes(wellInRange.sumOf { it.drawnMillis }),
                pacts = wellInRange.sumOf { it.draws },
                refundedMinutes = minutes(wellInRange.sumOf { it.refundedMillis }),
                dryOpens = wellInRange.sumOf { it.dryBlocks },
                pours = wellInRange.sumOf { it.pours },
                perApp = perApp.map { (pkg, ms) -> pkg to minutes(ms) }
                    .filter { it.second > 0 }
                    .sortedByDescending { it.second }
            )

            val opensInRange = days.map { openStats[it].orEmpty() }
            val opensPerApp = HashMap<String, AppOpenStats>()
            opensInRange.forEach { day ->
                day.forEach { (pkg, s) ->
                    val prev = opensPerApp[pkg] ?: AppOpenStats()
                    opensPerApp[pkg] = prev.copy(opens = prev.opens + s.opens, reflexOpens = prev.reflexOpens + s.reflexOpens)
                }
            }
            val reflexes = Reflexes(
                opens = opensPerApp.values.sumOf { it.opens },
                reflexOpens = opensPerApp.values.sumOf { it.reflexOpens },
                reflexesPerDay = opensInRange.map { day -> day.values.sumOf { it.reflexOpens } },
                perApp = opensPerApp.map { (pkg, s) -> AppOpens(pkg, s.opens, s.reflexOpens) }
                    .filter { it.opens > 0 }
                    .sortedByDescending { it.opens }
            )

            val sessionsInRange = sessions.filter { TrialEngine.dateKeyOf(it.endTimeMillis) in inRange }
            val index = days.withIndex().associate { (i, d) -> d to i }
            val perDay = List(days.size) { IntArray(FocusSource.entries.size) }
            sessionsInRange.forEach { s ->
                val i = index[TrialEngine.dateKeyOf(s.endTimeMillis)] ?: return@forEach
                perDay[i][sourceOf(s).ordinal] += s.durationMinutes
            }
            val blocks = blockEvents.filter { TrialEngine.dateKeyOf(it.timestamp) in inRange }
            val focus = Focus(
                minutesPerDay = perDay,
                minutesBySource = FocusSource.entries.associateWith { src -> perDay.sumOf { it[src.ordinal] } },
                sessions = sessionsInRange.size,
                blockedAttempts = blocks.size,
                mostBlocked = blocks.groupingBy { it.packageName }.eachCount().toList()
                    .sortedByDescending { it.second }.take(5),
                recentSessions = sessionsInRange.sortedByDescending { it.endTimeMillis }.take(10)
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
                pourMana = manaInRange.sumOf { it.pours },
                breakMana = manaInRange.sumOf { it.breaks },
                trialsCompleted = ledgerInRange.count { it.kind == LedgerKind.TRIAL },
                pours = ledgerInRange
                    .filter { it.kind == LedgerKind.PERK && it.refId == Perk.WELL_CHANNEL.name }
                    .sortedByDescending { it.timestampMillis }
            )

            return InsightsReport(days, well, reflexes, focus, mana)
        }

        private fun minutes(millis: Long): Int = (millis / 60_000L).toInt()
    }
}
