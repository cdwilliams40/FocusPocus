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

    /**
     * Every key in [Constants.PrefsKeys] must be a deliberate decision: either it
     * travels in a grimoire, or it is device-local enforcement/session state that
     * a backup restored days later must not resurrect. This guard fails on any
     * newly added key until someone classifies it — the omission that left Group
     * Seal's settings out of a backup in 1.8 would have failed here.
     */
    @Test
    fun `every prefs key is either exported or deliberately device-local`() {
        val deviceLocalKeys = setOf(
            // Live session / break state
            Constants.PrefsKeys.MANUAL_FOCUS_MODE,
            Constants.PrefsKeys.ACTIVE_BLOCKER,
            Constants.PrefsKeys.ACTIVE_BLOCKERS,
            Constants.PrefsKeys.ACTIVE_SCHEDULE_ID,
            Constants.PrefsKeys.FOCUS_TAG_ID,
            Constants.PrefsKeys.IS_ON_BREAK,
            Constants.PrefsKeys.BREAKS_USED_THIS_SESSION,
            Constants.PrefsKeys.BREAK_TIME_REMAINING,
            Constants.PrefsKeys.BREAK_END_TIME_MILLIS,
            Constants.PrefsKeys.BREAK_START_TIME_MILLIS,
            Constants.PrefsKeys.SESSION_BREAK_MILLIS,
            Constants.PrefsKeys.FOCUS_DURATION_MINUTES,
            Constants.PrefsKeys.FOCUS_TIME_REMAINING,
            Constants.PrefsKeys.FOCUS_END_TIME_MILLIS,
            Constants.PrefsKeys.FOCUS_SEGMENT_START_MILLIS,
            Constants.PrefsKeys.SCHEDULE_END_TIME_MILLIS,
            Constants.PrefsKeys.SESSION_BREAKS_ENABLED,
            Constants.PrefsKeys.SESSION_START_TIME,
            // Per-session perk tokens: cleared at every session start and stop
            Constants.PrefsKeys.EXTRA_BREAK_TOKENS,
            // Live enforcement state — a restore must not resurrect a stale
            // seal, allowance, or queued pact revision
            Constants.PrefsKeys.APP_COOLDOWN_STATES,
            Constants.PrefsKeys.PACT_ALLOWANCES,
            Constants.PrefsKeys.PACT_PENDING_REVISIONS,
            Constants.PrefsKeys.LAST_COOLDOWN_RESET_DATE,
            Constants.PrefsKeys.LAST_WRAPUP_DATE,
            // This device's Warden bookkeeping and permission snapshot
            Constants.PrefsKeys.DEVICE_OWNER_SUSPENDED_PACKAGES,
            Constants.PrefsKeys.WARDEN_REMOVAL_REQUEST_MILLIS,
            Constants.PrefsKeys.USAGE_PERMISSION_SNAPSHOT
        )

        val allKeys = Constants.PrefsKeys::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.get(null) as String }
            .toSet()

        assertEquals(
            "unclassified prefs keys — add each to BackupCodec.EXPORT_KEYS or to deviceLocalKeys",
            emptySet<String>(),
            allKeys - BackupCodec.EXPORT_KEYS - deviceLocalKeys
        )
        assertEquals(
            "deviceLocalKeys names a key that no longer exists",
            emptySet<String>(),
            deviceLocalKeys - allKeys
        )
        assertEquals(
            "BackupCodec.EXPORT_KEYS names a key that no longer exists",
            emptySet<String>(),
            BackupCodec.EXPORT_KEYS - allKeys
        )
    }

    @Test
    fun `group seal settings travel in a backup`() {
        prefs.putBoolean(Constants.PrefsKeys.GROUP_SEAL_ENABLED, true)
        prefs.putInt(Constants.PrefsKeys.GROUP_SEAL_OPEN_WINDOW_MINUTES, 20)
        prefs.putInt(Constants.PrefsKeys.GROUP_SEAL_DURATION_MINUTES, 45)

        val fresh = FakeSharedPreferences()
        BackupCodec.import(fresh, gson, BackupCodec.export(prefs, gson, appVersionCode = 37, now = t0))

        assertTrue(fresh.getBoolean(Constants.PrefsKeys.GROUP_SEAL_ENABLED, false))
        assertEquals(20, fresh.getInt(Constants.PrefsKeys.GROUP_SEAL_OPEN_WINDOW_MINUTES, -1))
        assertEquals(45, fresh.getInt(Constants.PrefsKeys.GROUP_SEAL_DURATION_MINUTES, -1))
    }

    @Test
    fun `restore keeps enforced per-app pacts and queues the backup's terms`() {
        // Backup made before the pact existed: no config for com.a at all.
        prefs.putString(Constants.PrefsKeys.THEME_MODE, "DARK")
        val json = BackupCodec.export(prefs, gson, appVersionCode = 40, now = t0)

        val device = FakeSharedPreferences()
        val pact = com.infinicada.focuspocus.model.AppTimeLimit(
            packageName = "com.a", dailyLimitMinutes = 0, pactModeEnabled = true
        )
        AppTimeLimitManager.saveTimeLimitConfigs(device, gson, mapOf("com.a" to pact))

        BackupCodec.import(device, gson, json, now = t0)

        assertEquals(pact, AppTimeLimitManager.getTimeLimitConfigs(device, gson)["com.a"])
        val revision = com.infinicada.focuspocus.limit.PactRevisionManager(device, gson)
            .revisionForApp("com.a")
        org.junit.Assert.assertNotNull(revision)
        assertTrue(revision!!.isRemoval)
        assertEquals(
            t0 + com.infinicada.focuspocus.limit.PactRevisionManager.REVISION_DELAY_MS,
            revision.appliesAtMillis
        )
        // Everything else still restores normally.
        assertEquals("DARK", device.getString(Constants.PrefsKeys.THEME_MODE, null))
    }

    @Test
    fun `restore keeps an enforced pact circle and its enchantment's apps`() {
        prefs.putString(
            Constants.PrefsKeys.BLOCKER_LISTS,
            """[{"name":"Social","mode":"BLACKLIST","apps":[]}]"""
        )
        val json = BackupCodec.export(prefs, gson, appVersionCode = 40, now = t0)

        val device = FakeSharedPreferences()
        device.putString(
            Constants.PrefsKeys.BLOCKER_LISTS,
            """[{"name":"Social","mode":"BLACKLIST","apps":["com.b"]}]"""
        )
        val circle = com.infinicada.focuspocus.model.PactGroup(blockerName = "Social")
        com.infinicada.focuspocus.limit.PactManager(device, gson).saveGroup(circle)

        BackupCodec.import(device, gson, json, now = t0)

        assertEquals(listOf(circle), com.infinicada.focuspocus.limit.PactManager(device, gson).getGroups())
        assertEquals(
            setOf("com.b"),
            BlockerRepository.getBlocker(device, "Social")?.effectiveApps
        )
        val revision = com.infinicada.focuspocus.limit.PactRevisionManager(device, gson)
            .revisionForCircle("Social")
        assertTrue(revision!!.isRemoval)
    }

    @Test
    fun `restore with identical pact terms queues nothing`() {
        val pact = com.infinicada.focuspocus.model.AppTimeLimit(
            packageName = "com.a", dailyLimitMinutes = 0, pactModeEnabled = true
        )
        AppTimeLimitManager.saveTimeLimitConfigs(prefs, gson, mapOf("com.a" to pact))
        val json = BackupCodec.export(prefs, gson, appVersionCode = 40, now = t0)

        BackupCodec.import(prefs, gson, json, now = t0)

        assertTrue(com.infinicada.focuspocus.limit.PactRevisionManager(prefs, gson).getRevisions().isEmpty())
        assertEquals(pact, AppTimeLimitManager.getTimeLimitConfigs(prefs, gson)["com.a"])
    }
}
