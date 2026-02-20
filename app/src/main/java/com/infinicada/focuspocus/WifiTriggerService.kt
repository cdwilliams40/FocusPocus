package com.infinicada.focuspocus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class WifiTriggerService : Service() {

    companion object {
        private const val TAG = "WifiTriggerService"
    }

    private val gson = Gson()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(Constants.WIFI_TRIGGER_NOTIFICATION_ID, buildNotification())
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
            Constants.WIFI_TRIGGER_CHANNEL_ID,
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
        return NotificationCompat.Builder(this, Constants.WIFI_TRIGGER_CHANNEL_ID)
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            NetworkUtils.getSsidFromCapabilities(capabilities)
        } else {
            NetworkUtils.getLegacyWifiSsid(applicationContext)
        }
    }

    private fun onWifiConnected(ssid: String) {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val triggers = AutoTriggerHelper.loadTriggers(prefs).filter { it.type == TriggerType.WIFI && it.enabled }

        val matchedTrigger = triggers.find { it.identifier.equals(ssid, ignoreCase = true) }
        if (matchedTrigger != null) {
            val isManualFocusActive = prefs.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
            if (!isManualFocusActive) {
                AutoTriggerHelper.activatePreset(this, prefs, matchedTrigger.presetId)
                // Persist the trigger ID so we can still handle disconnect after a service restart
                prefs.edit().putString(Constants.PrefsKeys.LAST_WIFI_TRIGGER_ID, matchedTrigger.id).apply()
                AutoTriggerHelper.incrementServicesTriggerCount(prefs)
            }
        }
    }

    private fun onWifiDisconnected() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        // Read from prefs so this works correctly even after a service restart
        val lastTriggerId = prefs.getString(Constants.PrefsKeys.LAST_WIFI_TRIGGER_ID, null)
            ?: return

        val isManualFocusActive = prefs.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
        if (isManualFocusActive) {
            SessionRecorder.record(prefs, gson)
            prefs.edit()
                .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
                .putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, 0)
                .remove(Constants.PrefsKeys.LAST_WIFI_TRIGGER_ID)
                .apply()
            AutoTriggerHelper.incrementServicesTriggerCount(prefs)
            DndController.updateDndState(this)
        } else {
            // Focus was stopped by another means; just clear the stored trigger
            prefs.edit().remove(Constants.PrefsKeys.LAST_WIFI_TRIGGER_ID).apply()
        }

        Log.d(TAG, "Wi-Fi disconnected; cleared trigger $lastTriggerId")
    }
}
