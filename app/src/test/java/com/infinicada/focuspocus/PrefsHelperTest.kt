package com.infinicada.focuspocus

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito

class PrefsHelperTest {

    private val gson = Gson()
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

    data class TestItem(val id: Int, val name: String)

    @Test
    fun `load returns parsed list when json is valid`() {
        val items = listOf(TestItem(1, "A"), TestItem(2, "B"))
        val json = gson.toJson(items)
        prefs.edit().putString("test_key", json).apply()

        val type = object : TypeToken<List<TestItem>>() {}.type
        val result = PrefsHelper.load<List<TestItem>>(prefs, gson, "test_key", type)

        assertEquals(items, result)
    }

    @Test
    fun `load returns null when key is missing`() {
        val type = object : TypeToken<List<TestItem>>() {}.type
        val result = PrefsHelper.load<List<TestItem>>(prefs, gson, "missing_key", type)

        assertNull(result)
    }

    @Test
    fun `load returns null and clears key when json is invalid`() {
        prefs.edit().putString("test_key", "invalid json").apply()

        val type = object : TypeToken<List<TestItem>>() {}.type
        var corruptionCallbackCalled = false
        val result = PrefsHelper.load<List<TestItem>>(prefs, gson, "test_key", type) {
            corruptionCallbackCalled = true
        }

        assertNull(result)
        assertTrue(corruptionCallbackCalled)
        assertFalse(prefs.contains("test_key"))

        // Verify Log.e was called (PrefsHelper logs with the throwable overload)
        mockedLog.verify { Log.e(Mockito.anyString(), Mockito.anyString(), Mockito.any()) }
    }

    @Test
    fun `save stores object as json`() {
        val items = listOf(TestItem(1, "A"))
        PrefsHelper.save(prefs, gson, "save_key", items)

        val json = prefs.getString("save_key", null)
        assertEquals(gson.toJson(items), json)
    }
}
