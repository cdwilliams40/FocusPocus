package com.infinicada.focuspocus

import android.app.Application
import com.google.firebase.FirebaseApp
import com.infinicada.focuspocus.data.AppContainer
import com.infinicada.focuspocus.enforcement.EnforcementController

class FocusPocusApplication : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            android.util.Log.e("FocusPocusApplication", "Firebase initialization failed", e)
        }
        // Re-assert device-owner protections on every process start; both are
        // cheap no-ops when the app is not device owner.
        DeviceOwnerManager.applySelfProtection(this)
        DeviceOwnerManager.syncSuspensions(this)
        // Channel must exist before anything outside the accessibility service
        // posts a progression notification.
        ProgressionNotifier.createChannel(this)
        // Guards channel (seal-lifted alerts) — created here for the same reason.
        GuardNotifier.createChannel(this)
        // Rituals channel historically came from the accessibility service —
        // but the alarm backstop can fire ritual notifications when the
        // service has never run this boot, so the channel must exist here too.
        RitualNotifier.createChannel(this)
        // Arm the AlarmManager backstop for the next ritual transition.
        RitualAlarmScheduler.scheduleNext(this)
        // Session-countdown notification: create its channel, then start the
        // prefs observer that keeps the notification in sync with session
        // state. attach() also reconciles once now, restoring the countdown
        // after a reboot and clearing one left over from a session that ended
        // while the process was down.
        SessionNotifier.createChannel(this)
        SessionNotifier.attach(this)
        // Decide which detector should be enforcing and start it if it's the
        // polling fallback. Runs on every process start because that is the one
        // moment guaranteed to follow an accessibility service that was revoked
        // or killed without ever reaching its onDestroy.
        EnforcementController.reconcile(this)
    }
}
