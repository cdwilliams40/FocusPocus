package com.infinicada.focuspocus

import org.junit.Assert.assertEquals
import org.junit.Test

class ConstantsTest {
    @Test
    fun defaultFocusPresets_areCorrect() {
        assertEquals("Deep Work", Constants.Defaults.FocusPresets.DEEP_WORK_NAME)
        assertEquals(240, Constants.Defaults.FocusPresets.DEEP_WORK_DURATION)
        assertEquals(true, Constants.Defaults.FocusPresets.DEEP_WORK_BREAKS)

        assertEquals("Quick Focus", Constants.Defaults.FocusPresets.QUICK_FOCUS_NAME)
        assertEquals(25, Constants.Defaults.FocusPresets.QUICK_FOCUS_DURATION)
        assertEquals(true, Constants.Defaults.FocusPresets.QUICK_FOCUS_BREAKS)

        assertEquals("Sleep Mode", Constants.Defaults.FocusPresets.SLEEP_MODE_NAME)
        assertEquals(480, Constants.Defaults.FocusPresets.SLEEP_MODE_DURATION)
        assertEquals(false, Constants.Defaults.FocusPresets.SLEEP_MODE_BREAKS)

        assertEquals("Default", Constants.Defaults.FocusPresets.DEFAULT_BLOCKER_NAME)
    }
}
