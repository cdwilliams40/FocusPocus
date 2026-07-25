package com.infinicada.focuspocus

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.infinicada.focuspocus.enforcement.ActiveEnforcer
import com.infinicada.focuspocus.enforcement.BlockingEngine
import com.infinicada.focuspocus.enforcement.EnforcementController

/**
 * The preferred foreground-app detector: `TYPE_WINDOW_STATE_CHANGED` names the
 * app the instant its window opens, so a block lands before the app is useful,
 * and it costs no polling.
 *
 * Enforcement itself lives in [BlockingEngine] — this class only detects, and
 * records that it is alive. When accessibility is unavailable, dead, or the user
 * has opted out of it, [com.infinicada.focuspocus.enforcement.FallbackEnforcementService]
 * drives the same engine from UsageStats polling instead.
 */
class MyAccessibilityService : AccessibilityService() {

    private companion object {
        const val TAG = "MyAccessibilityService"

        /**
         * Minimum gap between liveness stamps. Every app switch would otherwise
         * write to SharedPreferences and re-run the reconcile check on the event
         * path, for a signal that only needs to be minutes-fresh.
         */
        const val HEARTBEAT_INTERVAL_MS = 30_000L
    }

    private lateinit var sharedPreferences: SharedPreferences

    private var engine: BlockingEngine? = null

    private var lastHeartbeatMillis = 0L

    /**
     * Enforcement mode is a setting, so it can change while this service is
     * connected — a user switching to fallback mode has to actually stop this one
     * driving, or both detectors would report the same app open and double-count
     * pact attempts and friction escalation.
     */
    private val modeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Constants.PrefsKeys.ENFORCEMENT_MODE) applyMode()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.registerOnSharedPreferenceChangeListener(modeListener)
        // Stamp before reconciling: a stale stamp from a previous boot would
        // otherwise make reconcile start the fallback we're about to replace.
        recordHeartbeat(force = true)
        applyMode()
        Log.d(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        recordHeartbeat()
        // Null whenever this service isn't the active enforcer, so standing down
        // costs nothing per event.
        engine?.onForegroundApp(packageName)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::sharedPreferences.isInitialized) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(modeListener)
        }
        engine?.stop()
        engine = null
        // Accessibility is going away, so tell reconcile that directly rather than
        // letting it consult a setting that still lists us. A revoked or killed
        // service should degrade to slower blocking, not to none.
        //
        // Best effort: Android doesn't guarantee this callback when the service is
        // force-stopped, which is why process start, boot, and the stale-heartbeat
        // check all reconcile too.
        EnforcementController.reconcile(this, accessibilityEnabled = false)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    /** Creates or tears down the engine to match the user's enforcement mode. */
    private fun applyMode() {
        // accessibilityEnabled = true, not the probe: this service is connected,
        // which is better evidence than either the settings string or the
        // heartbeat. Reading the probe here would risk standing down — and
        // stopping all blocking — over a stale stamp we just wrote.
        val shouldDrive = EnforcementController.activeEnforcer(
            this,
            accessibilityEnabled = true
        ) == ActiveEnforcer.ACCESSIBILITY

        if (shouldDrive) {
            if (engine == null) {
                engine = BlockingEngine(this, onTick = { recordHeartbeat() })
                    .also { it.start() }
                Log.d(TAG, "Driving enforcement")
            }
        } else {
            engine?.let {
                it.stop()
                Log.d(TAG, "Standing down — the polling fallback is enforcing instead")
            }
            engine = null
        }
        // Whichever way that went, the fallback service's should-it-run answer
        // just changed.
        EnforcementController.reconcile(this, accessibilityEnabled = true)
    }

    /**
     * Records that events are still being delivered. Rate-limited to
     * [HEARTBEAT_INTERVAL_MS], since the signal only needs to be minutes-fresh.
     *
     * A gap long enough to have looked dead is also the one case where the
     * fallback may have stepped in for us, so that — and only that — re-runs
     * reconcile to stand it back down. Reconcile costs three binder calls, which
     * is not something to spend on the event path every 30 seconds for a state
     * that almost never changes.
     */
    private fun recordHeartbeat(force: Boolean = false) {
        if (!this::sharedPreferences.isInitialized) return
        val now = System.currentTimeMillis()
        val gap = now - lastHeartbeatMillis
        if (!force && gap < HEARTBEAT_INTERVAL_MS) return
        val wasStale = lastHeartbeatMillis > 0L && gap >= EnforcementController.HEARTBEAT_STALE_MS
        lastHeartbeatMillis = now
        sharedPreferences.edit()
            .putLong(Constants.PrefsKeys.ACCESSIBILITY_HEARTBEAT_MILLIS, now)
            .apply()
        if (wasStale) {
            Log.d(TAG, "Heartbeat recovered after ${gap}ms — reclaiming enforcement")
            EnforcementController.reconcile(this, accessibilityEnabled = true)
        }
    }
}
