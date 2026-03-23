package com.infinicada.focuspocus

import com.infinicada.focuspocus.model.ConditionalUnlock
import com.infinicada.focuspocus.model.DayOfWeek
import com.infinicada.focuspocus.model.FocusPreset
import com.infinicada.focuspocus.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelEffectiveBlockerNamesTest {

    // ── FocusPreset.effectiveBlockerNames ──

    @Test
    fun `FocusPreset effectiveBlockerNames returns blockerNames when non-empty`() {
        val preset = FocusPreset(
            name = "Test",
            blockerName = "Legacy",
            blockerNames = listOf("A", "B"),
            durationMinutes = 25,
            breaksEnabled = true
        )
        assertEquals(listOf("A", "B"), preset.effectiveBlockerNames)
    }

    @Test
    fun `FocusPreset effectiveBlockerNames falls back to blockerName when blockerNames empty`() {
        val preset = FocusPreset(
            name = "Test",
            blockerName = "Legacy",
            blockerNames = emptyList(),
            durationMinutes = 25,
            breaksEnabled = true
        )
        assertEquals(listOf("Legacy"), preset.effectiveBlockerNames)
    }

    @Test
    fun `FocusPreset effectiveBlockerNames returns empty when both empty`() {
        val preset = FocusPreset(
            name = "Test",
            blockerName = "",
            blockerNames = emptyList(),
            durationMinutes = 25,
            breaksEnabled = true
        )
        assertEquals(emptyList<String>(), preset.effectiveBlockerNames)
    }

    // ── Schedule.effectiveBlockerNames ──

    @Test
    fun `Schedule effectiveBlockerNames returns blockerNames when non-empty`() {
        val schedule = Schedule(
            name = "Test",
            blockerName = "Legacy",
            blockerNames = listOf("A", "B"),
            days = setOf(DayOfWeek.MONDAY),
            startTime = "09:00",
            endTime = "17:00"
        )
        assertEquals(listOf("A", "B"), schedule.effectiveBlockerNames)
    }

    @Test
    fun `Schedule effectiveBlockerNames falls back to blockerName when blockerNames empty`() {
        val schedule = Schedule(
            name = "Test",
            blockerName = "Legacy",
            blockerNames = emptyList(),
            days = setOf(DayOfWeek.MONDAY),
            startTime = "09:00",
            endTime = "17:00"
        )
        assertEquals(listOf("Legacy"), schedule.effectiveBlockerNames)
    }

    @Test
    fun `Schedule effectiveBlockerNames returns empty when both empty`() {
        val schedule = Schedule(
            name = "Test",
            blockerName = "",
            blockerNames = emptyList(),
            days = setOf(DayOfWeek.MONDAY),
            startTime = "09:00",
            endTime = "17:00"
        )
        assertEquals(emptyList<String>(), schedule.effectiveBlockerNames)
    }

    // ── ConditionalUnlock.effectiveUnlockedBlockerNames ──

    @Test
    fun `ConditionalUnlock effectiveUnlockedBlockerNames returns set when non-empty`() {
        val unlock = ConditionalUnlock(
            name = "Test",
            requiredAppPackage = "com.test",
            requiredMinutes = 10,
            unlockedBlockerNames = setOf("A", "B")
        )
        assertEquals(setOf("A", "B"), unlock.effectiveUnlockedBlockerNames)
    }

    @Test
    fun `ConditionalUnlock effectiveUnlockedBlockerNames returns empty set when empty`() {
        val unlock = ConditionalUnlock(
            name = "Test",
            requiredAppPackage = "com.test",
            requiredMinutes = 10,
            unlockedBlockerNames = emptySet()
        )
        assertEquals(emptySet<String>(), unlock.effectiveUnlockedBlockerNames)
    }
}
