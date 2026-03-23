package com.infinicada.focuspocus

import com.google.gson.Gson
import com.infinicada.focuspocus.data.ConditionalUnlockRepository
import com.infinicada.focuspocus.model.ConditionalUnlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConditionalUnlockRepositoryTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var repo: ConditionalUnlockRepository
    private val gson = Gson()

    private fun makeUnlock(
        id: String = "unlock-1",
        name: String = "Test"
    ) = ConditionalUnlock(
        id = id,
        name = name,
        requiredAppPackage = "com.test.app",
        requiredMinutes = 10,
        unlockedBlockerNames = setOf("Blocker1")
    )

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        repo = ConditionalUnlockRepository(prefs, gson)
    }

    @Test
    fun `getConditionalUnlocks returns empty when nothing stored`() {
        assertEquals(emptyList<ConditionalUnlock>(), repo.getConditionalUnlocks())
    }

    @Test
    fun `saveConditionalUnlock adds new rule`() {
        val rule = makeUnlock()
        val result = repo.saveConditionalUnlock(rule, emptyList())

        assertTrue(result)
        assertEquals(1, repo.getConditionalUnlocks().size)
    }

    @Test
    fun `saveConditionalUnlock updates existing rule by id`() {
        val original = makeUnlock(name = "Original")
        repo.saveConditionalUnlock(original, emptyList())

        val updated = makeUnlock(name = "Updated")
        val result = repo.saveConditionalUnlock(updated, repo.getConditionalUnlocks())

        assertTrue(result)
        assertEquals(1, repo.getConditionalUnlocks().size)
        assertEquals("Updated", repo.getConditionalUnlocks()[0].name)
    }

    @Test
    fun `saveConditionalUnlock rejects new rule at capacity`() {
        val fullList = (1..Constants.MAX_CONDITIONAL_UNLOCKS).map { makeUnlock(id = "unlock-$it") }
        val newRule = makeUnlock(id = "unlock-new")

        val result = repo.saveConditionalUnlock(newRule, fullList)

        assertFalse(result)
    }

    @Test
    fun `deleteConditionalUnlock removes rule by id`() {
        val rule = makeUnlock()
        val remaining = repo.deleteConditionalUnlock(rule, listOf(rule))

        assertEquals(0, remaining.size)
    }
}
