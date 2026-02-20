package com.infinicada.focuspocus

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TimeLimitCheckerTest {

    private lateinit var context: Context
    private lateinit var checkFunction: (Context, String, Int) -> Boolean
    private lateinit var clock: () -> Long
    private var currentTime = 0L

    @Before
    fun setUp() {
        context = mock()
        checkFunction = mock()
        currentTime = 1000L
        clock = { currentTime }
    }

    @Test
    fun `shouldBlock returns false when under limit`() {
        whenever(checkFunction.invoke(any(), eq("com.example.app"), eq(30))).thenReturn(false)
        val checker = TimeLimitChecker(context, checkFunction, clock)

        val result = checker.shouldBlock("com.example.app", 30)

        assertFalse(result)
        verify(checkFunction, times(1)).invoke(any(), eq("com.example.app"), eq(30))
    }

    @Test
    fun `shouldBlock returns true when over limit`() {
        whenever(checkFunction.invoke(any(), eq("com.example.app"), eq(30))).thenReturn(true)
        val checker = TimeLimitChecker(context, checkFunction, clock)

        val result = checker.shouldBlock("com.example.app", 30)

        assertTrue(result)
        verify(checkFunction, times(1)).invoke(any(), eq("com.example.app"), eq(30))
    }

    @Test
    fun `shouldBlock caches result for 60 seconds`() {
        // First call returns false (under limit)
        whenever(checkFunction.invoke(any(), eq("com.example.app"), eq(30))).thenReturn(false)
        val checker = TimeLimitChecker(context, checkFunction, clock)

        // Call 1: Should call checkFunction
        checker.shouldBlock("com.example.app", 30)
        verify(checkFunction, times(1)).invoke(any(), eq("com.example.app"), eq(30))

        // Call 2: 10 seconds later. Should use cache.
        currentTime += 10_000
        val result2 = checker.shouldBlock("com.example.app", 30)
        assertFalse(result2)
        // Verify checkFunction was NOT called again
        verify(checkFunction, times(1)).invoke(any(), eq("com.example.app"), eq(30))
    }

    @Test
    fun `shouldBlock refreshes after 60 seconds`() {
        // First call returns false
        whenever(checkFunction.invoke(any(), eq("com.example.app"), eq(30))).thenReturn(false)
        val checker = TimeLimitChecker(context, checkFunction, clock)

        // Call 1
        checker.shouldBlock("com.example.app", 30)
        verify(checkFunction, times(1)).invoke(any(), eq("com.example.app"), eq(30))

        // Advance time by 61 seconds (cache expired)
        currentTime += 61_000

        // Second call should trigger refresh
        // Let's change the result to true to verify it uses the new value
        whenever(checkFunction.invoke(any(), eq("com.example.app"), eq(30))).thenReturn(true)

        val result2 = checker.shouldBlock("com.example.app", 30)
        assertTrue(result2)
        // Verify checkFunction was called TWICE in total
        verify(checkFunction, times(2)).invoke(any(), eq("com.example.app"), eq(30))
    }

    @Test
    fun `shouldBlock caches separate results for different packages`() {
        whenever(checkFunction.invoke(any(), eq("com.example.app1"), eq(30))).thenReturn(false)
        whenever(checkFunction.invoke(any(), eq("com.example.app2"), eq(30))).thenReturn(true)
        val checker = TimeLimitChecker(context, checkFunction, clock)

        // Check app1
        assertFalse(checker.shouldBlock("com.example.app1", 30))
        // Check app2
        assertTrue(checker.shouldBlock("com.example.app2", 30))

        verify(checkFunction, times(1)).invoke(any(), eq("com.example.app1"), eq(30))
        verify(checkFunction, times(1)).invoke(any(), eq("com.example.app2"), eq(30))
    }

    @Test
    fun `benchmark cached vs uncached`() {
        // This test demonstrates the performance improvement
        whenever(checkFunction.invoke(any(), any(), any())).thenReturn(false)
        val checker = TimeLimitChecker(context, checkFunction, clock)

        // Simulate 100 checks within 1 second
        for (i in 1..100) {
            checker.shouldBlock("com.example.app", 30)
            currentTime += 10 // 10ms increments
        }

        // Should only call checkFunction once
        verify(checkFunction, times(1)).invoke(any(), eq("com.example.app"), eq(30))
    }
}
