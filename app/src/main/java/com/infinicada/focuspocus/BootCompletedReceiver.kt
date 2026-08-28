package com.infinicada.focuspocus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                DndController.updateDndState(context)
                DeviceOwnerManager.applySelfProtection(context)
                DeviceOwnerManager.syncSuspensions(context)
                // Alarms don't survive reboots (and clock changes move the
                // target) — re-arm the ritual backstop.
                RitualAlarmScheduler.scheduleNext(context)
                // Nor does the poller: BOOT_COMPLETED is one of the few places
                // a foreground service may be started from the background, so
                // fallback enforcement resumes here rather than waiting for the
                // user to open the app.
                ForegroundPollingService.syncRunState(context)
            }
        }
    }
}
