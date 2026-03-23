package com.infinicada.focuspocus

import com.google.gson.Gson
import com.infinicada.focuspocus.data.ScheduleRepository
import com.infinicada.focuspocus.model.DayOfWeek
import com.infinicada.focuspocus.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScheduleRepositoryTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var repo: ScheduleRepository
    private val gson = Gson()

    private fun makeSchedule(
        id: String = "sched-1",
        name: String = "Test",
        blockerNames: List<String> = listOf("Blocker1"),
        unbindingTalismanId: String? = null
    ) = Schedule(
        id = id,
        name = name,
        blockerNames = blockerNames,
        days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
        startTime = "09:00",
        endTime = "17:00",
        unbindingTalismanId = unbindingTalismanId
    )

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        repo = ScheduleRepository(prefs, gson)
    }

    @Test
    fun `getSchedules returns empty list when nothing stored`() {
        assertEquals(emptyList<Schedule>(), repo.getSchedules())
    }

    @Test
    fun `saveSchedule adds new schedule`() {
        val schedule = makeSchedule()
        val result = repo.saveSchedule(schedule, emptyList())

        assertTrue(result)
        assertEquals(1, repo.getSchedules().size)
        assertEquals("sched-1", repo.getSchedules()[0].id)
    }

    @Test
    fun `saveSchedule updates existing schedule by id`() {
        val original = makeSchedule(name = "Original")
        repo.saveSchedule(original, emptyList())
        val updated = makeSchedule(name = "Updated")

        val result = repo.saveSchedule(updated, repo.getSchedules())

        assertTrue(result)
        assertEquals(1, repo.getSchedules().size)
        assertEquals("Updated", repo.getSchedules()[0].name)
    }

    @Test
    fun `saveSchedule rejects new schedule at capacity`() {
        val fullList = (1..Constants.MAX_SCHEDULES).map { makeSchedule(id = "sched-$it") }
        val newSchedule = makeSchedule(id = "sched-new")

        val result = repo.saveSchedule(newSchedule, fullList)

        assertFalse(result)
    }

    @Test
    fun `saveSchedule allows update at capacity`() {
        val fullList = (1..Constants.MAX_SCHEDULES).map { makeSchedule(id = "sched-$it") }
        val updateExisting = makeSchedule(id = "sched-1", name = "Updated")

        val result = repo.saveSchedule(updateExisting, fullList)

        assertTrue(result)
    }

    @Test
    fun `deleteSchedule removes by id`() {
        val schedule = makeSchedule()
        repo.saveSchedule(schedule, emptyList())

        val remaining = repo.deleteSchedule(schedule, repo.getSchedules())

        assertEquals(0, remaining.size)
        assertEquals(0, repo.getSchedules().size)
    }

    @Test
    fun `cleanupOrphanedSchedules removes schedules with no valid blockers`() {
        val orphan = makeSchedule(id = "orphan", blockerNames = listOf("Deleted"))
        val valid = makeSchedule(id = "valid", blockerNames = listOf("Blocker1"))

        val cleaned = repo.cleanupOrphanedSchedules(
            listOf(orphan, valid),
            blockerNames = setOf("Blocker1"),
            talismanIds = emptySet()
        )

        assertEquals(1, cleaned.size)
        assertEquals("valid", cleaned[0].id)
    }

    @Test
    fun `cleanupOrphanedSchedules clears orphaned talismanId`() {
        val schedule = makeSchedule(unbindingTalismanId = "deleted-talisman")

        val cleaned = repo.cleanupOrphanedSchedules(
            listOf(schedule),
            blockerNames = setOf("Blocker1"),
            talismanIds = setOf("other-talisman")
        )

        assertEquals(1, cleaned.size)
        assertNull(cleaned[0].unbindingTalismanId)
    }

    @Test
    fun `cleanupOrphanedSchedules preserves valid schedules unchanged`() {
        val schedule = makeSchedule(unbindingTalismanId = "my-talisman")

        val cleaned = repo.cleanupOrphanedSchedules(
            listOf(schedule),
            blockerNames = setOf("Blocker1"),
            talismanIds = setOf("my-talisman")
        )

        assertEquals(1, cleaned.size)
        assertEquals("my-talisman", cleaned[0].unbindingTalismanId)
    }
}
