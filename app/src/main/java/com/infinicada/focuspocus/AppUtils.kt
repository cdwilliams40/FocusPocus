package com.infinicada.focuspocus

import android.content.Context
import android.content.pm.ApplicationInfo

object AppUtils {

    /**
     * Retrieves the application name (label) for a given package name.
     * If the app is not found or an error occurs, returns the package name as a fallback.
     */
    fun getAppName(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(
                pm.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    /**
     * A preinstalled system app that has never received an update — OS plumbing
     * and OEM utilities rather than something the user chose to install. The
     * pickers hide these. User-facing preinstalls (YouTube, Chrome, Maps…) take
     * store updates, which set FLAG_UPDATED_SYSTEM_APP and keep them visible
     * and blockable.
     */
    fun isStockSystemApp(appInfo: ApplicationInfo): Boolean {
        return appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
            appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
    }

    /**
     * Whether a whitelist enchantment may block [packageName]. A whitelist can
     * only ever exempt apps the picker offers, so it must only block that same
     * universe: launchable apps that aren't stock system apps. Everything else
     * (Android Auto's projection UI, permission dialogs, other unselectable
     * system surfaces) passes through — blocking it would leave the user no way
     * to whitelist it. Blacklists are unaffected: their apps were picked
     * explicitly, so an explicit match always blocks.
     */
    fun isWhitelistBlockable(context: Context, packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            pm.getLaunchIntentForPackage(packageName) != null &&
                !isStockSystemApp(pm.getApplicationInfo(packageName, 0))
        } catch (_: Exception) {
            false
        }
    }
}
