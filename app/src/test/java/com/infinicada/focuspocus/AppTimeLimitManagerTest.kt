package com.infinicada.focuspocus

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTimeLimitManagerTest {

    private val gson = Gson()
    private val prefs = FakeSharedPreferences()

    @Test
    fun getTimeLimits_returnsEmptyMap_whenPrefsEmpty() {
        val limits = AppTimeLimitManager.getTimeLimits(prefs, gson)
        assertTrue(limits.isEmpty())
    }

    @Test
    fun getTimeLimits_returnsCorrectMap_whenJsonValid() {
        val json = """{"com.example.app": 30, "com.another.app": 60}"""
        prefs.edit().putString(Constants.PrefsKeys.APP_TIME_LIMITS, json).apply()

        val limits = AppTimeLimitManager.getTimeLimits(prefs, gson)
        assertEquals(2, limits.size)
        assertEquals(30, limits["com.example.app"])
        assertEquals(60, limits["com.another.app"])
    }

    @Test
    fun getTimeLimits_returnsEmptyMap_whenJsonInvalid() {
        val json = """{"com.example.app": 30, "invalid_json"""
        prefs.edit().putString(Constants.PrefsKeys.APP_TIME_LIMITS, json).apply()

        val limits = AppTimeLimitManager.getTimeLimits(prefs, gson)
        assertTrue(limits.isEmpty())
    }

    @Test
    fun getTimeLimits_returnsEmptyMap_whenJsonTypeMismatch() {
        val json = """["item1", "item2"]""" // Not a Map<String, Int>
        prefs.edit().putString(Constants.PrefsKeys.APP_TIME_LIMITS, json).apply()

        // Gson might successfully parse a list as a map or throw an exception depending on the type token.
        // If it throws, the catch block should return empty map.
        // If it returns something else, we should assert it's empty or handle it.
        // Given TypeToken<Map<String, Int>>, Gson usually throws JsonSyntaxException for a JSON array.
        val limits = AppTimeLimitManager.getTimeLimits(prefs, gson)
        assertTrue(limits.isEmpty())
    }

    @Test
    fun getTimeLimits_returnsEmptyMap_whenJsonValueTypeMismatch() {
        val json = """{"com.example.app": "not_an_int"}"""
        prefs.edit().putString(Constants.PrefsKeys.APP_TIME_LIMITS, json).apply()

        // Gson might throw JsonSyntaxException because "not_an_int" is not an int.
        val limits = AppTimeLimitManager.getTimeLimits(prefs, gson)
        assertTrue(limits.isEmpty())
    }

    @Test
    fun saveTimeLimits_savesCorrectly() {
        val limits = mapOf("com.example.app" to 30, "com.another.app" to 60)
        AppTimeLimitManager.saveTimeLimits(prefs, gson, limits)

        val json = prefs.getString(Constants.PrefsKeys.APP_TIME_LIMITS, null)
        val savedLimits = AppTimeLimitManager.getTimeLimits(prefs, gson)

        assertEquals(limits, savedLimits)

        // Also verify the JSON string directly if needed, but round trip is usually sufficient.
        // We can check if the string contains the expected keys.
        assertNotNull("JSON string should not be null", json)
        assertTrue(json!!.contains("com.example.app"))
        assertTrue(json.contains("30"))
    }
}
