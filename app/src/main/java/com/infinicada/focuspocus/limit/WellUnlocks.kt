package com.infinicada.focuspocus.limit

import android.content.SharedPreferences
import com.infinicada.focuspocus.model.ConditionalUnlock

/**
 * Conditional unlocks under the Mana Well.
 *
 * A satisfied rule normally frees the apps it names for the rest of the day —
 * past the pact gate, the seal and the daily backstop alike. For a pact that
 * draws from the Well that would be a way around the shared pool, so there
 * the reward changes shape: the rule no longer frees the app, and instead
 * pours [GRANT_MINUTES] into the Well the first time each day it is found
 * satisfied. The earn-it idea stays; the time still comes out of the pool.
 *
 * Only Well-bound pact apps are affected. Wards, exempt pacts and
 * enchantments named by the same rule are unlocked exactly as before.
 */
object WellUnlocks {

    /** Minutes a satisfied rule adds to the Well, once per rule per day. */
    const val GRANT_MINUTES = 10

    /**
     * The rules that pay into the Well rather than free apps: those that name
     * at least one Well-bound pact app.
     */
    fun payingRules(
        rules: List<ConditionalUnlock>,
        isWellBound: (String) -> Boolean
    ): List<ConditionalUnlock> = rules.filter { rule ->
        rule.requiredMinutes > 0 && rule.effectiveUnlockedTimeLimitApps.any(isWellBound)
    }

    /**
     * Pays every paying rule that is satisfied and hasn't paid yet today, and
     * returns the minutes that went into the Well. [usedMillisToday] answers
     * "how long was this package in the foreground today"; it is only asked
     * about rules that could still pay, so a day with nothing pending costs
     * no usage query at all.
     *
     * A rule that finds the Well full stays unpaid and is tried again later
     * the same day ([ManaWell.creditUnlock]).
     */
    fun creditSatisfied(
        prefs: SharedPreferences,
        rules: List<ConditionalUnlock>,
        isWellBound: (String) -> Boolean,
        usedMillisToday: (String) -> Long,
        now: Long = System.currentTimeMillis(),
        todayKey: String = DayKey.today()
    ): Int {
        if (rules.isEmpty() || !ManaWell.isEnabled(prefs, now)) return 0
        val paid = ManaWell.creditedUnlocks(prefs, todayKey)
        return payingRules(rules, isWellBound)
            .filter { it.id !in paid }
            .filter { usedMillisToday(it.requiredAppPackage) >= it.requiredMinutes * WellMath.MINUTE_MS }
            .sumOf { ManaWell.creditUnlock(prefs, it.id, GRANT_MINUTES, now, todayKey) }
    }
}
