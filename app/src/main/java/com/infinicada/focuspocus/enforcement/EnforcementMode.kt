package com.infinicada.focuspocus.enforcement

import android.content.SharedPreferences
import com.infinicada.focuspocus.Constants

/**
 * How FocusPocus finds out which app just opened. Blocking itself is identical
 * across modes — [BlockingEngine] makes every decision — so this only picks the
 * detector.
 */
enum class EnforcementMode {
    /**
     * Accessibility when it's alive, polling when it isn't. The default, because
     * accessibility is both faster and cheaper, but is increasingly something the
     * OS or Play can take away: Android 17's Advanced Protection Mode revokes it
     * from any app not declared an accessibility tool, Android 13+ restricted
     * settings block sideloaded builds from being granted it at all, and OEM
     * battery optimizers kill the service outright. Falling back beats going dark.
     */
    AUTO,

    /** Accessibility only. Blocking simply stops if the service is revoked. */
    ACCESSIBILITY,

    /**
     * Polling only, even where accessibility is available — for users who would
     * rather not grant an accessibility service at all, and who accept a second
     * of detection lag plus an ongoing notification in exchange.
     */
    FALLBACK;

    companion object {
        val DEFAULT = AUTO

        fun from(stored: String?): EnforcementMode =
            entries.firstOrNull { it.name == stored } ?: DEFAULT

        fun read(prefs: SharedPreferences): EnforcementMode =
            from(prefs.getString(Constants.PrefsKeys.ENFORCEMENT_MODE, null))
    }
}

/** Which detector should actually be running right now. */
enum class ActiveEnforcer {
    ACCESSIBILITY,
    FALLBACK,

    /**
     * Nothing is enforcing: the chosen mode's detector is unavailable. Warden
     * (device-owner suspension) may still be greying blocked apps out, but no
     * overlay will appear.
     */
    NONE
}

/**
 * The mode-to-detector decision, with no Android dependencies so every branch is
 * unit-testable.
 *
 * Exactly one detector is ever chosen. Running both would double-count pact open
 * attempts and cooldown friction escalations, since each detector would report
 * the same app open to its own engine.
 */
internal fun resolveActiveEnforcer(
    mode: EnforcementMode,
    accessibilityEnabled: Boolean,
    fallbackAvailable: Boolean
): ActiveEnforcer = when (mode) {
    EnforcementMode.ACCESSIBILITY ->
        if (accessibilityEnabled) ActiveEnforcer.ACCESSIBILITY else ActiveEnforcer.NONE

    EnforcementMode.FALLBACK ->
        if (fallbackAvailable) ActiveEnforcer.FALLBACK else ActiveEnforcer.NONE

    // Accessibility first: it is push-based, so it blocks an app before the user
    // sees anything useful in it, and it costs no polling and no permanent
    // notification.
    EnforcementMode.AUTO -> when {
        accessibilityEnabled -> ActiveEnforcer.ACCESSIBILITY
        fallbackAvailable -> ActiveEnforcer.FALLBACK
        else -> ActiveEnforcer.NONE
    }
}
