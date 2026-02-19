package com.infinicada.focuspocus

import android.content.Context
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build

object NetworkUtils {

    /**
     * Extracts SSID from NetworkCapabilities (Android Q+).
     */
    fun getSsidFromCapabilities(capabilities: NetworkCapabilities): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val transportInfo = capabilities.transportInfo
            if (transportInfo is WifiInfo) {
                return normalizeSsid(transportInfo.ssid)
            }
        }
        return null
    }

    /**
     * Gets the current Wi-Fi SSID using WifiManager (Deprecated in newer Android versions).
     */
    fun getLegacyWifiSsid(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val info = wifiManager?.connectionInfo
        return normalizeSsid(info?.ssid)
    }

    private fun normalizeSsid(ssid: String?): String? {
        if (ssid != null && ssid != "<unknown ssid>") {
            return ssid.removeSurrounding("\"")
        }
        return null
    }
}
