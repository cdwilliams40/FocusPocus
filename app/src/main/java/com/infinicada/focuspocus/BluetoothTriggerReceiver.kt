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
        val triggers = loadTriggers(prefs).filter { it.type == TriggerType.BLUETOOTH && it.enabled }

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val deviceAddress = device.address ?: return
                val matchedTrigger = triggers.find {
                    it.identifier.equals(deviceAddress, ignoreCase = true)
                }
                if (matchedTrigger != null) {
                    val isManualFocusActive = prefs.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
                    if (!isManualFocusActive) {
                        activatePreset(context, prefs, matchedTrigger.presetId)
                        // Persist trigger ID in prefs so a receiver re-instantiation doesn't
                        // lose track of which trigger activated focus
                        prefs.edit()
                            .putString(Constants.PrefsKeys.LAST_BT_TRIGGER_ID, matchedTrigger.id)
                            .apply()
                        incrementServicesTriggerCount(prefs)
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
                        incrementServicesTriggerCount(prefs)
                    } else {
                        prefs.edit().remove(Constants.PrefsKeys.LAST_BT_TRIGGER_ID).apply()
                    }
                    Log.d(TAG, "Bluetooth disconnected; cleared trigger $lastTriggerId")
                }
            }
        }
    }

    private fun activatePreset(context: Context, prefs: android.content.SharedPreferences, presetId: String) {
        val presetsJson = prefs.getString(Constants.PrefsKeys.FOCUS_PRESETS, null) ?: return
        val presets: List<FocusPreset> = try {
            val type = object : TypeToken<List<FocusPreset>>() {}.type
            gson.fromJson(presetsJson, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing presets", e)
            return
        }
        val preset = presets.find { it.id == presetId } ?: return

        val blockersJson = prefs.getString(Constants.PrefsKeys.BLOCKER_LISTS, null) ?: return
        val blockers: List<Blocker> = try {
            val type = object : TypeToken<List<Blocker>>() {}.type
            gson.fromJson(blockersJson, type)
        } catch (e: Exception) { return }

        val blocker = blockers.find { it.name == preset.blockerName } ?: return

        val focusTimeRemaining = if (preset.durationMinutes > 0) preset.durationMinutes * 60 else 0
        prefs.edit()
            .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
            .putString(Constants.PrefsKeys.ACTIVE_BLOCKER, blocker.name)
            .putInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, preset.durationMinutes)
            .putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, focusTimeRemaining)
            .putBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, preset.breaksEnabled)
            .putLong(Constants.PrefsKeys.SESSION_START_TIME, System.currentTimeMillis())
            .apply()

        DndController.updateDndState(context)
    }

    private fun loadTriggers(prefs: android.content.SharedPreferences): List<AutoTrigger> {
        val json = prefs.getString(Constants.PrefsKeys.AUTO_TRIGGERS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AutoTrigger>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun incrementServicesTriggerCount(prefs: android.content.SharedPreferences) {
        val current = prefs.getInt(Constants.PrefsKeys.SERVICES_TRIGGER_COUNT, 0)
        prefs.edit().putInt(Constants.PrefsKeys.SERVICES_TRIGGER_COUNT, current + 1).apply()
    }
}
