package com.infinicada.focuspocus.enforcement

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.MainActivity
import com.infinicada.focuspocus.R

/**
 * Enforcement without an accessibility service: a foreground service that polls
 * UsageStats for the current app and feeds it to the same [BlockingEngine] the
 * accessibility path uses.
 *
 * This exists because accessibility is not something the app can rely on
 * keeping. Android 17's Advanced Protection Mode revokes it from every app that
 * hasn't declared `isAccessibilityTool` — which a focus blocker is not and
 * cannot honestly claim to be — Android 13+ restricted settings block sideloaded
 * builds from being granted it, Play's accessibility policy can withdraw
 * approval, and OEM battery optimizers kill the service on their own schedule.
 * In every one of those cases blocking used to just stop. Now it degrades.
 *
 * What the user gives up relative to accessibility:
 *
 * - **Latency.** A blocked app is visible for up to [POLL_INTERVAL_MS] plus
 *   however long the system takes to publish the usage event, instead of being
 *   caught on the window transition.
 * - **A permanent notification.** Foreground services must show one.
 * - **Two extra grants.** Usage access, and "display over other apps" — see
 *   [EnforcementController.isFallbackAvailable] for why the second is not
 *   optional.
 *
 * Polling stops while the screen is off: no app can be opened then, and the
 * engine's own minute tick keeps sessions, rituals and seals honest regardless.
 */
class FallbackEnforcementService : Service() {

    companion object {
        private const val TAG = "FallbackEnforcement"

        /**
         * One second: the roadmap's target for fallback detection, and about as
         * slow as blocking can be before a user can act inside the app first. The
         * query it drives is bounded to events since the previous poll, so the
         * cost is a small binder call rather than a scan.
         */
        private const val POLL_INTERVAL_MS = 1_000L

        private const val NOTIFICATION_ID = 9006

        /**
         * Starts the service if the caller is allowed to. Android 12+ forbids
         * starting a foreground service from the background, so this can legitimately
         * fail — from a boot receiver it is exempt, from the UI the app is visible,
         * and `SYSTEM_ALERT_WINDOW` (which fallback mode requires anyway) exempts
         * the rest. A failure is logged rather than propagated: the next
         * [EnforcementController.reconcile] tries again.
         */
        fun start(context: Context) {
            val intent = Intent(context, FallbackEnforcementService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not start fallback enforcement", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, FallbackEnforcementService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Could not stop fallback enforcement", e)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var engine: BlockingEngine? = null
    private var monitor: ForegroundAppMonitor? = null
    private var polling = false
    private var promoted = false
    private var screenReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ignored: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> startPolling()
                Intent.ACTION_SCREEN_OFF -> stopPolling()
            }
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!polling) return
            try {
                // Reported unconditionally, not only on change. The engine
                // tolerates repeats — its block debounce, open-transition guard and
                // pacing throttle all key on the package, and its blocker/limit
                // reads are cached on the stored JSON — so re-reporting costs a
                // couple of prefs reads and buys recovery from any transition the
                // event stream published late or dropped.
                monitor?.poll()?.let { engine?.onForegroundApp(it) }
            } catch (e: Exception) {
                // A failed poll must not end the loop — that would silently stop
                // all blocking until the user next opened the app.
                Log.e(TAG, "Poll failed", e)
            }
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        engine = BlockingEngine(this).also { it.start() }
        monitor = ForegroundAppMonitor(this)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        screenReceiverRegistered = true

        if (isScreenOn()) startPolling()
        Log.d(TAG, "Fallback enforcement started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android kills a foreground service that never calls startForeground, and
        // a redelivered start after a process kill lands here with the service
        // already created — so this belongs on the start path, not in onCreate.
        // Guarded because reconcile is called often (every accessibility
        // heartbeat), and re-posting the notification each time would burn
        // through the notification rate limit for no gain.
        if (!promoted) {
            promoteToForeground()
            promoted = true
        }
        // The engine holds all enforcement state in SharedPreferences, so a
        // restarted service picks up exactly where it left off.
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
        engine?.stop()
        engine = null
        monitor = null
        Log.d(TAG, "Fallback enforcement stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPolling() {
        if (polling) return
        polling = true
        // The slide mark is stale after a screen-off gap, so the first poll of a
        // new screen-on re-reads a full lookback window and finds whatever app
        // the user unlocked into.
        monitor?.reset()
        handler.post(pollRunnable)
    }

    private fun stopPolling() {
        if (!polling) return
        polling = false
        handler.removeCallbacks(pollRunnable)
        // Screen off ends the foreground app's continuous-use session, exactly as
        // it does on the accessibility path.
        engine?.onScreenOff()
    }

    private fun isScreenOn(): Boolean = try {
        (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: true
    } catch (e: Exception) {
        Log.e(TAG, "Could not read screen state, assuming on", e)
        true
    }

    private fun promoteToForeground() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Notifications blocked, or a start-not-allowed race. Nothing useful
            // to do but log — Android will stop us if it objects.
            Log.e(TAG, "Could not promote to foreground", e)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(
        this,
        Constants.ENFORCEMENT_CHANNEL_ID
    )
        .setSmallIcon(R.mipmap.fplogo_round)
        .setContentTitle(getString(R.string.enforcement_fallback_notification_title))
        .setContentText(getString(R.string.enforcement_fallback_notification_text))
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setOngoing(true)
        .setSilent(true)
        .setShowWhen(false)
        .build()

    private fun createChannel() {
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
            Log.e(TAG, "Failed to create enforcement channel", e)
        }
    }
}
