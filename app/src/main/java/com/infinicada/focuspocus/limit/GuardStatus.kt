package com.infinicada.focuspocus.limit

import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.model.AppTimeLimit
import com.infinicada.focuspocus.model.PactGroup

/**
 * Read-only enforcement snapshot for one guarded app, combined from the pact
 * allowance store, the cooldown store, and today's usage stats. Produced by
 * SpellbookViewModel.getGuardLiveState() for the Pacts dashboard.
 */
data class GuardLiveState(
    /** Epoch millis when the active pact allowance expires, or null when none. */
    val allowanceExpiryMillis: Long? = null,
    /** Epoch millis when the active seal (cooldown) lifts, or null when none. */
    val cooldownExpiryMillis: Long? = null,
    /** Foreground minutes today, for daily-limit progress. */
    val usedMinutesToday: Int = 0
)

/** Display state of a guarded app. Declaration order is the dashboard sort order. */
enum class GuardState { SEALED, PACT_ACTIVE, OVER_LIMIT, QUIET }

/** Aggregate counts for the dashboard headline. */
data class GuardHeadline(
    val sealedCount: Int,
    val pactActiveCount: Int,
    val overLimitCount: Int
)

/** Today's open/reflex rollup across pact-gated apps (wards aren't reflex-tracked). */
data class TodayRollup(val opens: Int, val reflexOpens: Int)

/** One card on the Pacts dashboard. */
sealed class GuardRow {

    /** An explicitly configured app — a pact or a ward, per [AppTimeLimit.pactModeEnabled]. */
    data class App(
        val packageName: String,
        val config: AppTimeLimit,
        val state: GuardState,
        /** Whole minutes until the seal lifts; 0 unless [state] is [GuardState.SEALED]. */
        val sealMinutesLeft: Int = 0,
        /** Whole minutes left on the allowance; 0 unless [state] is [GuardState.PACT_ACTIVE]. */
        val allowanceMinutesLeft: Int = 0,
        val usedMinutesToday: Int = 0,
        val opensToday: Int = 0,
        val reflexesToday: Int = 0
    ) : GuardRow()

    /**
     * A pact circle: one pact configuration covering a blacklist enchantment's
     * live membership. Apps with an explicit per-app config are excluded from
     * [memberPackages] — the explicit config always wins (the enforcement
     * precedence in MyAccessibilityService.resolvePactConfig).
     */
    data class Circle(
        val group: PactGroup,
        val memberPackages: List<String>,
        /**
         * Members whose pact gate is requestable right now — quiet ones, i.e.
         * not sealed, no running allowance, under any daily backstop. Feeds the
         * dashboard's request-time picker.
         */
        val quietMemberPackages: List<String>,
        val sealedCount: Int,
        val pactActiveCount: Int,
        val overLimitCount: Int,
        val opensToday: Int,
        val reflexesToday: Int
    ) : GuardRow()
}

/**
 * Pure resolution and ordering logic for the Pacts dashboard, kept free of
 * Android types so it is unit-testable.
 */
object GuardStatus {

    /** The display state for one config given its live enforcement snapshot. */
    fun resolveState(config: AppTimeLimit, live: GuardLiveState, now: Long): GuardState = when {
        (live.cooldownExpiryMillis ?: 0L) > now -> GuardState.SEALED
        config.pactModeEnabled && (live.allowanceExpiryMillis ?: 0L) > now -> GuardState.PACT_ACTIVE
        config.dailyLimitMinutes > 0 && live.usedMinutesToday >= config.dailyLimitMinutes ->
            GuardState.OVER_LIMIT
        else -> GuardState.QUIET
    }

    /** Whole minutes until [expiryMillis], rounded up; 0 once passed. */
    fun minutesUntil(expiryMillis: Long, now: Long): Int {
        val msLeft = expiryMillis - now
        return if (msLeft <= 0L) 0 else ((msLeft + 59_999) / 60_000).toInt()
    }

    /**
     * Live membership of [group]'s circle: the enchantment's apps minus those
     * with an explicit per-app config — the explicit config always wins,
     * whatever its style (the resolvePactConfig precedence).
     */
    fun circleMemberPackages(
        group: PactGroup,
        blockers: List<Blocker>,
        configs: Map<String, AppTimeLimit>
    ): List<String> =
        (blockers.find { it.name == group.blockerName }?.effectiveApps ?: emptySet())
            .filter { it !in configs }
            .sorted()

    /**
     * Every package currently gated by a pact: explicit pact-style configs plus
     * live circle members. The single home of the precedence rule for UI
     * consumers (Boons, the dashboard rollup, the widget).
     */
    fun pactGatedPackages(
        configs: Map<String, AppTimeLimit>,
        groups: List<PactGroup>,
        blockers: List<Blocker>
    ): Set<String> =
        configs.filterValues { it.pactModeEnabled }.keys +
            groups.flatMap { circleMemberPackages(it, blockers, configs) }

    /**
     * The pact settings governing [packageName], or null when it isn't
     * pact-gated — the UI-side mirror of the enforcement layer's
     * resolvePactConfig precedence: an explicit per-app config wins outright
     * (a plain ward config means never group-gated), otherwise live membership
     * in a pact circle applies.
     */
    fun effectivePactConfig(
        packageName: String,
        configs: Map<String, AppTimeLimit>,
        groups: List<PactGroup>,
        blockers: List<Blocker>
    ): AppTimeLimit? {
        configs[packageName]?.let { return if (it.pactModeEnabled) it else null }
        return groups
            .firstOrNull { packageName in circleMemberPackages(it, blockers, configs) }
            ?.toAppTimeLimit(packageName)
    }

    /**
     * Builds the dashboard's card list: one row per explicit config plus one per
     * pact circle, ordered most-urgent first — sealed, then active pacts, then
     * spent limits, then quiet rows by today's opens descending, with the
     * display name (from [names], falling back to the package name) as the
     * final tiebreaker.
     */
    fun buildRows(
        configs: Map<String, AppTimeLimit>,
        groups: List<PactGroup>,
        blockers: List<Blocker>,
        liveStates: Map<String, GuardLiveState>,
        openStats: Map<String, AppOpenStats>,
        names: Map<String, String>,
        now: Long
    ): List<GuardRow> {
        val appRows = configs.map { (pkg, config) ->
            val live = liveStates[pkg] ?: GuardLiveState()
            val stats = openStats[pkg] ?: AppOpenStats()
            val state = resolveState(config, live, now)
            GuardRow.App(
                packageName = pkg,
                config = config,
                state = state,
                sealMinutesLeft = if (state == GuardState.SEALED) {
                    minutesUntil(live.cooldownExpiryMillis ?: 0L, now)
                } else 0,
                allowanceMinutesLeft = if (state == GuardState.PACT_ACTIVE) {
                    minutesUntil(live.allowanceExpiryMillis ?: 0L, now)
                } else 0,
                usedMinutesToday = live.usedMinutesToday,
                opensToday = stats.opens,
                reflexesToday = stats.reflexOpens
            )
        }

        val circleRows = groups.map { group ->
            val members = circleMemberPackages(group, blockers, configs)
            val quietMembers = mutableListOf<String>()
            var sealedCount = 0
            var pactActiveCount = 0
            var overLimitCount = 0
            var opens = 0
            var reflexes = 0
            members.forEach { pkg ->
                val live = liveStates[pkg] ?: GuardLiveState()
                when (resolveState(group.toAppTimeLimit(pkg), live, now)) {
                    GuardState.SEALED -> sealedCount++
                    GuardState.PACT_ACTIVE -> pactActiveCount++
                    GuardState.OVER_LIMIT -> overLimitCount++
                    GuardState.QUIET -> quietMembers.add(pkg)
                }
                val stats = openStats[pkg] ?: AppOpenStats()
                opens += stats.opens
                reflexes += stats.reflexOpens
            }
            GuardRow.Circle(
                group, members, quietMembers,
                sealedCount, pactActiveCount, overLimitCount, opens, reflexes
            )
        }

        return (appRows + circleRows).sortedWith(
            compareBy({ statePriority(it) }, { -opensOf(it) }, { sortName(it, names) })
        )
    }

    /** Headline counts across every row, circles included. */
    fun headlineCounts(rows: List<GuardRow>): GuardHeadline {
        var sealedCount = 0
        var pactActiveCount = 0
        var overLimitCount = 0
        rows.forEach { row ->
            when (row) {
                is GuardRow.App -> when (row.state) {
                    GuardState.SEALED -> sealedCount++
                    GuardState.PACT_ACTIVE -> pactActiveCount++
                    GuardState.OVER_LIMIT -> overLimitCount++
                    GuardState.QUIET -> {}
                }
                is GuardRow.Circle -> {
                    sealedCount += row.sealedCount
                    pactActiveCount += row.pactActiveCount
                    overLimitCount += row.overLimitCount
                }
            }
        }
        return GuardHeadline(sealedCount, pactActiveCount, overLimitCount)
    }

    /**
     * Today's opens/reflexes summed over pact-gated rows only — ward apps are
     * not reflex-tracked, so counting them would just dilute the number.
     */
    fun todayRollup(rows: List<GuardRow>): TodayRollup {
        var opens = 0
        var reflexes = 0
        rows.forEach { row ->
            when (row) {
                is GuardRow.App -> if (row.config.pactModeEnabled) {
                    opens += row.opensToday
                    reflexes += row.reflexesToday
                }
                is GuardRow.Circle -> {
                    opens += row.opensToday
                    reflexes += row.reflexesToday
                }
            }
        }
        return TodayRollup(opens, reflexes)
    }

    /** True when any row is pact-gated, i.e. the opens/reflex rollup means something. */
    fun hasPactRows(rows: List<GuardRow>): Boolean = rows.any { row ->
        when (row) {
            is GuardRow.App -> row.config.pactModeEnabled
            is GuardRow.Circle -> true
        }
    }

    private fun statePriority(row: GuardRow): Int = when (row) {
        is GuardRow.App -> row.state.ordinal
        is GuardRow.Circle -> when {
            row.sealedCount > 0 -> GuardState.SEALED.ordinal
            row.pactActiveCount > 0 -> GuardState.PACT_ACTIVE.ordinal
            row.overLimitCount > 0 -> GuardState.OVER_LIMIT.ordinal
            else -> GuardState.QUIET.ordinal
        }
    }

    private fun opensOf(row: GuardRow): Int = when (row) {
        is GuardRow.App -> row.opensToday
        is GuardRow.Circle -> row.opensToday
    }

    private fun sortName(row: GuardRow, names: Map<String, String>): String = when (row) {
        is GuardRow.App -> (names[row.packageName] ?: row.packageName).lowercase()
        is GuardRow.Circle -> row.group.blockerName.lowercase()
    }
}
