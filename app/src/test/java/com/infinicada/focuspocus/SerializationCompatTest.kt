package com.infinicada.focuspocus

import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.limit.OpenReflexTracker
import com.infinicada.focuspocus.limit.WellApp
import com.infinicada.focuspocus.model.DayOfWeek
import com.infinicada.focuspocus.model.LedgerKind
import com.infinicada.focuspocus.model.ManaDay
import com.infinicada.focuspocus.model.ManaLedgerEntry
import com.infinicada.focuspocus.model.PactIntention
import com.infinicada.focuspocus.model.Schedule
import com.infinicada.focuspocus.model.Trial
import com.infinicada.focuspocus.model.TrialPeriod
import com.infinicada.focuspocus.model.TrialType
import com.infinicada.focuspocus.model.WellDay
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that every persisted `@Serializable` model decodes real JSON payloads
 * previously written by Gson (including missing fields added in newer versions,
 * retired/unknown fields, and damaged list entries) without crashing or losing data.
 */
class SerializationCompatTest {

    @Test
    fun `Blocker decodes legacy Gson payload with unknown websites field and absent autoAddNewApps`() {
        val legacyJson = """
            [
              {
                "name": "Social",
                "mode": "BLACKLIST",
                "apps": ["com.twitter.android", "com.instagram.android"],
                "websites": ["twitter.com", "reddit.com"]
              },
              {
                "name": "Study",
                "mode": "WHITELIST",
                "apps": ["com.obsidian"]
              }
            ]
        """.trimIndent()

        val decoded = AppJson.decodeListLenient<Blocker>(legacyJson)
        assertEquals(2, decoded.size)
        assertEquals("Social", decoded[0].name)
        assertEquals(BlockerMode.BLACKLIST, decoded[0].mode)
        assertEquals(setOf("com.twitter.android", "com.instagram.android"), decoded[0].apps)
        assertFalse(decoded[0].autoAddNewApps)

        assertEquals("Study", decoded[1].name)
        assertEquals(BlockerMode.WHITELIST, decoded[1].mode)
        assertEquals(setOf("com.obsidian"), decoded[1].apps)
    }

    @Test
    fun `BlockEvent decodes legacy Gson payload and round-trips`() {
        val legacyJson = """
            [{"packageName":"com.example.app","timestamp":1700000000000,"blockerName":"Social"}]
        """.trimIndent()

        val events = AppJson.decodeListLenient<BlockEvent>(legacyJson)
        assertEquals(1, events.size)
        assertEquals(BlockEvent("com.example.app", 1700000000000L, "Social"), events.single())

        val reencoded = AppJson.json.encodeToString(events)
        assertEquals(events, AppJson.decodeListLenient<BlockEvent>(reencoded))
    }

    @Test
    fun `FocusSession decodes pre-2_0 Gson payload missing source and plannedMinutes`() {
        val legacyJson = """
            [
              {
                "startTimeMillis": 1700000000000,
                "endTimeMillis": 1700001800000,
                "durationMinutes": 30,
                "blockerName": "Deep Work",
                "breaksUsed": 1
              }
            ]
        """.trimIndent()

        val sessions = AppJson.decodeListLenient<FocusSession>(legacyJson)
        assertEquals(1, sessions.size)
        val s = sessions.single()
        assertEquals(1700000000000L, s.startTimeMillis)
        assertEquals(30, s.durationMinutes)
        assertEquals("Deep Work", s.blockerName)
        assertEquals(1, s.breaksUsed)
        assertNull(s.source)
        assertNull(s.plannedMinutes)
    }

    @Test
    fun `Talisman decodes pre-2_0 Gson payload with only id and name`() {
        val legacyJson = """[{"id":"04A1B2C3","name":"Desk Tag"}]"""

        val talismans = AppJson.decodeListLenient<Talisman>(legacyJson)
        assertEquals(1, talismans.size)
        val t = talismans.single()
        assertEquals("04A1B2C3", t.id)
        assertEquals("Desk Tag", t.name)
        assertTrue(t.enchantments.isEmpty())
        assertNull(t.timedMinutes)
        assertTrue(t.breaksAllowed)
        assertTrue(t.locksSession)
    }

    @Test
    fun `Schedule decodes legacy single-blocker Gson payload without blockerNames`() {
        val legacyJson = """
            [
              {
                "id": "sched-legacy",
                "name": "Morning",
                "blockerName": "Social",
                "days": ["MONDAY", "WEDNESDAY", "FRIDAY"],
                "startTime": "09:00",
                "endTime": "12:00",
                "breaksEnabled": false,
                "breakDurationMinutes": 10,
                "maxBreaksPerSession": 2
              }
            ]
        """.trimIndent()

        val schedules = AppJson.decodeListLenient<Schedule>(legacyJson)
        assertEquals(1, schedules.size)
        val s = schedules.single()
        assertEquals("sched-legacy", s.id)
        assertEquals(listOf("Social"), s.effectiveBlockerNames)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), s.effectiveDays)
        assertEquals("09:00", s.effectiveStartTime)
        assertEquals("12:00", s.effectiveEndTime)
        assertFalse(s.breaksEnabled)
    }

    @Test
    fun `WellApp decodes legacy payload with and without substitutePackage`() {
        val legacyJson = """[{"packageName":"com.social"},{"packageName":"com.video","substitutePackage":"com.read"}]"""
        val apps = AppJson.decodeListLenient<WellApp>(legacyJson)
        assertEquals(
            listOf(WellApp("com.social", null), WellApp("com.video", "com.read")),
            apps
        )
    }

    @Test
    fun `OpenReflexTracker decodes legacy single-day Gson payload with retired reflexOpens field`() {
        val prefs = FakeSharedPreferences()
        prefs.putString(
            Constants.PrefsKeys.APP_OPEN_STATS,
            """{"date":"20261110","stats":{"com.social":{"opens":7,"reflexOpens":3}}}"""
        )
        val tracker = OpenReflexTracker(prefs, today = { "20261110" })
        assertEquals(mapOf("com.social" to AppOpenStats(opens = 7)), tracker.getAllStats())
    }

    @Test
    fun `WellDay decodes pre-2_1 Gson payload missing substituteWins intentions and drawsByHour`() {
        val legacyJson = """
            {
              "20261110": {
                "drawnMillis": 900000,
                "refundedMillis": 300000,
                "draws": 3,
                "pours": 1,
                "dryBlocks": 2,
                "perApp": {"com.social": 1200000}
              }
            }
        """.trimIndent()

        val days = AppJson.json.decodeFromString<Map<String, WellDay>>(legacyJson)
        val day = days.getValue("20261110")
        assertEquals(900_000L, day.drawnMillis)
        assertEquals(300_000L, day.refundedMillis)
        assertEquals(3, day.draws)
        assertEquals(1, day.pours)
        assertEquals(2, day.dryBlocks)
        assertEquals(mapOf("com.social" to 1_200_000L), day.perApp)
        assertEquals(0, day.substituteWins)
        assertNull(day.intentions)
        assertNull(day.drawsByHour)

        // Round-trip with new fields populated
        val updated = day.copy(
            substituteWins = 4,
            intentions = mapOf(PactIntention.QUICK_REPLY.name to 2),
            drawsByHour = mapOf(9 to 5, 22 to 10)
        )
        val encoded = AppJson.json.encodeToString(mapOf("20261110" to updated))
        assertEquals(updated, AppJson.json.decodeFromString<Map<String, WellDay>>(encoded)["20261110"])
    }

    @Test
    fun `ManaDay decodes pre-2_1 Gson payload missing substitute field`() {
        val legacyJson = """
            {
              "20261110": {
                "timedSession": 60,
                "openEndedSession": 15,
                "productive": 20,
                "trials": 40,
                "pours": 75,
                "breaks": 50,
                "productiveMinutes": 40
              }
            }
        """.trimIndent()

        val days = AppJson.json.decodeFromString<Map<String, ManaDay>>(legacyJson)
        val day = days.getValue("20261110")
        assertEquals(0L, day.substitute)
        assertEquals(135L, day.earned)
        assertEquals(125L, day.spent)
        assertEquals(40, day.productiveMinutes)
    }

    @Test
    fun `Trial decodes retired and current enum values and skips corrupt entries`() {
        val legacyJson = """
            [
              {"id":"t1","type":"NO_REFLEX_OPENS","target":1,"progress":1,"period":"DAILY","periodKey":"20261110","rewardMana":40,"claimed":true},
              {"id":"t2","type":"COMPLETE_SESSIONS","target":2,"progress":2,"period":"DAILY","periodKey":"20261110","rewardMana":40,"claimed":false,"param":0},
              {"id":"t3","type":"SUBSTITUTE_WINS","target":3,"progress":1,"period":"DAILY","periodKey":"20261110","rewardMana":40,"claimed":false},
              {"id":"bad","type":"OBFUSCATED_ENUM","target":1,"progress":0,"period":"DAILY","periodKey":"20261110","rewardMana":40,"claimed":false}
            ]
        """.trimIndent()

        val trials = AppJson.decodeListLenient<Trial>(legacyJson)
        assertEquals(listOf("t1", "t2", "t3"), trials.map { it.id })
        assertEquals(TrialType.NO_REFLEX_OPENS, trials[0].type)
        assertEquals(TrialType.COMPLETE_SESSIONS, trials[1].type)
        assertEquals(TrialType.SUBSTITUTE_WINS, trials[2].type)
        assertEquals(TrialPeriod.DAILY, trials[0].period)
    }

    @Test
    fun `ManaLedgerEntry decodes legacy kinds and new SUBSTITUTE_WIN while skipping corrupt entries`() {
        val legacyJson = """
            [
              {"timestampMillis":1,"amount":40,"kind":"BOON","dateKey":"20260710"},
              {"timestampMillis":2,"amount":50,"kind":"MILESTONE","dateKey":"20260710"},
              {"timestampMillis":3,"amount":25,"kind":"SIGIL","dateKey":"20260710"},
              {"timestampMillis":4,"amount":5,"kind":"SUBSTITUTE_WIN","packageName":"com.read","dateKey":"20261110"},
              {"timestampMillis":5,"amount":10,"kind":"UNKNOWN_LEGACY"}
            ]
        """.trimIndent()

        val entries = AppJson.decodeListLenient<ManaLedgerEntry>(legacyJson)
        assertEquals(
            listOf(LedgerKind.BOON, LedgerKind.MILESTONE, LedgerKind.SIGIL, LedgerKind.SUBSTITUTE_WIN),
            entries.map { it.kind }
        )
        assertEquals("com.read", entries.last().packageName)
    }

    @Test
    fun `EnforcementLogEntry round-trips cleanly and skips damaged rows`() {
        val prefs = FakeSharedPreferences()
        prefs.putString(
            Constants.PrefsKeys.ENFORCEMENT_LOG,
            """[{"timestampMillis":100,"category":"ALARM","message":"ok"},null,{"timestampMillis":"not-a-number"}]"""
        )
        val loaded = EnforcementLog.load(prefs)
        assertEquals(1, loaded.size)
        assertEquals(EnforcementLogEntry(100L, "ALARM", "ok"), loaded.single())
    }
}
