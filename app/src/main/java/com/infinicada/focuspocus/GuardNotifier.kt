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
import android.util.Log

/**
 * Guard-channel notifications — today just the opt-in "seal lifted" note.
 * Mirrors ProgressionNotifier: framework Notification.Builder, permission
 * guarded, and never allowed to throw on the service's minute tick.
 */
object GuardNotifier {
    private const val TAG = "GuardNotifier"

    /** Creates the guards channel. Called from Application.onCreate. */
    fun createChannel(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                Constants.GUARDS_CHANNEL_ID,
                context.getString(R.string.guards_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.guards_channel_desc)
            }
            nm.createNotificationChannel(channel)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create guards channel", e)
        }
    }

    /** Posts one "seal lifted" notification for [appNames], if the user opted in. */
    fun postSealsLifted(context: Context, prefs: SharedPreferences, appNames: List<String>) {
        if (appNames.isEmpty()) return
        if (!prefs.getBoolean(Constants.PrefsKeys.SEAL_LIFTED_ALERTS_ENABLED, false)) return
        val text = if (appNames.size == 1) {
            context.getString(R.string.seal_lifted_notification_text_one, appNames.first())
        } else {
            context.getString(
                R.string.seal_lifted_notification_text_many, appNames.size, appNames.joinToString(", ")
            )
        }
        try {
            if (!canPost(context)) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val intent = Intent().setClassName(context, "com.infinicada.focuspocus.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(context, Constants.GUARDS_CHANNEL_ID)
                .setSmallIcon(R.mipmap.fplogo_round)
                .setContentTitle(context.getString(R.string.seal_lifted_notification_title))
                .setContentText(text)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            nm.notify(Constants.SEAL_LIFTED_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post seal-lifted notification", e)
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
