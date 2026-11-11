package com.infinicada.focuspocus

import com.google.gson.Gson
import com.infinicada.focuspocus.limit.ManaWell
import com.infinicada.focuspocus.limit.WellApp
import com.infinicada.focuspocus.limit.WellApps
import com.infinicada.focuspocus.limit.WellSettings
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
        target.putString(Constants.PrefsKeys.WELL_APP_REMOVALS, """{"com.x":123}""")
        target.putString(Constants.PrefsKeys.PACT_ALLOWANCES, """{"com.x":123}""")

        BackupCodec.import(target, gson, json)

        assertFalse(target.contains(Constants.PrefsKeys.SCHEDULES))
        assertTrue(target.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false))
        assertEquals("""{"com.x":123}""", target.getString(Constants.PrefsKeys.WELL_APP_REMOVALS, null))
        assertEquals("""{"com.x":123}""", target.getString(Constants.PrefsKeys.PACT_ALLOWANCES, null))
    }

    @Test
    fun `live enforcement state never travels in an export`() {
        seedTypicalData()
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        prefs.putString(Constants.PrefsKeys.WELL_APP_REMOVALS, """{"com.x":123}""")
        prefs.putString(Constants.PrefsKeys.PACT_ALLOWANCES, """{"com.x":123}""")
        prefs.putString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, "some-id")

        val json = BackupCodec.export(prefs, gson, appVersionCode = 30, now = t0)

        assertFalse(json.contains(Constants.PrefsKeys.MANUAL_FOCUS_MODE))
        assertFalse(json.contains(Constants.PrefsKeys.WELL_APP_REMOVALS))
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
            // pact or skip a queued removal's delay
            Constants.PrefsKeys.PACT_ALLOWANCES,
            Constants.PrefsKeys.WELL_APP_REMOVALS,
            // The Mana Well's pool, today's draws and any queued loosening —
            // a restore must neither refill the Well nor skip its cooling-off
            Constants.PrefsKeys.WELL_LEVEL_MILLIS,
            Constants.PrefsKeys.WELL_UPDATED_AT_MILLIS,
            Constants.PrefsKeys.WELL_DRAWN_DATE,
            Constants.PrefsKeys.WELL_DRAWN_MILLIS,
            Constants.PrefsKeys.WELL_UNLOCK_CREDIT_DATE,
            Constants.PrefsKeys.WELL_UNLOCK_CREDITED_RULES,
            Constants.PrefsKeys.WELL_PENDING_APPLIES_AT_MILLIS,
            Constants.PrefsKeys.WELL_PENDING_ENABLED,
            Constants.PrefsKeys.WELL_PENDING_CAPACITY_MINUTES,
            Constants.PrefsKeys.WELL_PENDING_REFILL_INTERVAL_MINUTES,
            // This device's Warden bookkeeping and permission snapshot
            Constants.PrefsKeys.DEVICE_OWNER_SUSPENDED_PACKAGES,
            Constants.PrefsKeys.WARDEN_REMOVAL_REQUEST_MILLIS,
            Constants.PrefsKeys.USAGE_PERMISSION_SNAPSHOT,
            // Which migrations this device's data has been through
            Constants.PrefsKeys.SCHEMA_VERSION
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

    // ── Mana Well ──

    private val wellOn = WellSettings(enabled = true, capacityMinutes = 30, refillIntervalMinutes = 20)

    @Test
    fun `mana well terms travel in a backup but its pool does not`() {
        ManaWell.requestSettings(prefs, wellOn.copy(capacityMinutes = 45), t0)
        ManaWell.tryDraw(prefs, 20, t0, "20261109")
        val json = BackupCodec.export(prefs, gson, appVersionCode = 39, now = t0)

        assertFalse(json.contains(Constants.PrefsKeys.WELL_LEVEL_MILLIS))
        assertFalse(json.contains(Constants.PrefsKeys.WELL_DRAWN_MILLIS))

        val fresh = FakeSharedPreferences()
        BackupCodec.import(fresh, gson, json, t0)
        assertEquals(wellOn.copy(capacityMinutes = 45), ManaWell.settings(fresh, t0))
        // A device that never had the Well gets a full one, not the drained one.
        assertEquals(45, ManaWell.snapshot(fresh, t0, "20261109").availableMinutesAt(t0))
    }

    @Test
    fun `restore never switches the Well off or refills it`() {
        // The backup predates the Well entirely, so it says nothing about it.
        val json = BackupCodec.export(FakeSharedPreferences(), gson, appVersionCode = 38, now = t0)

        ManaWell.requestSettings(prefs, wellOn, t0)
        ManaWell.tryDraw(prefs, 30, t0, "20261109")
        BackupCodec.import(prefs, gson, json, t0)

        assertEquals(wellOn, ManaWell.settings(prefs, t0))
        assertEquals(0, ManaWell.snapshot(prefs, t0, "20261109").availableMinutesAt(t0))
        assertEquals(null, ManaWell.settingsState(prefs, t0).pending)
    }

    @Test
    fun `restore applies a stricter mana well at once`() {
        val backup = FakeSharedPreferences()
        ManaWell.requestSettings(backup, wellOn.copy(capacityMinutes = 15, refillIntervalMinutes = 40), t0)
        val json = BackupCodec.export(backup, gson, appVersionCode = 39, now = t0)

        ManaWell.requestSettings(prefs, wellOn, t0)
        BackupCodec.import(prefs, gson, json, t0)

        assertEquals(
            wellOn.copy(capacityMinutes = 15, refillIntervalMinutes = 40),
            ManaWell.settings(prefs, t0)
        )
        assertEquals(null, ManaWell.settingsState(prefs, t0).pending)
    }

    // ── Well apps ──

    private fun wellPackages(p: FakeSharedPreferences) = WellApps.packages(p, gson)

    @Test
    fun `well apps travel in a backup`() {
        WellApps.save(prefs, gson, listOf(WellApp("com.a", substitutePackage = "com.read"), WellApp("com.b")))
        val fresh = FakeSharedPreferences()
        BackupCodec.import(fresh, gson, BackupCodec.export(prefs, gson, appVersionCode = 43, now = t0), now = t0)
        assertEquals(WellApps.load(prefs, gson), WellApps.load(fresh, gson))
    }

    @Test
    fun `restore keeps the device's well apps and queues the removal of ones the backup leaves out`() {
        prefs.putString(Constants.PrefsKeys.THEME_MODE, "DARK")
        val json = BackupCodec.export(prefs, gson, appVersionCode = 43, now = t0)

        val device = FakeSharedPreferences()
        WellApps.save(device, gson, listOf(WellApp("com.a")))
        BackupCodec.import(device, gson, json, now = t0)

        assertEquals(setOf("com.a"), wellPackages(device))
        assertEquals(
            mapOf("com.a" to t0 + WellApps.REMOVAL_DELAY_MS),
            WellApps.pendingRemovals(device, gson)
        )
        // Everything else still restores normally.
        assertEquals("DARK", device.getString(Constants.PrefsKeys.THEME_MODE, null))
    }

    @Test
    fun `restore adds the backup's well apps at once and queues nothing for shared ones`() {
        WellApps.save(prefs, gson, listOf(WellApp("com.a"), WellApp("com.b")))
        val json = BackupCodec.export(prefs, gson, appVersionCode = 43, now = t0)

        val device = FakeSharedPreferences()
        WellApps.save(device, gson, listOf(WellApp("com.a")))
        BackupCodec.import(device, gson, json, now = t0)

        assertEquals(setOf("com.a", "com.b"), wellPackages(device))
        assertTrue(WellApps.pendingRemovals(device, gson).isEmpty())
    }

    @Test
    fun `a version 1 backup's pacts and circles become well apps and its wards are dropped`() {
        val configs = gson.toJson(gson.toJson(mapOf(
            "com.pact" to mapOf("packageName" to "com.pact", "pactModeEnabled" to true,
                "pactAlternativePackage" to "com.read"),
            "com.ward" to mapOf("packageName" to "com.ward", "dailyLimitMinutes" to 30)
        )))
        val groups = gson.toJson(gson.toJson(listOf(mapOf("blockerName" to "Social"))))
        val blockers = gson.toJson("""[{"name":"Social","mode":"BLACKLIST","apps":["com.circle"]}]""")
        val json = """
            {"format":"${BackupCodec.FORMAT}","formatVersion":1,"prefs":{
                "appTimeLimitConfigs":{"type":"string","value":$configs},
                "pactGroups":{"type":"string","value":$groups},
                "${Constants.PrefsKeys.BLOCKER_LISTS}":{"type":"string","value":$blockers}
            }}
        """.trimIndent()

        val device = FakeSharedPreferences()
        val result = BackupCodec.import(device, gson, json, now = t0)

        assertTrue(result is BackupCodec.ImportResult.Success)
        assertEquals(setOf("com.pact", "com.circle"), wellPackages(device))
        assertEquals("com.read", WellApps.find(device, gson, "com.pact")?.substitutePackage)
        assertFalse(device.contains("appTimeLimitConfigs"))
        assertFalse(device.contains("pactGroups"))
        assertTrue(device.getBoolean(Constants.PrefsKeys.WELL_ENABLED, false))
    }
}
