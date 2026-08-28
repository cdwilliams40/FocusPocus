package com.infinicada.focuspocus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * The policy-safe foreground detector: finds the app in front by polling
 * UsageStats, and drives the same [EnforcementEngine] the accessibility service
 * does.
 *
 * This exists so the app has an answer when Play denies or revokes the
 * accessibility declaration (see `docs/PLAY_ACCESSIBILITY_DECLARATION.md`).
 * It is slower — [POLL_INTERVAL_MS] plus however long UsageStats takes to
 * surface an event, so 1–2 s rather than ~50 ms — and it costs an ongoing
 * notification, which is why it is not the default. It is not weaker: every
 * decision still comes from the engine.
 *
 * Runs only while [EnforcementMode.POLLING] is selected; see [syncRunState],
 * which is the single place that decides whether this service should be alive.
 *
 * The UsageStats read is a binder call that walks an event stream, so it
 * happens on [pollThread] and only the resulting package name is handed to the
 * main thread. Polling stops entirely while the screen is off: nothing can come
 * to the foreground then, and a 1 Hz wakeup all night is exactly the kind of
 * battery drain that gets an app uninstalled.
 */
class ForegroundPollingService : Service() {

    private lateinit var engine: EnforcementEngine
    private var pollThread: HandlerThread? = null
    private var pollHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Last package handed to the engine, so only transitions are reported. */
    private var lastReportedPackage: String? = null

    private var polling = false
    private var screenReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> startPolling()
                Intent.ACTION_SCREEN_OFF -> {
                    stopPolling()
                    // The next unlock starts a fresh transition rather than
                    // replaying whatever was in front when the screen went off.
                    lastReportedPackage = null
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        engine = EnforcementEngine(this)
        engine.start()

        createNotificationChannel()
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply {
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        screenReceiverRegistered = true

        val thread = HandlerThread("fp-foreground-poll").also { it.start() }
        pollThread = thread
        pollHandler = Handler(thread.looper)

        if (isScreenOn()) startPolling()
        Log.d(TAG, "Polling enforcement started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Re-post the notification on every start request: startForeground must
        // be called promptly after startForegroundService, including on a
        // redeliver after the process was killed.
        startForegroundSafely()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPolling()
        if (screenReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering screen receiver", e)
            }
            screenReceiverRegistered = false
        }
        pollThread?.quitSafely()
        pollThread = null
        pollHandler = null
        if (this::engine.isInitialized) engine.stop()
        Log.d(TAG, "Polling enforcement stopped")
    }

    // -------------------------------------------------------------------------
    // The poll loop
    // -------------------------------------------------------------------------

    private fun startPolling() {
        if (polling) return
        polling = true
        pollHandler?.post(pollRunnable)
    }

    private fun stopPolling() {
        polling = false
        pollHandler?.removeCallbacks(pollRunnable)
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!polling) return
            try {
                val foreground = currentForegroundPackage(this@ForegroundPollingService)
                if (foreground != null && foreground != lastReportedPackage) {
                    lastReportedPackage = foreground
                    // The engine expects the main thread, like the accessibility
                    // service's event callback.
                    mainHandler.post { engine.onForegroundPackage(foreground) }
                }
            } catch (e: Exception) {
                // A failed read must not end the loop — enforcement would stop
                // silently, which is the one outcome this whole mode exists to
                // prevent.
                Log.e(TAG, "Foreground poll failed", e)
            }
            if (polling) pollHandler?.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private fun isScreenOn(): Boolean = try {
        (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: true
    } catch (e: Exception) {
        true
    }

    // -------------------------------------------------------------------------
    // Foreground notification
    // -------------------------------------------------------------------------

    private fun startForegroundSafely() {
        try {
            startForeground(Constants.ENFORCEMENT_NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            // Android 12+ refuses some background starts outright. Nothing to
            // salvage here; syncRunState runs again whenever the app is next
            // opened, which is a foreground context and always permitted.
            Log.e(TAG, "Could not enter the foreground", e)
            stopSelf()
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, Constants.ENFORCEMENT_CHANNEL_ID)
        .setSmallIcon(R.mipmap.fplogo_round)
        .setContentTitle(getString(R.string.enforcement_notification_title))
        .setContentText(getString(R.string.enforcement_notification_text))
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setSilent(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun createNotificationChannel() {
        try {
            val channel = NotificationChannel(
                Constants.ENFORCEMENT_CHANNEL_ID,
                getString(R.string.enforcement_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.enforcement_channel_description)
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.createNotificationChannel(channel)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create the enforcement notification channel", e)
        }
    }

    companion object {
        private const val TAG = "ForegroundPolling"

        /**
         * How often to ask UsageStats what is in front. One second is the
         * shortest interval that is worth the binder call: the event stream
         * itself lags by a beat, so polling faster buys latency the data
         * cannot deliver, and costs battery all day.
         */
        const val POLL_INTERVAL_MS = 1_000L

        /**
         * How far back each poll looks. Long enough to survive a missed tick or
         * a slow event stream, short enough that the scan stays cheap.
         */
        private const val POLL_LOOKBACK_MS = 10_000L

        /**
         * The package of the most recently resumed activity, or null when the
         * window held nothing (nothing changed, or usage access is missing).
         */
        fun currentForegroundPackage(context: Context, now: Long = System.currentTimeMillis()): String? {
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                    ?: return null
            val events = usageStatsManager.queryEvents(now - POLL_LOOKBACK_MS, now) ?: return null
            val event = UsageEvents.Event()
            var latestPackage: String? = null
            var latestTime = Long.MIN_VALUE
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED &&
                    event.timeStamp >= latestTime
                ) {
                    latestTime = event.timeStamp
                    latestPackage = event.packageName
                }
            }
            return latestPackage
        }

        /**
         * Starts or stops the poller to match the stored [EnforcementMode] —
         * the one place that decides whether this service should be alive.
         *
         * Call it from anywhere the answer could have changed: app resume, the
         * settings toggle, boot, and the accessibility service coming up or
         * going down.
         *
         * Starting a foreground service from the background is refused on
         * Android 12+, so a start attempt from a background caller can fail;
         * that is logged and left alone, because the next app resume calls this
         * again from a context where it is always allowed.
         */
        fun syncRunState(context: Context) {
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val wanted = EnforcementMode.of(prefs) == EnforcementMode.POLLING &&
                UsageStatsHelper.hasUsageStatsPermission(context)
            val intent = Intent(context, ForegroundPollingService::class.java)
            try {
                if (wanted) {
                    context.startForegroundService(intent)
                } else {
                    context.stopService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not ${if (wanted) "start" else "stop"} the poller", e)
            }
        }
    }
}
