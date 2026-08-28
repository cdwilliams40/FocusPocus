package com.infinicada.focuspocus

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusWidgetStateTest {

    private val now = 1_700_000_000_000L

    private fun state(
        sessionActive: Boolean = false,
        onBreak: Boolean = false,
        focusEnd: Long = 0L,
        breakEnd: Long = 0L,
        sealed: Int = 0
    ) = FocusWidgetState.of(sessionActive, onBreak, focusEnd, breakEnd, sealed, now)

    @Test
    fun `a running session outranks everything else`() {
        val s = state(sessionActive = true, focusEnd = now + 10 * 60_000L, sealed = 3)
        assertEquals(FocusWidgetState.Kind.FOCUSING, s.kind)
        assertEquals(10, s.minutesRemaining)
    }

    @Test
    fun `a break reports the break's own countdown`() {
        val s = state(
            sessionActive = true,
            onBreak = true,
            focusEnd = now + 90 * 60_000L,
            breakEnd = now + 5 * 60_000L
        )
        assertEquals(FocusWidgetState.Kind.ON_BREAK, s.kind)
        assertEquals(5, s.minutesRemaining)
    }

    /**
     * Standing guards are the app's home screen, so "no session" is not the
     * same as "nothing is happening" — a widget that said so would be lying
     * to someone with three apps sealed.
     */
    @Test
    fun `sealed apps are reported when no session is running`() {
        val s = state(sealed = 3)
        assertEquals(FocusWidgetState.Kind.SEALED, s.kind)
        assertEquals(3, s.sealedCount)
    }

    @Test
    fun `nothing running and nothing sealed is idle`() {
        assertEquals(FocusWidgetState.Kind.IDLE, state().kind)
        assertEquals(FocusWidgetState.Kind.IDLE, state(sealed = 0).kind)
    }

    @Test
    fun `an untimed session reports no minutes rather than zero left`() {
        val s = state(sessionActive = true, focusEnd = 0L)
        assertEquals(FocusWidgetState.Kind.FOCUSING, s.kind)
        assertEquals(0, s.minutesRemaining)
    }

    @Test
    fun `the countdown rounds up so the last partial minute still shows`() {
        assertEquals(1, FocusWidgetState.minutesUntil(now + 1_000L, now))
        assertEquals(1, FocusWidgetState.minutesUntil(now + 60_000L, now))
        assertEquals(2, FocusWidgetState.minutesUntil(now + 60_001L, now))
        assertEquals(0, FocusWidgetState.minutesUntil(now, now))
        assertEquals(0, FocusWidgetState.minutesUntil(now - 5_000L, now))
        assertEquals(0, FocusWidgetState.minutesUntil(0L, now))
    }
}
