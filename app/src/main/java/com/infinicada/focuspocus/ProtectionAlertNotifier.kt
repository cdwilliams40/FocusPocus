package com.infinicada.focuspocus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.infinicada.focuspocus.limit.WellApps

/**
 * Alerts the user if Android disables or kills Focus Pocus's accessibility service
 * while protection is actually needed (Well apps configured or a session active).
 * Tapping the notification opens Accessibility Settings directly; reconnecting the
 * service cancels the alert automatically.
 */
object ProtectionAlertNotifier {
    private const val TAG = "ProtectionAlertNotifier"

    /** Pure predicate for unit testing. */
    fun shouldAlertProtectionOff(
        accessibilityEnabled: Boolean,
        sessionActive: Boolean,
        wellAppsCount: Int
    ): Boolean = !accessibilityEnabled && (sessionActive || wellAppsCount > 0)

    fun createChannel(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val channel = NotificationChannel(
                Constants.PROTECTION_ALERT_CHANNEL_ID,
                context.getString(R.string.protection_alert_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.protection_alert_channel_description)
            }
            nm.createNotificationChannel(channel)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create protection alert channel", e)
        }
    }

    fun checkAndNotify(context: Context, prefs: SharedPreferences) {
        try {
            val accessibilityEnabled = ProtectionHealth.isAccessibilityServiceEnabled(context)
            val sessionActive = SessionManager.isSessionActive(prefs)
            val wellAppsCount = WellApps.load(prefs).size

            if (!shouldAlertProtectionOff(accessibilityEnabled, sessionActive, wellAppsCount)) {
                cancel(context)
                return
            }
            if (!canPost(context)) return

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pending = PendingIntent.getActivity(
                context,
                Constants.PROTECTION_ALERT_NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val body = if (sessionActive) {
                context.getString(R.string.protection_alert_body_session)
            } else {
                context.getString(R.string.protection_alert_body_well)
            }
            val notification = Notification.Builder(context, Constants.PROTECTION_ALERT_CHANNEL_ID)
                .setSmallIcon(R.mipmap.fplogo_round)
                .setContentTitle(context.getString(R.string.protection_alert_title))
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            nm.notify(Constants.PROTECTION_ALERT_NOTIFICATION_ID, notification)
            EnforcementLog.record(prefs, EnforcementLog.CAT_PROTECTION, "Accessibility service is off — alert posted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to evaluate/post protection-off alert", e)
        }
    }

    fun cancel(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            nm.cancel(Constants.PROTECTION_ALERT_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel protection alert", e)
        }
    }

    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return true
    }
}
