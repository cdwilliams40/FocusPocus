package com.infinicada.focuspocus

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Which foreground detector is allowed to enforce.
 *
 * Both modes run the same [EnforcementEngine] and make identical blocking
 * decisions; they differ only in how they learn that an app came to the
 * foreground, and therefore in how fast they learn it. Exactly one detector is
 * live at a time — two would double-count opens and race each other on seals.
 *
 * This is a user-visible choice rather than an automatic failover on purpose.
 * Silently swapping enforcement mechanisms behind someone's back changes the
 * app's battery and latency behaviour without telling them; Protection Health
 * is where a dead detector gets reported, and switching is their call.
 */
enum class EnforcementMode {
    /**
     * Android's accessibility API pushes a window-state event the moment an app
     * takes the foreground — roughly 50 ms, fast enough that a guarded app
     * never really finishes opening. The default, and the reason the app files
     * an accessibility declaration with Play.
     */
    ACCESSIBILITY,

    /**
     * [ForegroundPollingService] reads the same fact out of UsageStats on a
     * timer instead. Detection lands in 1–2 s, so a guarded app is visible for
     * a moment before the overlay arrives, and an ongoing notification has to
     * stay posted. In exchange it needs no accessibility permission at all —
     * which is what makes the app survive that declaration being denied or
     * revoked.
     */
    POLLING;

    companion object {
        val DEFAULT = ACCESSIBILITY

        /** The stored mode, falling back to [DEFAULT] for absent or junk values. */
        fun of(prefs: SharedPreferences): EnforcementMode {
            val stored = prefs.getString(Constants.PrefsKeys.ENFORCEMENT_MODE, null) ?: return DEFAULT
            return entries.firstOrNull { it.name == stored } ?: DEFAULT
        }

        fun store(prefs: SharedPreferences, mode: EnforcementMode) {
            prefs.edit { putString(Constants.PrefsKeys.ENFORCEMENT_MODE, mode.name) }
        }
    }
}
