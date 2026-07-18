package com.infinicada.focuspocus

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log

/**
 * Enforcement health: one place that answers "am I actually protected right
 * now?". Blocking silently degrades when the accessibility service is off,
 * usage access is revoked, notifications are blocked, or an OEM battery
 * optimizer kills the service — this surfaces each so the user learns before
 * they need it, not after.
 *
 * Every probe fails healthy on error: a broken system query must not paint
 * false alarms over a working setup.
 */
object ProtectionHealth {
    private const val TAG = "ProtectionHealth"

    data class Status(
        /** The accessibility service — the enforcement engine itself. */
        val accessibilityEnabled: Boolean,
        /** Usage access — daily limits and conditional unlocks depend on it. */
        val usageAccessGranted: Boolean,
        /** Notifications — ritual alerts, countdowns, seal-lifted notes. */
        val notificationsEnabled: Boolean,
        /** Battery optimization exemption — OEM optimizers kill the service. */
        val batteryUnrestricted: Boolean
    ) {
        val allHealthy: Boolean
            get() = accessibilityEnabled && usageAccessGranted &&
                notificationsEnabled && batteryUnrestricted
    }

    fun check(context: Context): Status = Status(
        accessibilityEnabled = isAccessibilityServiceEnabled(context),
        usageAccessGranted = UsageStatsHelper.hasUsageStatsPermission(context),
        notificationsEnabled = areNotificationsEnabled(context),
        batteryUnrestricted = isIgnoringBatteryOptimizations(context)
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
     */
    fun openBatteryOptimizationSettings(context: Context) =
        startSafely(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

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
