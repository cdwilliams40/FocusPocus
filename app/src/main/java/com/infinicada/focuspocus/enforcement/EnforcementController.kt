package com.infinicada.focuspocus.enforcement

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.ProtectionHealth
import com.infinicada.focuspocus.UsageStatsHelper

/**
 * Keeps exactly one detector running, and knows what the fallback still needs
 * before it can run at all.
 *
 * Call [reconcile] from anywhere the answer could have changed — process start,
 * boot, the accessibility service connecting or dying, the user switching modes
 * or granting a permission. It is idempotent, and costs three binder calls, so
 * it belongs on lifecycle events rather than on a hot path.
 */
object EnforcementController {

    private const val TAG = "EnforcementController"

    /**
     * The fallback needs two grants the accessibility path doesn't:
     *
     * - **Usage access**, to see which app is in the foreground at all.
     * - **Display over other apps**, because closing a blocked app and showing
     *   the overlay are activity starts from a background service, which Android
     *   10+ forbids unless the app holds `SYSTEM_ALERT_WINDOW`. The accessibility
     *   service is exempt from that rule; a plain foreground service is not, so
     *   without this grant the fallback would detect a block and then be unable
     *   to act on it.
     */
    fun isFallbackAvailable(context: Context): Boolean =
        UsageStatsHelper.hasUsageStatsPermission(context) && canDrawOverlays(context)

    fun canDrawOverlays(context: Context): Boolean = try {
        Settings.canDrawOverlays(context)
    } catch (e: Exception) {
        Log.e(TAG, "Overlay permission check failed", e)
        false
    }

    fun currentMode(context: Context): EnforcementMode =
        EnforcementMode.read(prefs(context))

    fun setMode(context: Context, mode: EnforcementMode) {
        prefs(context).edit()
            .putString(Constants.PrefsKeys.ENFORCEMENT_MODE, mode.name)
            .apply()
        reconcile(context)
    }

    /**
     * Which detector should be running, given the mode and what's been granted.
     *
     * [accessibilityEnabled] is overridable because the probe is a proxy, and two
     * callers know better than it does: the accessibility service itself knows it
     * is connected regardless of what the setting string says, and its
     * `onDestroy` knows it is going away while the setting still lists it.
     */
    fun activeEnforcer(
        context: Context,
        accessibilityEnabled: Boolean = isAccessibilityAlive(context)
    ): ActiveEnforcer = resolveActiveEnforcer(
        mode = currentMode(context),
        accessibilityEnabled = accessibilityEnabled,
        fallbackAvailable = isFallbackAvailable(context)
    )

    /**
     * Whether the accessibility service is enabled *and* has proved it recently.
     *
     * An OEM optimizer that kills the service leaves it listed in
     * `ENABLED_ACCESSIBILITY_SERVICES` forever, which is precisely the failure the
     * fallback exists to cover — so trusting the setting alone would leave the
     * user unprotected in the case they're most likely to hit. A stamp that has
     * gone quiet for [HEARTBEAT_STALE_MS] while the user is actively using the
     * device means the service is not delivering events.
     *
     * Two situations say nothing about whether the service works, and both are
     * given the same grace as a live-but-quiet one: a stamp written before this
     * boot (the service reconnects seconds into a boot, and doubting it would
     * flash the fallback's notification on every restart), and no stamp at all on
     * a fresh install. Counting the grace from boot rather than trusting them
     * outright still catches a service that never connects.
     */
    fun isAccessibilityAlive(context: Context): Boolean {
        if (!ProtectionHealth.isAccessibilityServiceEnabled(context)) return false
        val lastSeen = prefs(context).getLong(Constants.PrefsKeys.ACCESSIBILITY_HEARTBEAT_MILLIS, 0L)
        val bootMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val since = maxOf(lastSeen, bootMillis)
        // A clock moved backwards reads as a negative age, which is < the
        // threshold — alive, rather than declaring a working service dead.
        return System.currentTimeMillis() - since < HEARTBEAT_STALE_MS
    }

    /**
     * How quiet the heartbeat has to go before the service counts as dead.
     *
     * The stamp is refreshed on every app switch and every minute tick, so five
     * minutes of silence cannot happen on a device in use. It can happen across a
     * long screen-off (the minute tick doesn't fire in doze) — hence the
     * accessibility service re-stamping and re-reconciling as soon as it sees an
     * event again, which stands the fallback back down. Double duty: it is also
     * the grace period a freshly booted or freshly installed service gets before
     * silence counts against it.
     */
    const val HEARTBEAT_STALE_MS = 5 * 60 * 1000L

    /**
     * Starts or stops [FallbackEnforcementService] to match [activeEnforcer].
     *
     * Only the fallback is actuated here — the accessibility service's lifetime
     * belongs to the OS, so it gates itself on [activeEnforcer] instead.
     */
    fun reconcile(
        context: Context,
        accessibilityEnabled: Boolean = isAccessibilityAlive(context)
    ) {
        val shouldRun = activeEnforcer(context, accessibilityEnabled) == ActiveEnforcer.FALLBACK
        if (shouldRun) {
            FallbackEnforcementService.start(context)
        } else {
            FallbackEnforcementService.stop(context)
        }
    }

    /**
     * Sends the user to the "Display over other apps" screen for FocusPocus,
     * falling back to the system-wide list if the per-app deep link is missing.
     */
    fun openOverlaySettings(context: Context) {
        val perApp = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(perApp)
        } catch (e: Exception) {
            Log.e(TAG, "Per-app overlay screen unavailable, falling back to the list", e)
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Overlay settings unavailable", e2)
            }
        }
    }

    /**
     * True when the OS is actively refusing to let this app hold an accessibility
     * service, rather than the user simply not having turned it on.
     *
     * Android 17's Advanced Protection Mode revokes and re-refuses accessibility
     * for every app that hasn't declared `isAccessibilityTool` — which a blocker
     * cannot honestly declare. There is no API to read that state directly, so
     * this is a heuristic on the one observable consequence: the mode is on, and
     * our service is not enabled. Used only to word the UI ("your device is
     * blocking this") rather than to gate behaviour, so a false positive costs a
     * misleading sentence, not broken enforcement.
     */
    fun isAccessibilityBlockedByDevice(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < ADVANCED_PROTECTION_SDK) return false
        if (ProtectionHealth.isAccessibilityServiceEnabled(context)) return false
        return isAdvancedProtectionEnabled(context)
    }

    /**
     * Reads Advanced Protection's state reflectively rather than through
     * `AdvancedProtectionManager`, so the app carries no compile-time dependency on
     * an API that only exists on the newest platforms and can keep building as
     * compileSdk moves. A missing service, class or method just means "not on",
     * which is also the right answer when `QUERY_ADVANCED_PROTECTION_MODE` isn't
     * held.
     */
    private fun isAdvancedProtectionEnabled(context: Context): Boolean = try {
        val manager = context.getSystemService("advanced_protection") ?: return false
        val method = manager.javaClass.getMethod("isAdvancedProtectionEnabled")
        method.invoke(manager) as? Boolean ?: false
    } catch (e: Exception) {
        false
    }

    /**
     * API 37 (Android 17) — the release that started revoking accessibility from
     * non-tool apps under Advanced Protection. Earlier releases expose the mode but
     * don't act on it this way, so claiming the device is blocking us there would
     * be wrong. A literal because [Build.VERSION_CODES] has no stable name for it
     * on every compileSdk this builds against.
     */
    private const val ADVANCED_PROTECTION_SDK = 37

    private fun prefs(context: Context) =
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
}
