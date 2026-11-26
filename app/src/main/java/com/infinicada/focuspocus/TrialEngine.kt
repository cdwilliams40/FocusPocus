package com.infinicada.focuspocus

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
        Template("daily_timed_minutes", TrialType.FOCUS_MINUTES, TrialPeriod.DAILY, listOf(30, 45, 60), 50L),
        Template("daily_start_timed", TrialType.START_TIMED_SESSION, TrialPeriod.DAILY, listOf(1), 40L),
        Template("daily_well_under", TrialType.WELL_UNDER, TrialPeriod.DAILY, listOf(1), 60L, listOf(10, 15, 20)),
        Template("daily_productive", TrialType.PRODUCTIVE_MINUTES, TrialPeriod.DAILY, listOf(30, 60), 40L),
        Template("daily_substitute_wins", TrialType.SUBSTITUTE_WINS, TrialPeriod.DAILY, listOf(2, 3), 50L)
    )

    val WEEKLY_TEMPLATES = listOf(
        Template("weekly_timed_minutes", TrialType.FOCUS_MINUTES, TrialPeriod.WEEKLY, listOf(150, 240, 360), 200L),
        Template("weekly_well_under", TrialType.WELL_UNDER, TrialPeriod.WEEKLY, listOf(1), 200L, listOf(60, 90, 120)),
        Template("weekly_substitute_wins", TrialType.SUBSTITUTE_WINS, TrialPeriod.WEEKLY, listOf(5, 8), 160L)
    )

    const val DAILY_SLOTS = 2
    const val WEEKLY_SLOTS = 1

    /**
     * Which templates may be drawn right now. Templates that can't be
     * measured, or would be won without trying (no Well apps, no productive
     * apps, no configured substitutes), are left out rather than shown as free mana.
     */
    data class Eligibility(
        val hasWellApps: Boolean,
        val canMeasureProductive: Boolean,
        val hasSubstitutes: Boolean = false
    ) {
        fun allows(template: Template): Boolean = when (template.type) {
            TrialType.FOCUS_MINUTES, TrialType.START_TIMED_SESSION -> true
            TrialType.WELL_UNDER -> hasWellApps
            TrialType.PRODUCTIVE_MINUTES -> canMeasureProductive
            TrialType.SUBSTITUTE_WINS -> hasSubstitutes
            TrialType.COMPLETE_SESSIONS,
            TrialType.COMPLETE_RITUAL,
            TrialType.STAY_UNDER_LIMITS,
            TrialType.NO_REFLEX_OPENS -> false
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
     * Advances session-driven trials for one completed session. Only timed
     * sessions count: an open-ended ritual running every day would otherwise
     * win them without effort. Returns the updated list and the trials that
     * crossed their target just now.
     */
    fun advanceForSession(
        trials: List<Trial>,
        durationMinutes: Int,
        timed: Boolean,
        startedByHand: Boolean
    ): Pair<List<Trial>, List<Trial>> = advance(trials) { trial ->
        when (trial.type) {
            TrialType.FOCUS_MINUTES ->
                if (timed) trial.copy(progress = trial.progress + durationMinutes) else trial
            TrialType.START_TIMED_SESSION ->
                if (timed && startedByHand) trial.copy(progress = trial.progress + 1) else trial
            else -> trial
        }
    }

    /** Sets productive-minute trials to [minutesToday] (daily) or adds the day's growth (weekly). */
    fun advanceForProductive(
        trials: List<Trial>,
        minutesToday: Int,
        addedMinutes: Int
    ): Pair<List<Trial>, List<Trial>> = advance(trials) { trial ->
        if (trial.type != TrialType.PRODUCTIVE_MINUTES) trial
        else when (trial.period) {
            TrialPeriod.DAILY -> trial.copy(progress = maxOf(trial.progress, minutesToday))
            TrialPeriod.WEEKLY -> trial.copy(progress = trial.progress + addedMinutes)
        }
    }

    /** Advances substitute-win trials by one win. */
    fun advanceForSubstituteWin(
        trials: List<Trial>
    ): Pair<List<Trial>, List<Trial>> = advance(trials) { trial ->
        if (trial.type == TrialType.SUBSTITUTE_WINS) trial.copy(progress = trial.progress + 1) else trial
    }

    private fun advance(trials: List<Trial>, step: (Trial) -> Trial): Pair<List<Trial>, List<Trial>> {
        val newlyCompleted = mutableListOf<Trial>()
        val updated = trials.map { trial ->
            if (trial.claimed || trial.completed) return@map trial
            val advanced = step(trial)
            val capped = if (advanced.progress > advanced.target) advanced.copy(progress = advanced.target) else advanced
            if (capped.completed && !trial.completed) newlyCompleted += capped
            capped
        }
        return updated to newlyCompleted
    }

    /**
     * Judges an ended "at most" trial from recorded history: WELL_UNDER
     * succeeds when the period's net Well draws stayed at or under
     * [Trial.param] minutes.
     */
    fun judgeEndedTrial(
        trial: Trial,
        wellDrawnMinutesByDay: Map<String, Int>
    ): Trial {
        fun inPeriod(day: String) = when (trial.period) {
            TrialPeriod.DAILY -> day == trial.periodKey
            TrialPeriod.WEEKLY -> weekKeyForDay(day) == trial.periodKey
        }
        return when (trial.type) {
            TrialType.WELL_UNDER -> {
                val drawn = wellDrawnMinutesByDay.filterKeys(::inPeriod).values.sum()
                if (drawn <= trial.param) trial.copy(progress = trial.target) else trial
            }
            else -> trial
        }
    }

    /** "yyyyMMdd" day key for an epoch timestamp (matches DayKey.today). */
    fun dateKeyOf(timestampMillis: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestampMillis }
        // Locale.ROOT so a non-Latin default locale can't emit digits that the
        // ASCII-only key parsers (weekKeyForDay's BASIC_ISO_DATE, retention cutoffs)
        // reject — matches DayKey.today.
        return String.format(
            java.util.Locale.ROOT, "%04d%02d%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }
}
