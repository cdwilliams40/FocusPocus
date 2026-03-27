package com.infinicada.focuspocus

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

class BrowserDetector(private val context: Context) {

    companion object {
        // Fallback list of known browsers
        internal val KNOWN_BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.fenix",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.brave.browser",
            "com.duckduckgo.mobile.android",
            "com.sec.android.app.sbrowser",
            "com.vivaldi.browser",
            "com.kiwibrowser.browser",
            "org.chromium.chrome"
        )
    }

    fun getBrowserPackages(): Set<String> {
        val packages = mutableSetOf<String>()
        packages.addAll(KNOWN_BROWSER_PACKAGES)

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        // We use MATCH_ALL to get all browsers including those that might not be default.
        // MATCH_ALL was added in API 23.
        val flags = PackageManager.MATCH_ALL

        try {
            val resolveInfos = context.packageManager.queryIntentActivities(intent, flags)
            for (info in resolveInfos) {
                info.activityInfo?.packageName?.let { packages.add(it) }
            }
        } catch (e: Exception) {
            // If dynamic detection fails, we still have the static list.
        }

        return packages
    }
}
