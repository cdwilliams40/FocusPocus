package com.infinicada.focuspocus

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import com.infinicada.focuspocus.enforcement.ActiveEnforcer
import com.infinicada.focuspocus.enforcement.EnforcementController
import com.infinicada.focuspocus.enforcement.EnforcementMode

/**
 * Enforcement health: one place that answers "am I actually protected right
 * now?". Blocking silently degrades when no detector is running, usage access is
 * revoked, notifications are blocked, or an OEM battery optimizer kills the
 * service — this surfaces each so the user learns before they need it, not after.
 *
 * "Am I protected" is not the same question as "is accessibility on" any more.
 * Since the polling fallback exists, the honest answer is whichever detector
 * [EnforcementController] resolved, which is why [Status.enforcing] rather than
 * [Status.accessibilityEnabled] is what the card should key off.
 *
 * Every probe fails healthy on error: a broken system query must not paint
 * false alarms over a working setup.
 */
object ProtectionHealth {
    private const val TAG = "ProtectionHealth"

    data class Status(
        /** The accessibility service — the fastest detector, and the revocable one. */
        val accessibilityEnabled: Boolean,
        /** Usage access — daily limits, conditional unlocks, and fallback detection. */
        val usageAccessGranted: Boolean,
        /** Notifications — ritual alerts, countdowns, seal-lifted notes. */
        val notificationsEnabled: Boolean,
        /** Battery optimization exemption — OEM optimizers kill the service. */
        val batteryUnrestricted: Boolean,
        /** "Display over other apps" — the fallback cannot act on a block without it. */
        val overlayGranted: Boolean,
        /** The detector the user asked for. */
        val mode: EnforcementMode,
        /** The detector actually running, which is what enforcement really depends on. */
        val activeEnforcer: ActiveEnforcer,
        /**
         * Advanced Protection (or an equivalent device policy) is refusing this
         * app an accessibility service, rather than the user having left it off.
         * Wording only — see [EnforcementController.isAccessibilityBlockedByDevice].
         */
        val accessibilityBlockedByDevice: Boolean
    ) {
        /**
         * *Something* is detecting app opens. Deliberately not "accessibility is
         * on": a user running the polling fallback is protected, and a permanently
         * red row would teach them to ignore this card.
         */
        val enforcing: Boolean
            get() = activeEnforcer != ActiveEnforcer.NONE

        /**
         * Whether the overlay grant is worth asking about. In accessibility mode it
         * is genuinely unnecessary, so showing it as missing would be noise.
         */
        val overlayRelevant: Boolean
            get() = mode == EnforcementMode.FALLBACK ||
                (mode == EnforcementMode.AUTO && !accessibilityEnabled)

        val allHealthy: Boolean
            get() = enforcing && usageAccessGranted && notificationsEnabled &&
                batteryUnrestricted && (!overlayRelevant || overlayGranted)
    }

    fun check(context: Context): Status = Status(
        accessibilityEnabled = isAccessibilityServiceEnabled(context),
        usageAccessGranted = UsageStatsHelper.hasUsageStatsPermission(context),
        notificationsEnabled = areNotificationsEnabled(context),
        batteryUnrestricted = isIgnoringBatteryOptimizations(context),
        overlayGranted = EnforcementController.canDrawOverlays(context),
        mode = EnforcementController.currentMode(context),
        activeEnforcer = EnforcementController.activeEnforcer(context),
        accessibilityBlockedByDevice = EnforcementController.isAccessibilityBlockedByDevice(context)
    )

    /** Same settings-string walk MainActivity uses for its service gate. */
    fun isAccessibilityServiceEnabled(context: Context): Boolean = try {
        val expected = ComponentName(context, MyAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (enabled == null) {
            false
        } else {
            // SimpleStringSplitter is both Iterator and Iterable, which makes
            // the sequence extensions ambiguous — walk it explicitly.
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            var found = false
            while (splitter.hasNext()) {
                if (ComponentName.unflattenFromString(splitter.next()) == expected) {
                    found = true
                    break
                }
            }
            found
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error checking accessibility state", e)
        true
    }

    private fun areNotificationsEnabled(context: Context): Boolean = try {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.areNotificationsEnabled() ?: true
    } catch (e: Exception) {
        Log.e(TAG, "Error checking notification state", e)
        true
    }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean = try {
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    } catch (e: Exception) {
        Log.e(TAG, "Error checking battery optimization state", e)
        true
    }

    // ── Fix-it intents (each falls back to the app-details screen) ──

    fun openAccessibilitySettings(context: Context) =
        startSafely(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openNotificationSettings(context: Context) = startSafely(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    )

    /**
     * The optimization *list* screen, not the direct exemption request — the
     * request path needs the REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission
     * Play scrutinizes, and the list keeps the choice in the user's hands.
     *
     * Warden mode is the exception: Settings disables the battery controls of
     * an active device admin (the same OS protection that removes its
     * force-stop button), so the list screen is a dead end there — the user
     * lands on a toggle they cannot flip. The direct consent dialog still
     * works because it rides the device-idle allowlist, which the admin
     * greying doesn't touch, and it still leaves the decision to the user.
     */
    @SuppressLint("BatteryLife")
    fun openBatteryOptimizationSettings(context: Context) {
        if (DeviceOwnerManager.isDeviceOwner(context)) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.fromParts("package", context.packageName, null))
                )
                return
            } catch (e: Exception) {
                Log.e(TAG, "Exemption dialog unavailable, falling back to the list screen", e)
            }
        }
        startSafely(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun startSafely(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Settings screen unavailable, falling back to app details", e)
            try {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                )
            } catch (e2: Exception) {
                Log.e(TAG, "App details screen unavailable too", e2)
            }
        }
    }
}
