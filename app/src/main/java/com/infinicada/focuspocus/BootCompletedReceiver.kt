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
                // Bring ritual state in line with the schedule table now — a
                // ritual whose window closed while the phone was off ends, and
                // one whose window is open starts — then re-arm the backstop
                // alarm, which neither reboots nor clock changes preserve.
                RitualAlarmScheduler.onAlarmFired(context)
            }
        }
    }
}
