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
import com.infinicada.focuspocus.model.Trial

/**
 * Progression-channel notifications (trial completions, daily wrap-up).
 *
 * Uses the framework Notification.Builder rather than NotificationCompat so it
 * stays androidx-free like the rest of the session plumbing (minSdk 29 makes
 * the compat layer unnecessary here). Every post is permission-guarded —
 * POST_NOTIFICATIONS may be denied and nothing here is allowed to throw on the
 * session-stop path.
 */
object ProgressionNotifier {
    private const val TAG = "ProgressionNotifier"

    /**
     * Creates the progression channel. Called from Application.onCreate so the
     * channel exists before any ViewModel/manager posts to it (the rituals
     * channel is created by the accessibility service, which is too late for
     * notifications posted outside the service).
     */
    fun createChannel(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                Constants.PROGRESSION_CHANNEL_ID,
                context.getString(R.string.progression_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.progression_channel_desc)
            }
            nm.createNotificationChannel(channel)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create progression channel", e)
        }
    }

    /** Posts one "trial complete — claim your mana" notification if enabled. */
    fun postTrialCompletions(context: Context, prefs: SharedPreferences, completed: List<Trial>) {
        if (completed.isEmpty()) return
        if (!prefs.getBoolean(Constants.PrefsKeys.PROGRESSION_ENABLED, true)) return
        if (!prefs.getBoolean(Constants.PrefsKeys.TRIAL_ALERTS_ENABLED, true)) return
        val totalMana = completed.sumOf { it.rewardMana }
        val text = context.resources.getQuantityString(
            R.plurals.trial_complete_notification_text, completed.size, completed.size, totalMana
        )
        post(
            context,
            Constants.TRIAL_COMPLETION_NOTIFICATION_ID,
            context.getString(R.string.trial_complete_notification_title),
            text
        )
    }

    /** Posts the evening wrap-up. Gating happens in the caller via ProgressionMath.shouldSendWrapup. */
    fun postDailyWrapup(context: Context, reclaimedMinutes: Int, manaToday: Long, streak: Int) {
        val hours = reclaimedMinutes / 60
        val mins = reclaimedMinutes % 60
        val time = if (hours > 0) {
            context.getString(R.string.wrapup_time_hours_minutes, hours, mins)
        } else {
            context.getString(R.string.wrapup_time_minutes, mins)
        }
        post(
            context,
            Constants.DAILY_WRAPUP_NOTIFICATION_ID,
            context.getString(R.string.wrapup_notification_title),
            context.getString(R.string.wrapup_notification_text, time, manaToday, streak)
        )
    }

    private fun post(context: Context, id: Int, title: String, text: String) {
        try {
            if (!canPost(context)) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            // Class name string instead of MainActivity::class keeps this file
            // free of UI-layer imports.
            val intent = Intent().setClassName(context, "com.infinicada.focuspocus.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(context, Constants.PROGRESSION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.fplogo_round)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            nm.notify(id, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post progression notification", e)
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
