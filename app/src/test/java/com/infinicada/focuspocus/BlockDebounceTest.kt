package com.infinicada.focuspocus

import com.infinicada.focuspocus.limit.BlockDebounce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockDebounceTest {

    private val blockedAt = 1_000_000L
    private val window = BlockDebounce.WINDOW_MS

    // ── isRecent: the debounce itself ──

    @Test
    fun `a block is recent until its window has passed`() {
        assertTrue(BlockDebounce.isRecent(blockedAt, blockedAt))
        assertTrue(BlockDebounce.isRecent(blockedAt, blockedAt + window - 1))
        assertFalse(BlockDebounce.isRecent(blockedAt, blockedAt + window))
        assertFalse(BlockDebounce.isRecent(blockedAt, blockedAt + 60_000))
    }

    @Test
    fun `an app that was never blocked has no recent block`() {
        assertFalse(BlockDebounce.isRecent(null, blockedAt))
    }

    // ── isEcho: is this event the blocked app coming back? ──

    @Test
    fun `an event from a just-blocked app behind the block screen is an echo`() {
        // Seen on a device: Chrome's last window event arrives about a second
        // after it was closed, with the block screen already in front.
        assertTrue(BlockDebounce.isEcho(blockedAt, blockedAt + 1_100, blockScreenOnTop = true))
    }

    @Test
    fun `a quick return to the app is not an echo once the block screen is gone`() {
        // The event is still debounced, but it must count as the app being in
        // front so the next timed check covers it.
        assertFalse(BlockDebounce.isEcho(blockedAt, blockedAt + 1_100, blockScreenOnTop = false))
    }

    @Test
    fun `after the window an event counts even with the block screen in front`() {
        assertFalse(BlockDebounce.isEcho(blockedAt, blockedAt + window, blockScreenOnTop = true))
    }

    @Test
    fun `an app that was never blocked cannot echo`() {
        assertFalse(BlockDebounce.isEcho(null, blockedAt, blockScreenOnTop = true))
    }

    // ── remainingMillis: when to judge an app that came back inside the window ──

    @Test
    fun `the rest of the window is what remains`() {
        assertEquals(window, BlockDebounce.remainingMillis(blockedAt, blockedAt))
        assertEquals(window - 1_050, BlockDebounce.remainingMillis(blockedAt, blockedAt + 1_050))
        assertEquals(1, BlockDebounce.remainingMillis(blockedAt, blockedAt + window - 1))
    }

    @Test
    fun `nothing remains once the window has passed or without a block`() {
        assertEquals(0, BlockDebounce.remainingMillis(blockedAt, blockedAt + window))
        assertEquals(0, BlockDebounce.remainingMillis(blockedAt, blockedAt + 60_000))
        assertEquals(0, BlockDebounce.remainingMillis(null, blockedAt))
    }

    @Test
    fun `a clock set back cannot stretch the wait past one window`() {
        assertEquals(window, BlockDebounce.remainingMillis(blockedAt, blockedAt - 3_600_000))
    }

    // ── isStillInFront: is the app that came back really there? ──

    private val app = "com.example.feed"
    private val launcher = "com.example.launcher"

    @Test
    fun `an app both records agree on is in front`() {
        assertTrue(BlockDebounce.isStillInFront(app, trackedForeground = app, lastResumedPackage = app))
    }

    @Test
    fun `an app the window events no longer track is not in front`() {
        // The block screen's own window event, or the launcher's, cleared it.
        assertFalse(BlockDebounce.isStillInFront(app, trackedForeground = null, lastResumedPackage = app))
        assertFalse(BlockDebounce.isStillInFront(app, trackedForeground = launcher, lastResumedPackage = app))
    }

    @Test
    fun `a late event after the user went home does not put the app in front`() {
        // Tracked because the block screen was already gone, but the launcher
        // was the last thing the system resumed.
        assertFalse(BlockDebounce.isStillInFront(app, trackedForeground = app, lastResumedPackage = launcher))
    }

    @Test
    fun `without a usage record the window events decide`() {
        assertTrue(BlockDebounce.isStillInFront(app, trackedForeground = app, lastResumedPackage = null))
        assertFalse(BlockDebounce.isStillInFront(app, trackedForeground = null, lastResumedPackage = null))
    }
}
