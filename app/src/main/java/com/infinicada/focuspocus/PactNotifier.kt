package com.infinicada.focuspocus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.infinicada.focuspocus.limit.PactManager

/**
 * Quiet ongoing notification shown while one or more Mana Well pacts are active.
 * Displays a live chronometer countdown to the earliest pact expiry and a one-tap
 * "End pact & refund" action button so returning unused minutes takes a single tap
 * from the notification shade.
 */
object PactNotifier {
    private const val TAG = "PactNotifier"
    private const val ACTION_REQUEST_CODE = 9105

    fun createChannel(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val channel = NotificationChannel(
                Constants.PACT_CHANNEL_ID,
                context.getString(R.string.pact_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.pact_channel_description)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create pact notification channel", e)
        }
    }

    fun update(context: Context) {
        try {
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val now = System.currentTimeMillis()
            val active = PactManager(prefs).getActiveAllowances(now)
            if (active.isEmpty()) {
                nm.cancel(Constants.PACT_NOTIFICATION_ID)
                return
            }
            if (!canPost(context)) return

            val earliestEntry = active.minByOrNull { it.value } ?: return
            val appNames = active.keys.map { AppUtils.getAppName(context, it) }
            val title = if (appNames.size == 1) {
                context.getString(R.string.pact_notification_title_single, appNames.first())
            } else {
                context.getString(R.string.pact_notification_title_multi, appNames.size)
            }
            val text = if (appNames.size == 1) {
                context.getString(R.string.pact_notification_text_single)
            } else {
                appNames.joinToString(", ")
            }

            val openIntent = Intent().setClassName(context, "com.infinicada.focuspocus.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val contentPending = PendingIntent.getActivity(
                context,
                Constants.PACT_NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val endIntent = Intent(context, PactActionReceiver::class.java).apply {
                action = PactActionReceiver.ACTION_END_ACTIVE_PACTS
            }
            val endPending = PendingIntent.getBroadcast(
                context,
                ACTION_REQUEST_CODE,
                endIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val endAction = Notification.Action.Builder(
                null,
                context.getString(R.string.pact_notification_action_end),
                endPending
            ).build()

            val builder = Notification.Builder(context, Constants.PACT_CHANNEL_ID)
                .setSmallIcon(R.mipmap.fplogo_round)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setWhen(earliestEntry.value)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(endAction)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setCategory(Notification.CATEGORY_STOPWATCH)
            }

            nm.notify(Constants.PACT_NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update pact notification", e)
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
