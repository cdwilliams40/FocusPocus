package com.infinicada.focuspocus

import com.google.gson.Gson
import com.infinicada.focuspocus.data.PresetRepository
import com.infinicada.focuspocus.model.FocusPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PresetRepositoryTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var repo: PresetRepository
    private val gson = Gson()

    private fun makePreset(
        id: String = "preset-1",
        name: String = "Test",
        talismanId: String? = null
    ) = FocusPreset(
        id = id,
        name = name,
        blockerNames = listOf("Blocker1"),
        durationMinutes = 25,
        breaksEnabled = true,
        talismanId = talismanId
    )

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        repo = PresetRepository(prefs, gson)
    }

    @Test
    fun `getPresets returns defaults when nothing stored`() {
        val presets = repo.getPresets()
        assertEquals(3, presets.size)
    }

    @Test
    fun `savePreset adds new preset`() {
        val preset = makePreset()
        val result = repo.savePreset(preset, emptyList())

        assertTrue(result)
    }

    @Test
    fun `savePreset updates existing preset by id`() {
        val original = makePreset(name = "Original")
        repo.savePreset(original, emptyList())

        val stored = repo.getPresets()
        val updated = makePreset(name = "Updated")
        val result = repo.savePreset(updated, stored)

        assertTrue(result)
    }

    @Test
    fun `savePreset rejects new preset at capacity`() {
        val fullList = (1..Constants.MAX_PRESETS).map { makePreset(id = "preset-$it") }
        val newPreset = makePreset(id = "preset-new")

        val result = repo.savePreset(newPreset, fullList)

        assertFalse(result)
    }

    @Test
    fun `deletePreset removes by id`() {
        val preset = makePreset()
        val remaining = repo.deletePreset(preset, listOf(preset))

        assertEquals(0, remaining.size)
    }

    @Test
    fun `cleanupOrphanedPresets clears orphaned talismanId`() {
        val preset = makePreset(talismanId = "deleted-talisman")

        val cleaned = repo.cleanupOrphanedPresets(
            listOf(preset),
            talismanIds = setOf("other-talisman")
        )

        assertEquals(1, cleaned.size)
        assertNull(cleaned[0].talismanId)
    }

    @Test
    fun `cleanupOrphanedPresets preserves valid talismanId`() {
        val preset = makePreset(talismanId = "my-talisman")

        val cleaned = repo.cleanupOrphanedPresets(
            listOf(preset),
            talismanIds = setOf("my-talisman")
        )

        assertEquals(1, cleaned.size)
        assertEquals("my-talisman", cleaned[0].talismanId)
    }
}
