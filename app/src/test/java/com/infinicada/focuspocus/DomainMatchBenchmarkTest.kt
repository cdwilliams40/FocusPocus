package com.infinicada.focuspocus

import org.junit.Test
import org.junit.Assert.assertEquals
import kotlin.system.measureNanoTime

class DomainMatchBenchmarkTest {

    private fun legacyDomainMatches(navigatedDomain: String, blockedDomain: String): Boolean {
        if (blockedDomain.length > 255 || navigatedDomain.length > 2048) return false
        val nav = navigatedDomain.lowercase()
        val blocked = blockedDomain.lowercase()
        return nav == blocked || nav.endsWith(".$blocked")
    }

    private fun optimizedDomainMatches(navigatedDomain: String, blockedDomain: String): Boolean {
        if (blockedDomain.length > 255 || navigatedDomain.length > 2048) return false

        val navLen = navigatedDomain.length
        val blockedLen = blockedDomain.length

        if (navLen < blockedLen) return false

        if (navLen == blockedLen) {
            return navigatedDomain.regionMatches(0, blockedDomain, 0, blockedLen, ignoreCase = true)
        }

        // navLen > blockedLen
        // Check for ending with ".$blockedDomain"
        val offset = navLen - blockedLen
        // The character before the match must be a dot
        if (navigatedDomain[offset - 1] != '.') return false

        return navigatedDomain.regionMatches(offset, blockedDomain, 0, blockedLen, ignoreCase = true)
    }

    @Test
    fun testCorrectness() {
        val testCases = listOf(
            Triple("google.com", "google.com", true),
            Triple("GOOGLE.COM", "google.com", true),
            Triple("google.com", "GOOGLE.COM", true),
            Triple("mail.google.com", "google.com", true),
            Triple("MAIL.GOOGLE.COM", "google.com", true),
            Triple("mail.google.com", "GOOGLE.COM", true),
            Triple("google.com", "mail.google.com", false), // blocked is longer
            Triple("oogle.com", "google.com", false),
            Triple("agoogle.com", "google.com", false), // partial suffix but no dot
            Triple("google.co", "google.com", false),
            Triple("com", "google.com", false),
            Triple("", "google.com", false),
            Triple("google.com", "", false), // blocked empty
            Triple("sub.sub.google.com", "google.com", true),
            Triple("notgoogle.com", "google.com", false),
            Triple(".google.com", "google.com", true)
        )

        for ((nav, blocked, expected) in testCases) {
            val legacy = legacyDomainMatches(nav, blocked)
            val optimized = optimizedDomainMatches(nav, blocked)

            assertEquals("Legacy failed for '$nav' vs '$blocked'", expected, legacy)
            assertEquals("Optimized failed for '$nav' vs '$blocked'", expected, optimized)
        }
    }

    @Test
    fun benchmark() {
        val iterations = 1_000_000
        val blocked = "google.com"
        val hit = "mail.google.com"
        val miss = "facebook.com"
        val partial = "agoogle.com" // ends with but no dot

        // Warmup
        repeat(10000) {
            legacyDomainMatches(hit, blocked)
            optimizedDomainMatches(hit, blocked)
        }

        val legacyTime = measureNanoTime {
            repeat(iterations) {
                legacyDomainMatches(hit, blocked)
                legacyDomainMatches(miss, blocked)
                legacyDomainMatches(partial, blocked)
            }
        }

        val optimizedTime = measureNanoTime {
            repeat(iterations) {
                optimizedDomainMatches(hit, blocked)
                optimizedDomainMatches(miss, blocked)
                optimizedDomainMatches(partial, blocked)
            }
        }

        println("Legacy Time: ${legacyTime / 1_000_000} ms")
        println("Optimized Time: ${optimizedTime / 1_000_000} ms")

        if (optimizedTime > 0) {
            val improvement = legacyTime.toDouble() / optimizedTime.toDouble()
            println("Speedup: %.2fx".format(improvement))
        }
    }
}
