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
    private var lastActivatedTriggerId: String? = null

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
                        lastActivatedTriggerId = matchedTrigger.id
                    }
                }
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                if (lastActivatedTriggerId != null) {
                    val deviceAddress = device.address ?: return
                    val matchedTrigger = triggers.find {
                        it.identifier.equals(deviceAddress, ignoreCase = true)
                    }
                    if (matchedTrigger != null && matchedTrigger.id == lastActivatedTriggerId) {
                        val isManualFocusActive = prefs.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
                        if (isManualFocusActive) {
                            recordSession(context, prefs)
                            prefs.edit()
                                .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
                                .putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, 0)
                                .apply()
                            DndController.updateDndState(context)
                        }
                        lastActivatedTriggerId = null
                    }
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

    private fun recordSession(context: Context, prefs: android.content.SharedPreferences) {
        val startTime = prefs.getLong(Constants.PrefsKeys.SESSION_START_TIME, 0L)
        if (startTime == 0L) return
        val endTime = System.currentTimeMillis()
        val durationMin = ((endTime - startTime) / 60000).toInt()
        if (durationMin < 1) return
        val blockerName = prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null) ?: "Unknown"
        val breaksUsed = prefs.getInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, 0)
        val session = FocusSession(
            startTimeMillis = startTime,
            endTimeMillis = endTime,
            durationMinutes = durationMin,
            blockerName = blockerName,
            breaksUsed = breaksUsed
        )
        val json = prefs.getString(Constants.PrefsKeys.FOCUS_SESSIONS, null)
        val sessions: MutableList<FocusSession> = if (json != null) {
            try {
                val type = object : TypeToken<MutableList<FocusSession>>() {}.type
                gson.fromJson(json, type)
            } catch (_: Exception) { mutableListOf() }
        } else mutableListOf()
        sessions.add(session)
        val pruned = if (sessions.size > 500) sessions.drop(sessions.size - 500) else sessions
        prefs.edit()
            .putString(Constants.PrefsKeys.FOCUS_SESSIONS, gson.toJson(pruned))
            .remove(Constants.PrefsKeys.SESSION_START_TIME)
            .apply()
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
}
