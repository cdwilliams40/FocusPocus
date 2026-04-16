package com.infinicada.focuspocus

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

object AppUtils {

    private const val TAG = "AppUtils"

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
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error resolving app name for $packageName", e)
            packageName
        }
    }
}
