package com.infinicada.focuspocus

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito

class PrefsHelperTest {

    private val prefs = FakeSharedPreferences()
    private lateinit var mockedLog: MockedStatic<Log>

    @Before
    fun setup() {
        mockedLog = Mockito.mockStatic(Log::class.java)
    }

    @After
    fun tearDown() {
        mockedLog.close()
    }

    @Serializable
    data class TestItem(val id: Int, val name: String)

    @Test
    fun `load returns parsed list when json is valid`() {
        val items = listOf(TestItem(1, "A"), TestItem(2, "B"))
        val json = AppJson.json.encodeToString(items)
        prefs.edit().putString("test_key", json).apply()

        val result = PrefsHelper.load<List<TestItem>>(prefs, "test_key")

        assertEquals(items, result)
    }

    @Test
    fun `load returns null when key is missing`() {
        val result = PrefsHelper.load<List<TestItem>>(prefs, "missing_key")
        assertNull(result)
    }

    @Test
    fun `load returns null and clears key when json is invalid`() {
        prefs.edit().putString("test_key", "invalid json").apply()

        var corruptionCallbackCalled = false
        val result = PrefsHelper.load<List<TestItem>>(prefs, "test_key") {
            corruptionCallbackCalled = true
        }

        assertNull(result)
        assertTrue(corruptionCallbackCalled)
        assertFalse(prefs.contains("test_key"))
        assertEquals(
            "invalid json",
            prefs.getString("test_key" + PrefsHelper.CORRUPT_BACKUP_SUFFIX, null)
        )
        mockedLog.verify { Log.e(Mockito.anyString(), Mockito.anyString(), Mockito.any()) }
    }

    @Test
    fun `save stores object as json`() {
        val items = listOf(TestItem(1, "A"))
        PrefsHelper.save(prefs, "save_key", items)

        val json = prefs.getString("save_key", null)
        assertEquals(AppJson.json.encodeToString(items), json)
    }
}
