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
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.model.Schedule
import java.util.Date

/**
 * Ongoing "session in progress" notification with a live countdown.
 *
 * While a session is active (manual spell, scheduled ritual, or talisman) a
 * silent ongoing notification names the session and counts down to its end
 * via the system chronometer (setUsesChronometer + setChronometerCountDown),
 * which ticks every second with zero polling from the app. Untimed sessions
 * count up from the session start instead, and breaks count down to the
 * moment focus resumes.
 *
 * State observation is centralized: [attach] registers a SharedPreferences
 * listener on the session keys, so every mutation path — UI, accessibility
 * service, NFC/QR triggers, auto-breaks — refreshes the notification without
 * per-call-site wiring. A single prefs commit changes several keys, so
 * refreshes are debounced into one post. The accessibility service also
 * re-asserts the notification on its minute tick, healing swipe-dismissals
 * (possible on Android 14+) and posts dropped by rate limiting.
 *
 * Uses the framework Notification.Builder (androidx-free like the rest of
 * the session plumbing), every entry point is permission-guarded, and
 * nothing here is allowed to throw — same contract as [ProgressionNotifier].
 */
object SessionNotifier {
    private const val TAG = "SessionNotifier"
    private const val UPDATE_DEBOUNCE_MS = 200L

    /** Session-state keys whose changes can alter what the notification shows. */
    private val WATCHED_KEYS = setOf(
        Constants.PrefsKeys.MANUAL_FOCUS_MODE,
        Constants.PrefsKeys.FOCUS_TAG_ID,
        Constants.PrefsKeys.IS_ON_BREAK,
        Constants.PrefsKeys.ACTIVE_BLOCKER,
        Constants.PrefsKeys.ACTIVE_BLOCKERS,
        Constants.PrefsKeys.ACTIVE_SCHEDULE_ID,
        // A ritual's display name comes from the schedule list, so a rename
        // mid-session must refresh the notification too.
        Constants.PrefsKeys.SCHEDULES,
        Constants.PrefsKeys.SESSION_START_TIME,
        Constants.PrefsKeys.FOCUS_END_TIME_MILLIS,
        Constants.PrefsKeys.SCHEDULE_END_TIME_MILLIS,
        Constants.PrefsKeys.BREAK_END_TIME_MILLIS
    )

    /** What the notification should show. A null resolve result means cancel. */
    data class CountdownState(
        /** Ritual name or joined enchantment names; null when neither is known. */
        val sessionName: String?,
        val isRitual: Boolean,
        val onBreak: Boolean,
        /** Wall-clock end the chronometer counts down to; null = no countdown. */
        val countdownEndMillis: Long?,
        /** Count-up anchor for untimed sessions; null = no chronometer at all. */
        val countUpStartMillis: Long?
    )

    private val gson = Gson()

    // Lazy so unit tests can exercise resolveState without an Android Looper.
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    @Volatile private var appContext: Context? = null
    private val updateRunnable = Runnable { appContext?.let { update(it) } }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in WATCHED_KEYS) {
            handler.removeCallbacks(updateRunnable)
            handler.postDelayed(updateRunnable, UPDATE_DEBOUNCE_MS)
        }
    }

    /**
     * Creates the channel. Called from Application.onCreate so it exists
     * before any component posts to it. IMPORTANCE_LOW keeps the ongoing
     * notification silent; no badge because it reflects a standing state,
     * not something unread.
     */
    fun createChannel(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                Constants.FOCUS_SESSION_CHANNEL_ID,
                context.getString(R.string.focus_session_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.focus_session_channel_description)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create focus session channel", e)
        }
    }

    /**
     * Starts observing session state and reconciles the notification with it.
     * Called once from Application.onCreate; the initial [update] restores the
     * notification after reboots and process death, and clears a stale one
     * left over from a session that ended while the process was down.
     */
    fun attach(context: Context) {
        appContext = context.applicationContext
        try {
            context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefsListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register session state listener", e)
        }
        update(context)
    }

    /** Posts, refreshes, or cancels the notification to match current state. */
    fun update(context: Context) {
        try {
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val state = resolveState(prefs, gson, System.currentTimeMillis())
            if (state == null) {
                nm.cancel(Constants.SESSION_COUNTDOWN_NOTIFICATION_ID)
                return
            }
            if (!canPost(context)) return
            nm.notify(Constants.SESSION_COUNTDOWN_NOTIFICATION_ID, build(context, state))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update session notification", e)
        }
    }

    /**
     * Derives the notification content from persisted session state. Pure
     * with respect to Android services so unit tests can drive it directly.
     *
     * A session is active when manual focus mode is on or a talisman anchors
     * it — the same predicate the blocking logic uses. The countdown target
     * is the break end while on a break, else the schedule window's end for
     * rituals, else the focus duration's end; whichever applies must still be
     * in the future (an overdue end, waiting for the service's minute tick to
     * reap it, falls back to counting elapsed time rather than showing a
     * negative countdown).
     */
    fun resolveState(prefs: SharedPreferences, gson: Gson, nowMillis: Long): CountdownState? {
        val manualFocus = prefs.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
        val focusTagId = prefs.getString(Constants.PrefsKeys.FOCUS_TAG_ID, null)
        if (!manualFocus && focusTagId == null) return null

        val scheduleId = prefs.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)
        val scheduleName = scheduleId?.let { loadScheduleName(prefs, gson, it) }
        val sessionName = scheduleName
            ?: activeBlockerNames(prefs, gson).joinToString(", ").ifEmpty { null }

        if (prefs.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)) {
            val breakEnd = prefs.getLong(Constants.PrefsKeys.BREAK_END_TIME_MILLIS, 0L)
            return CountdownState(
                sessionName = sessionName,
                isRitual = scheduleName != null,
                onBreak = true,
                countdownEndMillis = breakEnd.takeIf { it > nowMillis },
                countUpStartMillis = null
            )
        }

        val endMillis = if (scheduleId != null) {
            prefs.getLong(Constants.PrefsKeys.SCHEDULE_END_TIME_MILLIS, 0L)
        } else {
            prefs.getLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, 0L)
        }
        // Talisman-only sessions have no recorded start, so they get a plain
        // notification with no chronometer rather than a bogus elapsed time.
        val startMillis = prefs.getLong(Constants.PrefsKeys.SESSION_START_TIME, 0L)
        return CountdownState(
            sessionName = sessionName,
            isRitual = scheduleName != null,
            onBreak = false,
            countdownEndMillis = endMillis.takeIf { it > nowMillis },
            countUpStartMillis = startMillis.takeIf { it in 1..nowMillis }
        )
    }

    private fun activeBlockerNames(prefs: SharedPreferences, gson: Gson): List<String> {
        val json = prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKERS, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                val parsed: List<String>? = gson.fromJson(json, type)
                if (parsed != null) return parsed
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing active blockers JSON", e)
            }
        }
        val single = prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null)
        return if (single != null) listOf(single) else emptyList()
    }

    private fun loadScheduleName(prefs: SharedPreferences, gson: Gson, scheduleId: String): String? {
        val json = prefs.getString(Constants.PrefsKeys.SCHEDULES, null) ?: return null
        return try {
            val type = object : TypeToken<List<Schedule>>() {}.type
            gson.fromJson<List<Schedule>?>(json, type)?.find { it.id == scheduleId }?.name
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing schedules JSON", e)
            null
        }
    }

    private fun build(context: Context, state: CountdownState): Notification {
        // Class name string instead of MainActivity::class keeps this file
        // free of UI-layer imports.
        val intent = Intent().setClassName(context, "com.infinicada.focuspocus.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val title = when {
            state.onBreak -> context.getString(R.string.focus_session_notification_break)
            state.sessionName == null -> context.getString(R.string.focus_session_notification_title)
            state.isRitual ->
                context.getString(R.string.focus_session_notification_title_ritual, state.sessionName)
            else -> context.getString(R.string.focus_session_notification_title_spell, state.sessionName)
        }
        val text = when {
            state.onBreak && state.countdownEndMillis != null -> {
                val resumeTime = formatTime(context, state.countdownEndMillis)
                if (state.sessionName != null) {
                    context.getString(
                        R.string.focus_session_notification_resumes_at_named,
                        state.sessionName, resumeTime
                    )
                } else {
                    context.getString(R.string.focus_session_notification_resumes_at, resumeTime)
                }
            }
            state.onBreak -> state.sessionName
                ?: context.getString(R.string.focus_session_notification_unlimited)
            state.countdownEndMillis != null -> context.getString(
                R.string.focus_session_notification_ends_at,
                formatTime(context, state.countdownEndMillis)
            )
            else -> context.getString(R.string.focus_session_notification_unlimited)
        }

        return Notification.Builder(context, Constants.FOCUS_SESSION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.fplogo_round)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .apply {
                when {
                    state.countdownEndMillis != null -> {
                        // The system chronometer ticks down to `when` live.
                        setShowWhen(true)
                        setWhen(state.countdownEndMillis)
                        setUsesChronometer(true)
                        setChronometerCountDown(true)
                    }
                    state.countUpStartMillis != null -> {
                        setShowWhen(true)
                        setWhen(state.countUpStartMillis)
                        setUsesChronometer(true)
                    }
                    else -> setShowWhen(false)
                }
            }
            .build()
    }

    private fun formatTime(context: Context, millis: Long): String =
        android.text.format.DateFormat.getTimeFormat(context).format(Date(millis))

    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return true
    }
}
