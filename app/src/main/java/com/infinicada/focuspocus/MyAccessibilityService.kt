package com.infinicada.focuspocus

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * The primary foreground detector: Android's accessibility API tells us which
 * app just took the foreground, and [EnforcementEngine] decides what to do
 * about it.
 *
 * Deliberately thin. Every blocking decision lives in the engine so the
 * UsageStats fallback ([ForegroundPollingService]) enforces identically — see
 * `docs/PLAY_ACCESSIBILITY_DECLARATION.md` for why that separation is a
 * distribution requirement and not merely tidiness.
 *
 * The service reads exactly one field from the events it receives:
 * `event.packageName`. It subscribes to `typeWindowStateChanged` only and does
 * not declare `canRetrieveWindowContent`, so it cannot read screen content —
 * a property the Play declaration rests on. Keep it that way.
 *
 * It stands down entirely under [EnforcementMode.POLLING]: the engine is not
 * started and events are dropped, so only one detector ever enforces. The mode
 * is watched rather than read once, because the user can change it in Settings
 * while this service is connected.
 */
class MyAccessibilityService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences

    /** Non-null exactly while this detector is the one enforcing. */
    private var engine: EnforcementEngine? = null

    private val modeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Constants.PrefsKeys.ENFORCEMENT_MODE) applyEnforcementMode()
        }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")
        prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(modeListener)
        applyEnforcementMode()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(modeListener)
        }
        engine?.stop()
        engine = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val activeEngine = engine ?: return
        if (event == null) return
        // The service subscribes to typeWindowStateChanged only (see
        // accessibility_service_config.xml), but the framework is free to
        // deliver other types, so this stays a guard rather than a switch.
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        // onForegroundPackage never throws: a dead service means nothing is
        // blocked until the user manually re-enables accessibility.
        activeEngine.onForegroundPackage(packageName)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    /**
     * Brings this detector into line with the stored mode, and hands the poller
     * the same chance. Starting an already-started engine, or stopping an
     * absent one, is a no-op, so this is safe to call on every change.
     */
    private fun applyEnforcementMode() {
        val shouldEnforce = EnforcementMode.of(prefs) == EnforcementMode.ACCESSIBILITY
        if (shouldEnforce && engine == null) {
            engine = EnforcementEngine(this).also { it.start() }
            Log.d(TAG, "Enforcing from accessibility events")
        } else if (!shouldEnforce && engine != null) {
            engine?.stop()
            engine = null
            Log.d(TAG, "Standing down — the poller is enforcing")
        }
        ForegroundPollingService.syncRunState(this)
    }

    private companion object {
        const val TAG = "MyAccessibilityService"
    }
}
