package com.infinicada.focuspocus

import android.util.Patterns
import java.net.URI

object UrlUtils {

    // Default matcher uses Android's Patterns.WEB_URL
    // We wrap it in a try-catch because in unit tests, Patterns.WEB_URL might be null or throw RuntimeException("Stub!")
    // In production (Android), it will work fine.
    internal var urlMatcher: (String) -> Boolean = { text ->
        try {
            // Use matches() to ensure the entire text is a valid URL,
            // preventing partial matches on non-URL text.
            Patterns.WEB_URL.matcher(text).matches()
        } catch (e: Exception) {
            // Fallback for tests if not explicitly overridden
            false
        }
    }

    fun looksLikeUrl(text: String): Boolean {
        if (text.isBlank()) return false
        return urlMatcher(text)
    }

    fun extractDomain(urlText: String): String? {
        try {
            val trimmed = urlText.trim()
            if (trimmed.isEmpty() || trimmed.length > 2048) return null

            // Handle URLs without scheme
            // Browsers often omit the scheme in the address bar
            val uriString = if (!trimmed.contains("://")) {
                "https://$trimmed"
            } else {
                trimmed
            }

            // Use java.net.URI for robust parsing
            val uri = URI(uriString)
            val host = uri.host ?: return null

            // Remove trailing dot if present (FQDN)
            val cleanHost = host.removeSuffix(".")

            if (cleanHost.isEmpty()) return null

            return cleanHost.lowercase()
        } catch (e: Exception) {
            return null
        }
    }
}
