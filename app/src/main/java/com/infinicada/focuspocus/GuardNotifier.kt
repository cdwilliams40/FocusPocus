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
 * Guards-channel notifications (seal lifted). Mirrors ProgressionNotifier:
 * framework Notification.Builder, permission-guarded, never throws.
 */
object GuardNotifier {
    private const val TAG = "GuardNotifier"

    /**
     * Creates the guards channel. Called from Application.onCreate, like the
     * progression channel, so it exists before anything posts to it.
     */
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

    /**
     * Posts one "seal lifted" note for [packageName] if the opt-in toggle is
     * on. Tag-keyed by package so simultaneous lifts don't overwrite each
     * other.
     */
    fun postSealLifted(context: Context, prefs: SharedPreferences, packageName: String) {
        if (!prefs.getBoolean(Constants.PrefsKeys.SEAL_LIFTED_ALERTS_ENABLED, false)) return
        try {
            if (!canPost(context)) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val appName = AppUtils.getAppName(context, packageName)
            val intent = Intent().setClassName(context, "com.infinicada.focuspocus.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(context, Constants.GUARDS_CHANNEL_ID)
                .setSmallIcon(R.mipmap.fplogo_round)
                .setContentTitle(context.getString(R.string.seal_lifted_title))
                .setContentText(context.getString(R.string.seal_lifted_message, appName))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            nm.notify(packageName, Constants.SEAL_LIFTED_NOTIFICATION_ID, notification)
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
