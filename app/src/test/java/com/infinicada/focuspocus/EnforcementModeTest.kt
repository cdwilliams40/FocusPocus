package com.infinicada.focuspocus

import com.infinicada.focuspocus.enforcement.ActiveEnforcer
import com.infinicada.focuspocus.enforcement.EnforcementMode
import com.infinicada.focuspocus.enforcement.resolveActiveEnforcer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which detector runs, for every combination of mode and availability. The
 * invariant that matters most: never two at once (they would double-count pact
 * attempts and friction escalation), and never zero when one is available.
 */
class EnforcementModeTest {

    private fun resolve(
        mode: EnforcementMode,
        accessibility: Boolean,
        fallback: Boolean
    ) = resolveActiveEnforcer(mode, accessibility, fallback)

    // ── AUTO: accessibility preferred, polling as the safety net ──

    @Test
    fun `auto prefers accessibility when it is alive`() {
        assertEquals(
            ActiveEnforcer.ACCESSIBILITY,
            resolve(EnforcementMode.AUTO, accessibility = true, fallback = true)
        )
    }

    @Test
    fun `auto falls back to polling when accessibility is gone`() {
        assertEquals(
            ActiveEnforcer.FALLBACK,
            resolve(EnforcementMode.AUTO, accessibility = false, fallback = true)
        )
    }

    @Test
    fun `auto enforces nothing when neither path is available`() {
        assertEquals(
            ActiveEnforcer.NONE,
            resolve(EnforcementMode.AUTO, accessibility = false, fallback = false)
        )
    }

    @Test
    fun `auto still uses accessibility when the fallback is unavailable`() {
        assertEquals(
            ActiveEnforcer.ACCESSIBILITY,
            resolve(EnforcementMode.AUTO, accessibility = true, fallback = false)
        )
    }

    // ── ACCESSIBILITY: the user opted out of polling, so losing the service
    //    genuinely means no enforcement rather than a silent downgrade ──

    @Test
    fun `accessibility mode uses the service when alive`() {
        assertEquals(
            ActiveEnforcer.ACCESSIBILITY,
            resolve(EnforcementMode.ACCESSIBILITY, accessibility = true, fallback = true)
        )
    }

    @Test
    fun `accessibility mode does not silently start polling`() {
        assertEquals(
            ActiveEnforcer.NONE,
            resolve(EnforcementMode.ACCESSIBILITY, accessibility = false, fallback = true)
        )
    }

    // ── FALLBACK: chosen deliberately, so an available accessibility service
    //    must not override it ──

    @Test
    fun `fallback mode polls even when accessibility is available`() {
        assertEquals(
            ActiveEnforcer.FALLBACK,
            resolve(EnforcementMode.FALLBACK, accessibility = true, fallback = true)
        )
    }

    @Test
    fun `fallback mode enforces nothing without its grants`() {
        // Usage access or the overlay permission is missing: polling would detect
        // blocks it cannot act on, so claiming to enforce would be a lie.
        assertEquals(
            ActiveEnforcer.NONE,
            resolve(EnforcementMode.FALLBACK, accessibility = true, fallback = false)
        )
    }

    // ── Stored value handling ──

    @Test
    fun `unknown and missing stored modes read as the default`() {
        assertEquals(EnforcementMode.DEFAULT, EnforcementMode.from(null))
        assertEquals(EnforcementMode.DEFAULT, EnforcementMode.from(""))
        // A mode removed in a future version, or a name mangled by R8, must not
        // leave enforcement in an undefined state.
        assertEquals(EnforcementMode.DEFAULT, EnforcementMode.from("VPN"))
    }

    @Test
    fun `every mode round-trips through its stored name`() {
        EnforcementMode.entries.forEach { mode ->
            assertEquals(mode, EnforcementMode.from(mode.name))
        }
    }

    @Test
    fun `the default keeps blocking alive when accessibility is taken away`() {
        // The whole point of the default: a user who never opens Settings still
        // gets enforcement after Advanced Protection or an OEM kills the service.
        assertEquals(EnforcementMode.AUTO, EnforcementMode.DEFAULT)
        assertEquals(
            ActiveEnforcer.FALLBACK,
            resolve(EnforcementMode.DEFAULT, accessibility = false, fallback = true)
        )
    }
}
