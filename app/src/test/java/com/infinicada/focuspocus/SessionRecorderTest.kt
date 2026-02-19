package com.infinicada.focuspocus

import android.content.SharedPreferences
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.Calendar

class SessionRecorderTest {

    private val gson = Gson()
    private val fakePrefs = FakeSharedPreferences()

    @Test
    fun testCalculateCurrentStreak_empty() {
        val sessions = emptyList<FocusSession>()
        assertEquals(0, calculateCurrentStreak(sessions))
    }

    @Test
    fun testCalculateCurrentStreak_single() {
        val now = System.currentTimeMillis()
        val sessions = listOf(
            FocusSession(startTimeMillis = now - 60000, endTimeMillis = now, durationMinutes = 1, blockerName = "test", breaksUsed = 0)
        )
        assertEquals(1, calculateCurrentStreak(sessions))
    }

    @Test
    fun testRecord_basic() {
        fakePrefs.putLong(Constants.PrefsKeys.SESSION_START_TIME, System.currentTimeMillis() - 120000) // 2 mins ago
        fakePrefs.putString(Constants.PrefsKeys.ACTIVE_BLOCKER, "TestBlocker")

        val sessions = SessionRecorder.record(fakePrefs, gson)

        assertEquals(1, sessions.size)
        assertEquals(2, sessions[0].durationMinutes)
        assertEquals("TestBlocker", sessions[0].blockerName)

        // SESSION_START_TIME should be removed
        assertEquals(0L, fakePrefs.getLong(Constants.PrefsKeys.SESSION_START_TIME, 0L))

        // FOCUS_SESSIONS should be updated in prefs
        val json = fakePrefs.getString(Constants.PrefsKeys.FOCUS_SESSIONS, "[]")
        assertTrue(json!!.contains("TestBlocker"))
    }
}

class FakeSharedPreferences : SharedPreferences {
    val map = Collections.synchronizedMap(mutableMapOf<String, Any>())
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = map

    override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = map[key] as? MutableSet<String> ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(this)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        listener?.let { listeners.add(it) }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        listeners.remove(listener)
    }

    fun putString(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }

    fun putLong(key: String, value: Long) {
        map[key] = value
    }

    inner class FakeEditor(private val prefs: FakeSharedPreferences) : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private val removals = mutableListOf<String>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            changes[key!!] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            changes[key!!] = values
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            changes[key!!] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            changes[key!!] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            changes[key!!] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            changes[key!!] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            removals.add(key!!)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) prefs.map.clear()
            removals.forEach { prefs.map.remove(it) }
            changes.forEach { (k, v) ->
                if (v == null) prefs.map.remove(k) else prefs.map[k] = v
            }
        }
    }
}
