package com.infinicada.focuspocus

import com.infinicada.focuspocus.handler.NfcTapDebouncer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcTapDebouncerTest {

    @Test
    fun `rejects rapid duplicate tap of same tag within debounce window`() {
        val debouncer = NfcTapDebouncer(windowMillis = 1_500L)
        val t0 = 10_000L

        assertTrue(debouncer.shouldAccept("tag-1", t0))
        assertFalse(debouncer.shouldAccept("tag-1", t0 + 200L))
        assertFalse(debouncer.shouldAccept("tag-1", t0 + 1_499L))
        assertTrue(debouncer.shouldAccept("tag-1", t0 + 1_500L))
    }

    @Test
    fun `accepts a different tag immediately without waiting`() {
        val debouncer = NfcTapDebouncer(windowMillis = 1_500L)
        val t0 = 10_000L

        assertTrue(debouncer.shouldAccept("tag-1", t0))
        assertTrue(debouncer.shouldAccept("tag-2", t0 + 100L))
        assertFalse(debouncer.shouldAccept("tag-2", t0 + 500L))
        assertTrue(debouncer.shouldAccept("tag-1", t0 + 600L))
    }
}
