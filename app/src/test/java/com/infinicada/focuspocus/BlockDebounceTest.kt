package com.infinicada.focuspocus

import com.infinicada.focuspocus.limit.BlockDebounce
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
}
