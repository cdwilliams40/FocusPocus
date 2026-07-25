package com.infinicada.focuspocus

import android.app.usage.UsageEvents
import com.infinicada.focuspocus.enforcement.resolveForegroundPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The fold that turns a window of usage events into "which app is in front".
 * This is the whole of the fallback's detection accuracy, and the one part of it
 * that can be exercised without a device.
 */
class ForegroundAppMonitorTest {

    private fun resumed(pkg: String, at: Long) =
        ForegroundEvent(pkg, UsageEvents.Event.ACTIVITY_RESUMED, at)

    private fun paused(pkg: String, at: Long) =
        ForegroundEvent(pkg, UsageEvents.Event.ACTIVITY_PAUSED, at)

    private fun stopped(pkg: String, at: Long) =
        ForegroundEvent(pkg, UsageEvents.Event.ACTIVITY_STOPPED, at)

    @Test
    fun `empty window keeps the seed`() {
        assertEquals("com.app.a", resolveForegroundPackage(emptyList(), seed = "com.app.a"))
    }

    @Test
    fun `single resume wins`() {
        assertEquals("com.app.a", resolveForegroundPackage(listOf(resumed("com.app.a", 100))))
    }

    @Test
    fun `newest resume wins over an older one`() {
        val events = listOf(resumed("com.app.a", 100), resumed("com.app.b", 200))
        assertEquals("com.app.b", resolveForegroundPackage(events))
    }

    @Test
    fun `pause of the current app clears the answer`() {
        val events = listOf(resumed("com.app.a", 100), paused("com.app.a", 200))
        assertNull(resolveForegroundPackage(events))
    }

    @Test
    fun `pause of the seeded app clears the answer`() {
        // The app was already foreground when polling began, then the user left it
        // for something we can't see (a launcher that emits no resume of its own).
        val events = listOf(paused("com.app.a", 200))
        assertNull(resolveForegroundPackage(events, seed = "com.app.a"))
    }

    @Test
    fun `pause of a different app leaves the current one alone`() {
        // Switching from A to B produces B's resume and A's pause, and the system
        // does not guarantee which order they arrive in.
        val events = listOf(resumed("com.app.b", 200), paused("com.app.a", 201))
        assertEquals("com.app.b", resolveForegroundPackage(events, seed = "com.app.a"))
    }

    @Test
    fun `late stop for an app already left does not wipe a newer resume`() {
        // The regression this guards: an ACTIVITY_STOPPED can be published well
        // after the app lost focus. Treating it as "nothing is foreground" would
        // make the engine stop enforcing on the app the user is actually in.
        val events = listOf(
            resumed("com.app.a", 100),
            resumed("com.app.b", 200),
            stopped("com.app.a", 300)
        )
        assertEquals("com.app.b", resolveForegroundPackage(events))
    }

    @Test
    fun `reopening an app after leaving it is detected`() {
        val events = listOf(
            resumed("com.app.a", 100),
            paused("com.app.a", 200),
            resumed("com.app.a", 300)
        )
        assertEquals("com.app.a", resolveForegroundPackage(events))
    }

    @Test
    fun `back to back resumes of the same app collapse`() {
        // Multi-activity apps resume several activities in a row on one open.
        val events = listOf(resumed("com.app.a", 100), resumed("com.app.a", 110))
        assertEquals("com.app.a", resolveForegroundPackage(events))
    }

    @Test
    fun `unrelated event types are ignored`() {
        val events = listOf(
            resumed("com.app.a", 100),
            ForegroundEvent("com.app.b", UsageEvents.Event.DEVICE_SHUTDOWN, 200)
        )
        assertEquals("com.app.a", resolveForegroundPackage(events))
    }
}
