package com.infinicada.focuspocus

import android.content.Context

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
}
