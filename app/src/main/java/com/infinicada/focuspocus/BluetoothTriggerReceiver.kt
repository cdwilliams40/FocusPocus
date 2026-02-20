package com.infinicada.focuspocus

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class BluetoothTriggerReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BtTriggerReceiver"
    }

    private val gson = Gson()

    override fun onReceive(context: Context, intent: Intent) {
        val device = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        } ?: return

        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val triggers = AutoTriggerHelper.loadTriggers(prefs).filter { it.type == TriggerType.BLUETOOTH && it.enabled }

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val deviceAddress = device.address ?: return
                val matchedTrigger = triggers.find {
                    it.identifier.equals(deviceAddress, ignoreCase = true)
                }
                if (matchedTrigger != null) {
                    val isManualFocusActive = prefs.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
                    if (!isManualFocusActive) {
                        AutoTriggerHelper.activatePreset(context, prefs, matchedTrigger.presetId)
                        // Persist trigger ID in prefs so a receiver re-instantiation doesn't
                        // lose track of which trigger activated focus
                        prefs.edit()
                            .putString(Constants.PrefsKeys.LAST_BT_TRIGGER_ID, matchedTrigger.id)
                            .apply()
                        AutoTriggerHelper.incrementServicesTriggerCount(prefs)
                    }
                }
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val lastTriggerId = prefs.getString(Constants.PrefsKeys.LAST_BT_TRIGGER_ID, null)
                    ?: return
                val deviceAddress = device.address ?: return
                val matchedTrigger = triggers.find {
                    it.identifier.equals(deviceAddress, ignoreCase = true)
                }
                if (matchedTrigger != null && matchedTrigger.id == lastTriggerId) {
                    val isManualFocusActive = prefs.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
                    if (isManualFocusActive) {
                        SessionRecorder.record(prefs, gson)
                        prefs.edit()
                            .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
                            .putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, 0)
                            .remove(Constants.PrefsKeys.LAST_BT_TRIGGER_ID)
                            .apply()
                        DndController.updateDndState(context)
                        AutoTriggerHelper.incrementServicesTriggerCount(prefs)
                    } else {
                        prefs.edit().remove(Constants.PrefsKeys.LAST_BT_TRIGGER_ID).apply()
                    }
                    Log.d(TAG, "Bluetooth disconnected; cleared trigger $lastTriggerId")
                }
            }
        }
    }
}
