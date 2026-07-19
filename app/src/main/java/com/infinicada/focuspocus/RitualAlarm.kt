package com.infinicada.focuspocus

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.model.Schedule

/**
 * AlarmManager backstop for rituals. The accessibility service's minute tick
 * remains the primary driver, but if the service is dead (battery-killed,
 * never re-enabled after an update) rituals used to silently not fire. One
 * exact alarm is kept armed for the next ritual transition (start or end);
 * firing reconciles session state through the same SessionManager paths and
 * re-arms. Both drivers are idempotent — whoever runs first wins, the other
 * no-ops on the ACTIVE_SCHEDULE_ID guard.
 */
object RitualAlarmScheduler {
    private const val TAG = "RitualAlarmScheduler"
    private const val REQUEST_CODE = 7001
    const val ACTION_RITUAL_ALARM = "com.infinicada.focuspocus.RITUAL_ALARM"

    /** (Re)arms the single alarm for the next transition; cancels when none. */
    fun scheduleNext(context: Context) {
        try {
            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pending = pendingIntent(context)
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val next = nextRitualTransitionMillis(loadSchedules(prefs), System.currentTimeMillis())
            if (next == null) {
                alarmManager.cancel(pending)
                return
            }
            // Exact when allowed; the inexact fallback still lands within
            // minutes, which beats never (this is a backstop, the service's
            // minute tick handles the punctual case).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
            } else {
                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
                } catch (e: SecurityException) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
                }
            }
            Log.d(TAG, "Ritual alarm armed for $next")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to arm ritual alarm", e)
        }
    }

    fun onAlarmFired(context: Context) {
        try {
            reconcile(context)
        } catch (e: Exception) {
            Log.e(TAG, "Ritual reconcile failed", e)
        }
        scheduleNext(context)
    }

    /**
     * Brings session state in line with the schedule table right now: ends an
     * active ritual whose window has closed, then activates any ritual whose
     * window contains this moment — the same rules the service applies on its
     * own restart (checkMissedScheduleActivation).
     */
    private fun reconcile(context: Context) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val schedules = loadSchedules(prefs)
        if (schedules.isEmpty()) return
        val now = System.currentTimeMillis()

        val activeId = prefs.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)
        if (activeId != null) {
            val active = schedules.find { it.id == activeId }
            if (active != null && !isScheduleActiveAt(active, now)) {
                Log.d(TAG, "Alarm ending ritual ${active.name}")
                SessionManager.stopSession(context, prefs, gson)
                RitualNotifier.postEnded(context, active)
            }
            // Still running (window open) or dangling id — either way the
            // service's tick owns the messier cases; don't stack a new start.
            if (prefs.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null) != null) return
        }
        if (prefs.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)) return

        val blockers = BlockerRepository.getBlockers(prefs)
        for (schedule in schedules) {
            if (!isScheduleActiveAt(schedule, now)) continue
            val validNames = schedule.effectiveBlockerNames
                .filter { name -> blockers.any { it.name == name } }
            if (validNames.isEmpty()) continue
            Log.d(TAG, "Alarm starting ritual ${schedule.name}")
            SessionManager.startSession(
                sharedPreferences = prefs,
                blockerNames = validNames,
                scheduleId = schedule.id,
                breaksEnabled = schedule.breaksEnabled,
                scheduleEndTimeMillis = computeScheduleEndMillis(schedule, now)
            )
            DndController.updateDndState(context)
            DeviceOwnerManager.syncSuspensions(context)
            RitualNotifier.postStarted(context, schedule)
            return
        }
    }

    private fun loadSchedules(prefs: android.content.SharedPreferences): List<Schedule> {
        val type = object : TypeToken<List<Schedule>>() {}.type
        return PrefsHelper.load<List<Schedule>>(prefs, Gson(), Constants.PrefsKeys.SCHEDULES, type)
            ?.filterNotNull() ?: emptyList()
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, RitualAlarmReceiver::class.java).setAction(ACTION_RITUAL_ALARM),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

class RitualAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RitualAlarmScheduler.ACTION_RITUAL_ALARM) return
        RitualAlarmScheduler.onAlarmFired(context)
    }
}

/**
 * Ritual start/end notifications, shared by the accessibility service and the
 * alarm backstop. Notification ids reuse the service's historical formula so
 * SpellbookViewModel.deleteSchedule keeps cancelling the right ones.
 */
object RitualNotifier {
    private const val TAG = "RitualNotifier"

    /** Even ids for starts, odd for ends — the service's original scheme. */
    fun notificationId(scheduleId: String, isEndNotification: Boolean): Int {
        val hash = scheduleId.fold(0) { acc, c -> acc * 31 + c.code }
        val baseId = (hash and 0x7FFFFFFE)
        return if (isEndNotification) baseId + 1 else baseId
    }

    /** Idempotent; called from Application.onCreate and the service. */
    fun createChannel(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val channel = NotificationChannel(
                Constants.RITUALS_CHANNEL_ID,
                context.getString(R.string.rituals_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.rituals_channel_description)
            }
            nm.createNotificationChannel(channel)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create rituals channel", e)
        }
    }

    fun postStarted(context: Context, schedule: Schedule) = post(
        context,
        notificationId(schedule.id, isEndNotification = false),
        context.getString(R.string.ritual_started_title),
        context.getString(R.string.ritual_started_message, schedule.name)
    )

    fun postEnded(context: Context, schedule: Schedule) = post(
        context,
        notificationId(schedule.id, isEndNotification = true),
        context.getString(R.string.ritual_ended_title),
        context.getString(R.string.ritual_ended_message, schedule.name)
    )

    private fun post(context: Context, id: Int, title: String, text: String) {
        try {
            if (!canPost(context)) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val intent = Intent().setClassName(context, "com.infinicada.focuspocus.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(context, Constants.RITUALS_CHANNEL_ID)
                .setSmallIcon(R.mipmap.fplogo_round)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            nm.notify(id, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post ritual notification", e)
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
