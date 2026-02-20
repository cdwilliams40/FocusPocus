package com.infinicada.focuspocus

import android.content.Context
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build

object NetworkUtils {

    /**
     * Extracts SSID from NetworkCapabilities (Android Q+).
     * @param capabilities The NetworkCapabilities object from ConnectivityManager.
     * @return The SSID without quotes, or null if not available or unknown.
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
     * This method suppresses the deprecation warning as it is intended for use on older Android versions
     * or as a fallback where NetworkCapabilities are not available.
     * @param context The application context.
     * @return The SSID without quotes, or null if not available or unknown.
     */
    fun getLegacyWifiSsid(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val info = wifiManager?.connectionInfo
        return normalizeSsid(info?.ssid)
    }

    /**
     * Helper to normalize SSID by removing quotes and checking for unknown values.
     */
    private fun normalizeSsid(ssid: String?): String? {
        if (ssid != null && ssid != "<unknown ssid>") {
            return ssid.removeSurrounding("\"")
        }
        return null
    }
}
