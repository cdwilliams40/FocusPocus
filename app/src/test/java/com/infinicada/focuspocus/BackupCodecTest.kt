package com.infinicada.focuspocus

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupCodecTest {

    private lateinit var prefs: FakeSharedPreferences
    private val gson = Gson()
    private val t0 = 1_000_000_000_000L

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
    }

    private fun seedTypicalData() {
        prefs.putString(Constants.PrefsKeys.BLOCKER_LISTS, """[{"name":"Social","mode":"BLACKLIST"}]""")
        prefs.putString(Constants.PrefsKeys.THEME_MODE, "DARK")
        prefs.putInt(Constants.PrefsKeys.BREAK_DURATION_MINUTES, 7)
        prefs.putLong(Constants.PrefsKeys.MANA_BALANCE, 420L)
        prefs.putBoolean(Constants.PrefsKeys.HIDE_STOP_BUTTON, true)
        prefs.putInt(Constants.PrefsKeys.LONGEST_STREAK, 12)
    }

    @Test
    fun `export and import round-trip restores every exported key with its type`() {
        seedTypicalData()
        val json = BackupCodec.export(prefs, gson, appVersionCode = 30, now = t0)

        val fresh = FakeSharedPreferences()
        val result = BackupCodec.import(fresh, gson, json)

        assertTrue(result is BackupCodec.ImportResult.Success)
        assertEquals(6, (result as BackupCodec.ImportResult.Success).restoredKeys)
        assertEquals(
            """[{"name":"Social","mode":"BLACKLIST"}]""",
            fresh.getString(Constants.PrefsKeys.BLOCKER_LISTS, null)
        )
        assertEquals("DARK", fresh.getString(Constants.PrefsKeys.THEME_MODE, null))
        assertEquals(7, fresh.getInt(Constants.PrefsKeys.BREAK_DURATION_MINUTES, -1))
        assertEquals(420L, fresh.getLong(Constants.PrefsKeys.MANA_BALANCE, -1L))
        assertTrue(fresh.getBoolean(Constants.PrefsKeys.HIDE_STOP_BUTTON, false))
        assertEquals(12, fresh.getInt(Constants.PrefsKeys.LONGEST_STREAK, -1))
    }

    @Test
    fun `import replaces existing exported keys but leaves live state alone`() {
        seedTypicalData()
        val json = BackupCodec.export(prefs, gson, appVersionCode = 30, now = t0)

        val target = FakeSharedPreferences()
        // Pre-existing config that the backup does not contain must be removed…
        target.putString(Constants.PrefsKeys.SCHEDULES, """[{"name":"Old ritual"}]""")
        // …while live enforcement state must survive untouched.
        target.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        target.putString(Constants.PrefsKeys.APP_COOLDOWN_STATES, """{"com.x":{}}""")
        target.putString(Constants.PrefsKeys.PACT_ALLOWANCES, """{"com.x":123}""")

        BackupCodec.import(target, gson, json)

        assertFalse(target.contains(Constants.PrefsKeys.SCHEDULES))
        assertTrue(target.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false))
        assertEquals("""{"com.x":{}}""", target.getString(Constants.PrefsKeys.APP_COOLDOWN_STATES, null))
        assertEquals("""{"com.x":123}""", target.getString(Constants.PrefsKeys.PACT_ALLOWANCES, null))
    }

    @Test
    fun `live enforcement state never travels in an export`() {
        seedTypicalData()
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        prefs.putString(Constants.PrefsKeys.APP_COOLDOWN_STATES, """{"com.x":{}}""")
        prefs.putString(Constants.PrefsKeys.PACT_ALLOWANCES, """{"com.x":123}""")
        prefs.putString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, "some-id")

        val json = BackupCodec.export(prefs, gson, appVersionCode = 30, now = t0)

        assertFalse(json.contains(Constants.PrefsKeys.MANUAL_FOCUS_MODE))
        assertFalse(json.contains(Constants.PrefsKeys.APP_COOLDOWN_STATES))
        assertFalse(json.contains(Constants.PrefsKeys.PACT_ALLOWANCES))
        assertFalse(json.contains(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID))
    }

    @Test
    fun `garbage and wrong-format files are rejected without touching prefs`() {
        prefs.putString(Constants.PrefsKeys.THEME_MODE, "DARK")

        assertEquals(BackupCodec.ImportResult.InvalidFormat, BackupCodec.import(prefs, gson, "not json"))
        assertEquals(BackupCodec.ImportResult.InvalidFormat, BackupCodec.import(prefs, gson, "{}"))
        assertEquals(
            BackupCodec.ImportResult.InvalidFormat,
            BackupCodec.import(prefs, gson, """{"format":"something-else","formatVersion":1,"prefs":{}}""")
        )
        // Rejections must not have wiped anything.
        assertEquals("DARK", prefs.getString(Constants.PrefsKeys.THEME_MODE, null))
    }

    @Test
    fun `a newer format version is refused`() {
        val json = """{"format":"${BackupCodec.FORMAT}","formatVersion":${BackupCodec.FORMAT_VERSION + 1},"prefs":{}}"""
        assertEquals(BackupCodec.ImportResult.UnsupportedVersion, BackupCodec.import(prefs, gson, json))
    }

    @Test
    fun `unknown keys and malformed entries in a backup are skipped`() {
        val json = """
            {"format":"${BackupCodec.FORMAT}","formatVersion":1,"prefs":{
                "someFutureKey":{"type":"string","value":"x"},
                "${Constants.PrefsKeys.MANUAL_FOCUS_MODE}":{"type":"boolean","value":"true"},
                "${Constants.PrefsKeys.BREAK_DURATION_MINUTES}":{"type":"int","value":"banana"},
                "${Constants.PrefsKeys.THEME_MODE}":{"type":"string","value":"LIGHT"}
            }}
        """.trimIndent()

        val result = BackupCodec.import(prefs, gson, json)

        assertEquals(BackupCodec.ImportResult.Success(1), result)
        assertEquals("LIGHT", prefs.getString(Constants.PrefsKeys.THEME_MODE, null))
        assertFalse(prefs.contains("someFutureKey"))
        // Live-state key smuggled into a file is ignored on import too.
        assertFalse(prefs.contains(Constants.PrefsKeys.MANUAL_FOCUS_MODE))
        assertFalse(prefs.contains(Constants.PrefsKeys.BREAK_DURATION_MINUTES))
    }
}
