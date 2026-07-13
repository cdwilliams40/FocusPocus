package com.infinicada.focuspocus

import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.model.Trial
import com.infinicada.focuspocus.model.TrialPeriod
import com.infinicada.focuspocus.model.TrialType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Random

/**
 * Pure trial (challenge) mechanics: deterministic slate drawing, session-driven
 * progress, and end-of-period judgment. All inputs are explicit values so the
 * whole engine unit-tests without Android; [Progression] adapts prefs state in
 * and out.
 */
object TrialEngine {

    data class Template(
        val key: String,
        val type: TrialType,
        val period: TrialPeriod,
        val targets: List<Int>,
        val rewardMana: Long,
        /** Choices for [Trial.param] where the type uses one. */
        val paramChoices: List<Int> = listOf(0)
    )

    val DAILY_TEMPLATES = listOf(
        Template("daily_sessions", TrialType.COMPLETE_SESSIONS, TrialPeriod.DAILY, listOf(2, 3), 40L),
        Template("daily_minutes", TrialType.FOCUS_MINUTES, TrialPeriod.DAILY, listOf(60, 90, 120), 50L),
        Template("daily_ritual", TrialType.COMPLETE_RITUAL, TrialPeriod.DAILY, listOf(1), 40L),
        Template("daily_under_limits", TrialType.STAY_UNDER_LIMITS, TrialPeriod.DAILY, listOf(1), 60L)
    )

    val WEEKLY_TEMPLATES = listOf(
        Template("weekly_minutes", TrialType.FOCUS_MINUTES, TrialPeriod.WEEKLY, listOf(300, 450, 600), 200L),
        Template("weekly_reflex", TrialType.NO_REFLEX_OPENS, TrialPeriod.WEEKLY, listOf(1), 150L, listOf(10, 15, 20))
    )

    const val DAILY_SLOTS = 2
    const val WEEKLY_SLOTS = 1

    /**
     * Which templates may be drawn right now. Templates that can't be measured
     * (no usage permission) or are vacuously true (no limits configured, no
     * tracked apps) are excluded rather than shown as free mana.
     */
    data class Eligibility(
        val hasSchedules: Boolean,
        val canJudgeLimits: Boolean,
        val hasReflexHistory: Boolean
    ) {
        fun allows(template: Template): Boolean = when (template.type) {
            TrialType.COMPLETE_SESSIONS, TrialType.FOCUS_MINUTES -> true
            TrialType.COMPLETE_RITUAL -> hasSchedules
            TrialType.STAY_UNDER_LIMITS -> canJudgeLimits
            TrialType.NO_REFLEX_OPENS -> hasReflexHistory
        }
    }

    private val BASIC_DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

    /** ISO week key ("2026-W28", Monday start) for a "yyyyMMdd" day key. */
    fun weekKeyForDay(dayKey: String): String = try {
        val date = LocalDate.parse(dayKey, BASIC_DATE)
        val wf = WeekFields.ISO
        // Locale.ROOT: default-locale digits (e.g. Persian) would change the
        // key when the device language changes, orphaning stored weekly keys.
        String.format(
            java.util.Locale.ROOT, "%04d-W%02d",
            date.get(wf.weekBasedYear()), date.get(wf.weekOfWeekBasedYear())
        )
    } catch (e: Exception) {
        dayKey
    }

    /** A trial has ended when its period no longer matches the current keys. */
    fun isEnded(trial: Trial, dailyKey: String, weekKey: String): Boolean = when (trial.period) {
        TrialPeriod.DAILY -> trial.periodKey != dailyKey
        TrialPeriod.WEEKLY -> trial.periodKey != weekKey
    }

    /**
     * Keeps still-active trials and fills empty daily/weekly slots with a
     * deterministic draw ([Random] seeded by the period key, so every rotation
     * on a given day draws the same slate regardless of process restarts).
     * Slots are only filled when a period has no active trials at all —
     * mid-period eligibility changes take effect at the next rotation.
     */
    fun drawSlate(dailyKey: String, weekKey: String, active: List<Trial>, eligibility: Eligibility): List<Trial> {
        val result = active.toMutableList()
        if (active.none { it.period == TrialPeriod.DAILY }) {
            result += draw(DAILY_TEMPLATES.filter { eligibility.allows(it) }, DAILY_SLOTS, dailyKey)
        }
        if (active.none { it.period == TrialPeriod.WEEKLY }) {
            result += draw(WEEKLY_TEMPLATES.filter { eligibility.allows(it) }, WEEKLY_SLOTS, weekKey)
        }
        return result
    }

    private fun draw(eligible: List<Template>, slots: Int, periodKey: String): List<Trial> {
        if (eligible.isEmpty()) return emptyList()
        // java.util.Random's algorithm is specified, so the same seed draws the
        // same slate on every device and after every process restart.
        val rng = Random(periodKey.hashCode().toLong())
        val pool = eligible.toMutableList()
        val drawn = mutableListOf<Trial>()
        repeat(minOf(slots, pool.size)) {
            val template = pool.removeAt(rng.nextInt(pool.size))
            drawn += Trial(
                id = "${template.key}_$periodKey",
                type = template.type,
                target = template.targets[rng.nextInt(template.targets.size)],
                period = template.period,
                periodKey = periodKey,
                rewardMana = template.rewardMana,
                param = template.paramChoices[rng.nextInt(template.paramChoices.size)]
            )
        }
        return drawn
    }

    /**
     * Advances session-driven trials for one completed session. Returns the
     * updated list and the trials that crossed their target just now.
     */
    fun advanceForSession(
        trials: List<Trial>,
        durationMinutes: Int,
        fromRitual: Boolean
    ): Pair<List<Trial>, List<Trial>> {
        val newlyCompleted = mutableListOf<Trial>()
        val updated = trials.map { trial ->
            if (trial.claimed || trial.completed) return@map trial
            val advanced = when (trial.type) {
                TrialType.COMPLETE_SESSIONS -> trial.copy(progress = trial.progress + 1)
                TrialType.FOCUS_MINUTES -> trial.copy(progress = trial.progress + durationMinutes)
                TrialType.COMPLETE_RITUAL ->
                    if (fromRitual) trial.copy(progress = trial.progress + 1) else trial
                // Judged at rollover, never advanced by sessions.
                TrialType.STAY_UNDER_LIMITS, TrialType.NO_REFLEX_OPENS -> trial
            }
            val capped = if (advanced.progress > advanced.target) advanced.copy(progress = advanced.target) else advanced
            if (capped.completed && !trial.completed) newlyCompleted += capped
            capped
        }
        return updated to newlyCompleted
    }

    /**
     * Judges an ended day/week-scoped trial from recorded history:
     * STAY_UNDER_LIMITS succeeds when no "Time Limit" block event landed on the
     * trial's day (and limits were actually configured); NO_REFLEX_OPENS when
     * the week's reflex-open total stayed at or under [Trial.param].
     */
    fun judgeEndedTrial(
        trial: Trial,
        blockEvents: List<BlockEvent>,
        reflexDays: Map<String, Map<String, AppOpenStats>>,
        limitsConfigured: Boolean
    ): Trial = when (trial.type) {
        TrialType.STAY_UNDER_LIMITS -> {
            val breached = blockEvents.any {
                it.blockerName == TIME_LIMIT_BLOCK_REASON && dateKeyOf(it.timestamp) == trial.periodKey
            }
            if (limitsConfigured && !breached) trial.copy(progress = trial.target) else trial
        }
        TrialType.NO_REFLEX_OPENS -> {
            val reflexes = reflexDays
                .filterKeys { weekKeyForDay(it) == trial.periodKey }
                .values.sumOf { day -> day.values.sumOf { it.reflexOpens } }
            if (reflexes <= trial.param) trial.copy(progress = trial.target) else trial
        }
        else -> trial
    }

    /** "yyyyMMdd" day key for an epoch timestamp (matches SessionCooldownManager.todayString). */
    fun dateKeyOf(timestampMillis: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestampMillis }
        return "%04d%02d%02d".format(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    /** The blockerName the accessibility service records for daily-limit blocks. */
    const val TIME_LIMIT_BLOCK_REASON = "Time Limit"
}
