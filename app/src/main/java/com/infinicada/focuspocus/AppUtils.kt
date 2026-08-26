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
     * Whether the pickers offer [packageName] — launchable, and not a stock
     * system app. This is the single definition of "an app the user can choose",
     * and every path that puts a package into a blocklist, or blocks one without
     * the user having named it, must respect it:
     *
     * - **Whitelist enforcement** blocks whatever the list *doesn't* name, so it
     *   may only block inside this universe. Android Auto's projection UI,
     *   permission dialogs and other unselectable system surfaces pass through —
     *   blocking them would leave the user no way to whitelist them.
     * - **Auto-banish** adds newly installed packages to opted-in blacklists
     *   without the user seeing them. An unpickable package added that way would
     *   be blocked with no row to untick, so it is filtered out here.
     *
     * Blacklists are filtered by the same rule, even though their entries were
     * named explicitly. A stored list can outlive the universe it was built from
     * — a package auto-banished by an older build, a stock system app picked
     * before the pickers hid them — and such an entry is unreachable: the picker
     * doesn't list it, so there is no row to untick. Rather than enforce a block
     * the user cannot lift, those entries go inert. They stay in storage
     * harmlessly, and start blocking again if the package ever becomes pickable.
     */
    fun isPickable(context: Context, packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            pm.getLaunchIntentForPackage(packageName) != null &&
                !isStockSystemApp(pm.getApplicationInfo(packageName, 0))
        } catch (_: Exception) {
            false
        }
    }
}
