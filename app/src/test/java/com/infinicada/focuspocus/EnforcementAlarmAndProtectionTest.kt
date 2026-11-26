package com.infinicada.focuspocus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnforcementAlarmAndProtectionTest {

    private val t0 = 1_700_000_000_000L
    private val min = 60_000L

    @Test
    fun `computeNextAlarmMillis picks earliest across sessions, pacts, removals, revisions, and backups`() {
        val prefs = FakeSharedPreferences()
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        prefs.putLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, t0 + 30 * min)

        val next = RitualAlarmScheduler.computeNextAlarmMillis(
            prefs = prefs,
            now = t0,
            schedules = emptyList(),
            activePactExpiries = listOf(t0 + 15 * min, t0 + 45 * min),
            pendingRemovalExpiries = listOf(t0 + 24 * 60 * min),
            pendingWellRevisionMillis = t0 + 12 * 60 * min,
            nextAutoBackupMillis = t0 + 60 * min
        )
        assertEquals(t0 + 15 * min, next)

        // With no active pacts, session end wins
        val nextSession = RitualAlarmScheduler.computeNextAlarmMillis(
            prefs = prefs,
            now = t0,
            schedules = emptyList(),
            activePactExpiries = emptyList(),
            pendingRemovalExpiries = listOf(t0 + 24 * 60 * min),
            pendingWellRevisionMillis = t0 + 12 * 60 * min,
            nextAutoBackupMillis = t0 + 60 * min
        )
        assertEquals(t0 + 30 * min, nextSession)
    }

    @Test
    fun `computeNextAlarmMillis ignores past-due instants and returns null when empty`() {
        val prefs = FakeSharedPreferences()
        val next = RitualAlarmScheduler.computeNextAlarmMillis(
            prefs = prefs,
            now = t0,
            schedules = emptyList(),
            activePactExpiries = listOf(t0 - 1_000L, t0),
            pendingRemovalExpiries = listOf(t0 - 5_000L),
            pendingWellRevisionMillis = t0 - 10_000L,
            nextAutoBackupMillis = null
        )
        assertNull(next)
    }

    @Test
    fun `protection-off alert fires only when accessibility is off and protection is needed`() {
        assertFalse(ProtectionAlertNotifier.shouldAlertProtectionOff(accessibilityEnabled = true, sessionActive = true, wellAppsCount = 3))
        assertFalse(ProtectionAlertNotifier.shouldAlertProtectionOff(accessibilityEnabled = false, sessionActive = false, wellAppsCount = 0))
        assertTrue(ProtectionAlertNotifier.shouldAlertProtectionOff(accessibilityEnabled = false, sessionActive = true, wellAppsCount = 0))
        assertTrue(ProtectionAlertNotifier.shouldAlertProtectionOff(accessibilityEnabled = false, sessionActive = false, wellAppsCount = 1))
    }

    @Test
    fun `EnforcementLog caps at MAX_ENTRIES and returns newest first`() {
        val prefs = FakeSharedPreferences()
        repeat(60) { i ->
            EnforcementLog.record(prefs, EnforcementLog.CAT_ALARM, "Event $i", nowMillis = t0 + i * 1_000L)
        }
        val entries = EnforcementLog.load(prefs)
        assertEquals(EnforcementLog.MAX_ENTRIES, entries.size)
        assertEquals("Event 59", entries.first().message)
        assertEquals("Event 10", entries.last().message)

        EnforcementLog.clear(prefs)
        assertTrue(EnforcementLog.load(prefs).isEmpty())
    }
}
