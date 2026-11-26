package com.infinicada.focuspocus

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.infinicada.focuspocus.limit.ManaWell
import com.infinicada.focuspocus.limit.PactManager
import com.infinicada.focuspocus.limit.WellApps
import com.infinicada.focuspocus.model.Schedule

/**
 * Unified exact-alarm scheduler covering every time-driven enforcement transition:
 * - next ritual start/end,
 * - active timed session end, break end, and Pomodoro auto-break,
 * - earliest running Well pact expiry,
 * - earliest pending 24h Well app removal,
 * - pending 24h Well terms revision,
 * - next scheduled auto-backup (when configured).
 *
 * One exact alarm is kept armed for the earliest upcoming instant across all of
 * these so enforcement stays punctual even in Doze or if Android kills the
 * accessibility service. Firing runs idempotent reconciliation and re-arms.
 *
 * Keeps the [RitualAlarmScheduler] / [RitualAlarmReceiver] names for manifest
 * and upgrade compatibility.
 */
object RitualAlarmScheduler {
    private const val TAG = "RitualAlarmScheduler"
    private const val REQUEST_CODE = 7001
    const val ACTION_RITUAL_ALARM = "com.infinicada.focuspocus.RITUAL_ALARM"

    /**
     * Pure calculation of the earliest upcoming alarm instant strictly after [now],
     * or null when nothing is pending.
     */
    fun computeNextAlarmMillis(
        prefs: SharedPreferences,
        now: Long,
        schedules: List<Schedule>,
        activePactExpiries: Collection<Long>,
        pendingRemovalExpiries: Collection<Long>,
        pendingWellRevisionMillis: Long?,
        nextAutoBackupMillis: Long? = null
    ): Long? {
        val activeScheduleId = prefs.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)
        val activeSchedule = activeScheduleId?.let { id -> schedules.find { it.id == id } }
        val exceptions = RitualExceptions.loadAll(prefs)

        val candidates = listOfNotNull(
            nextRitualTransitionMillis(schedules, now) { exceptions[it].orEmpty() },
            SessionTransitions.nextDueMillis(prefs, now, activeSchedule),
            activePactExpiries.filter { it > now }.minOrNull(),
            pendingRemovalExpiries.filter { it > now }.minOrNull(),
            pendingWellRevisionMillis?.takeIf { it > now },
            nextAutoBackupMillis?.takeIf { it > now }
        )
        return candidates.filter { it > now }.minOrNull()
    }

    /** (Re)arms the single alarm for the earliest upcoming transition; cancels when none. */
    fun scheduleNext(context: Context) {
        try {
            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pending = pendingIntent(context)
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()

            val schedules = loadSchedules(prefs)
            val activePactExpiries = PactManager(prefs).getActiveAllowances(now).values
            val pendingRemovals = WellApps.pendingRemovals(prefs).values
            val pendingWellRevision = listOf(
                prefs.getLong(Constants.PrefsKeys.WELL_PENDING_APPLIES_AT_MILLIS, 0L),
                prefs.getLong(Constants.PrefsKeys.WELL_GATE_PENDING_APPLIES_AT_MILLIS, 0L)
            ).filter { it > now }.minOrNull()

            val next = computeNextAlarmMillis(
                prefs = prefs,
                now = now,
                schedules = schedules,
                activePactExpiries = activePactExpiries,
                pendingRemovalExpiries = pendingRemovals,
                pendingWellRevisionMillis = pendingWellRevision,
                nextAutoBackupMillis = AutoBackupManager.nextDueMillis(prefs, now)
            )
            if (next == null) {
                alarmManager.cancel(pending)
                return
            }
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
            Log.d(TAG, "Unified enforcement alarm armed for $next")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to arm enforcement alarm", e)
        }
    }

    fun onAlarmFired(context: Context) {
        val prefs = try {
            context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }
        try {
            if (prefs != null) {
                reconcileAll(context, prefs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Enforcement alarm reconcile failed", e)
        }
        scheduleNext(context)
    }

    private fun reconcileAll(context: Context, prefs: SharedPreferences) {
        val now = System.currentTimeMillis()
        val schedules = loadSchedules(prefs)

        // 1. Timed sessions, breaks, and Pomodoro auto-breaks
        val advanceOutcome = SessionManager.advance(context, prefs, schedules, now)
        if (advanceOutcome.changed) {
            EnforcementLog.record(
                prefs,
                EnforcementLog.CAT_ALARM,
                "Session transitions: ${advanceOutcome.events.joinToString()}"
            )
        }

        // 2. Scheduled rituals
        reconcileRituals(context, prefs, schedules, now)

        // 3. Pending 24h Well app removals & pending 24h Well terms revision
        if (WellApps.applyDueRemovals(prefs, now)) {
            EnforcementLog.record(prefs, EnforcementLog.CAT_PACT, "Applied due Well app removal(s)")
        }
        ManaWell.settings(prefs, now)

        // 4. Lapsed pacts
        val lapsed = PactManager(prefs).takeLapsedAllowances(now)
        if (lapsed.isNotEmpty()) {
            EnforcementLog.record(prefs, EnforcementLog.CAT_PACT, "Expired ${lapsed.size} pact(s)")
        }

        // 5. Reconcile Warden greying & notifications
        DeviceOwnerManager.syncSuspensions(context)
        SessionNotifier.update(context)
        PactNotifier.update(context)

        // 6. Check whether accessibility service was turned off while protection is needed
        ProtectionAlertNotifier.checkAndNotify(context, prefs)

        // 7. Run scheduled automatic backup if due
        AutoBackupManager.runIfDue(context, now)
    }

    private fun reconcileRituals(
        context: Context,
        prefs: SharedPreferences,
        schedules: List<Schedule>,
        now: Long
    ) {
        if (schedules.isEmpty()) return

        val exceptions = RitualExceptions.loadAll(prefs)
        val activeId = prefs.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)
        if (activeId != null) {
            val active = schedules.find { it.id == activeId }
            if (active != null && !isScheduleActiveAt(active, now, exceptions[active.id].orEmpty())) {
                Log.d(TAG, "Alarm ending ritual ${active.name}")
                SessionManager.stopSession(context, prefs)
                RitualNotifier.postEnded(context, active)
                EnforcementLog.record(prefs, EnforcementLog.CAT_RITUAL, "Ended ritual '${active.name}'")
            }
            if (prefs.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null) != null) return
        }

        val blockerNames = BlockerRepository.getBlockers(prefs).mapTo(mutableSetOf()) { it.name }
        val schedule = findRitualToResume(
            schedules = schedules,
            nowMillis = now,
            existingBlockerNames = blockerNames,
            skippedDatesFor = { exceptions[it].orEmpty() }
        ) {
            SessionManager.isRitualDismissed(prefs, it, now)
        } ?: return

        if (SessionManager.isSessionActive(prefs)) {
            // A ritual replaces a manual or talisman session only at its start,
            // as the service's minute tick does. Later in the window (a ritual
            // created or edited mid-window, an unrelated alarm) the running
            // session is left alone, like the service's reconnect catch-up.
            if (!isStartMinute(schedule, now)) return
            SessionManager.stopSession(context, prefs)
        }

        Log.d(TAG, "Alarm starting ritual ${schedule.name}")
        SessionManager.startSession(
            context = context,
            sharedPreferences = prefs,
            blockerNames = schedule.effectiveBlockerNames.filter { it in blockerNames },
            scheduleId = schedule.id,
            breaksEnabled = schedule.breaksEnabled,
            scheduleEndTimeMillis = computeScheduleEndMillis(schedule, now)
        )
        RitualNotifier.postStarted(context, schedule)
        EnforcementLog.record(prefs, EnforcementLog.CAT_RITUAL, "Started ritual '${schedule.name}'")
    }

    private fun isStartMinute(schedule: Schedule, now: Long): Boolean {
        val startMins = parseScheduleMinutes(schedule.effectiveStartTime) ?: return false
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE) == startMins
    }

    private fun loadSchedules(prefs: SharedPreferences): List<Schedule> =
        PrefsHelper.loadListLenient<Schedule>(prefs, Constants.PrefsKeys.SCHEDULES) ?: emptyList()

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
 * Ritual start/end & auto-break notifications, shared by the accessibility service,
 * SessionManager, and the unified alarm scheduler.
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

    fun postAutoBreakStarted(context: Context, focusedMinutes: Int, breakMinutes: Int) = post(
        context,
        Constants.FOCUS_SESSION_NOTIFICATION_ID,
        context.getString(R.string.auto_break_started_title),
        context.getString(R.string.auto_break_started_message, focusedMinutes, breakMinutes)
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
