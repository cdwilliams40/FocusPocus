package com.infinicada.focuspocus

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.regex.Pattern

class UrlUtilsTest {

    @Before
    fun setUp() {
        // Set up a robust URL matcher for testing, since Patterns.WEB_URL is not available in unit tests.
        // This regex is a simplified version of what Android's Patterns.WEB_URL might match.
        // It ensures we can verify the flow of looksLikeUrl.
        val testUrlPattern = Pattern.compile(
            "^(https?://)?([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}(:[0-9]+)?(/.*)?$"
        )

        UrlUtils.urlMatcher = { text ->
             testUrlPattern.matcher(text).matches()
        }
    }

    @Test
    fun testLooksLikeUrl_DelegatesToMatcher() {
        // This confirms UrlUtils uses the supplied matcher (or default)
        assertTrue(UrlUtils.looksLikeUrl("google.com"))
        assertFalse(UrlUtils.looksLikeUrl("not a url"))
    }

    @Test
    fun testExtractDomain_Simple() {
        assertEquals("google.com", UrlUtils.extractDomain("google.com"))
        assertEquals("google.com", UrlUtils.extractDomain("https://google.com"))
        assertEquals("google.com", UrlUtils.extractDomain("http://google.com"))
        assertEquals("google.com", UrlUtils.extractDomain("ftp://google.com"))
    }

    @Test
    fun testExtractDomain_WithPathAndQuery() {
        assertEquals("google.com", UrlUtils.extractDomain("google.com/foo/bar"))
        assertEquals("google.com", UrlUtils.extractDomain("https://google.com/search?q=foo"))
        assertEquals("google.com", UrlUtils.extractDomain("google.com/#fragment"))
    }

    @Test
    fun testExtractDomain_WithPort() {
        assertEquals("google.com", UrlUtils.extractDomain("google.com:8080"))
        assertEquals("google.com", UrlUtils.extractDomain("https://google.com:8080/foo"))
    }

    @Test
    fun testExtractDomain_WithUserInfo() {
        // Vulnerability fix: previously user:pass@google.com might have failed
        assertEquals("google.com", UrlUtils.extractDomain("user:pass@google.com"))
        assertEquals("google.com", UrlUtils.extractDomain("https://user:pass@google.com:8080/foo"))
    }

    @Test
    fun testExtractDomain_TrailingDot() {
        // Vulnerability fix: "example.com."
        assertEquals("example.com", UrlUtils.extractDomain("example.com."))
        assertEquals("example.com", UrlUtils.extractDomain("https://example.com./foo"))
    }

    @Test
    fun testExtractDomain_Whitespace() {
         assertEquals("example.com", UrlUtils.extractDomain(" example.com "))
    }

    @Test
    fun testExtractDomain_Invalid() {
        assertNull(UrlUtils.extractDomain(" "))
        // "http:// " -> host is empty -> removeSuffix(".") is empty -> null
        assertNull(UrlUtils.extractDomain("http://"))

        // "not a url" -> prepends https:// -> https://not a url -> invalid URI char (space) -> null
        assertNull(UrlUtils.extractDomain("not a url"))
    }

    @Test
    fun testExtractDomain_IPAddress() {
        assertEquals("192.168.1.1", UrlUtils.extractDomain("192.168.1.1"))
        assertEquals("192.168.1.1", UrlUtils.extractDomain("http://192.168.1.1"))
    }
}
