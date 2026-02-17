package com.infinicada.focuspocus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class WifiTriggerService : Service() {

    companion object {
        private const val TAG = "WifiTriggerService"
        private const val CHANNEL_ID = "wifi_trigger_channel"
        private const val NOTIFICATION_ID = 9001
    }

    private val gson = Gson()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastActivatedTriggerId: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerNetworkCallback()
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Wi-Fi Triggers",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitoring Wi-Fi for auto-trigger activation"
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.fplogo_round)
            .setContentTitle("Focus Pocus")
            .setContentText("Monitoring Wi-Fi for auto-triggers")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val ssid = extractSsid(capabilities)
                if (ssid != null) {
                    onWifiConnected(ssid)
                }
            }

            override fun onLost(network: Network) {
                onWifiDisconnected()
            }
        }

        cm.registerNetworkCallback(request, networkCallback!!)
    }

    private fun extractSsid(capabilities: NetworkCapabilities): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val transportInfo = capabilities.transportInfo
            if (transportInfo is WifiInfo) {
                val ssid = transportInfo.ssid
                if (ssid != null && ssid != "<unknown ssid>") {
                    return ssid.removeSurrounding("\"")
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val info = wifiManager.connectionInfo
            val ssid = info?.ssid
            if (ssid != null && ssid != "<unknown ssid>") {
                return ssid.removeSurrounding("\"")
            }
        }
        return null
    }

    private fun onWifiConnected(ssid: String) {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val triggers = loadTriggers(prefs).filter { it.type == TriggerType.WIFI && it.enabled }

        val matchedTrigger = triggers.find { it.identifier.equals(ssid, ignoreCase = true) }
        if (matchedTrigger != null) {
            val isManualFocusActive = prefs.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
            if (!isManualFocusActive) {
                activatePreset(prefs, matchedTrigger.presetId)
                lastActivatedTriggerId = matchedTrigger.id
            }
        }
    }

    private fun onWifiDisconnected() {
        if (lastActivatedTriggerId != null) {
            val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val isManualFocusActive = prefs.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
            if (isManualFocusActive) {
                recordSession(prefs)
                prefs.edit()
                    .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
                    .putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, 0)
                    .apply()
                DndController.updateDndState(this)
            }
            lastActivatedTriggerId = null
        }
    }

    private fun recordSession(prefs: android.content.SharedPreferences) {
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

    private fun activatePreset(prefs: android.content.SharedPreferences, presetId: String) {
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

        DndController.updateDndState(this)
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
