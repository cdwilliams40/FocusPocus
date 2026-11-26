package com.infinicada.focuspocus

import com.infinicada.focuspocus.limit.ManaWell
import com.infinicada.focuspocus.limit.PactManager
import com.infinicada.focuspocus.limit.WellMath
import com.infinicada.focuspocus.limit.WellRefillGate
import com.infinicada.focuspocus.limit.WellSettings
import com.infinicada.focuspocus.model.DayOfWeek
import com.infinicada.focuspocus.model.Schedule
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Covers the fixes made after reviewing the 2.x refactor. */
class ReviewFixesTest {

    private val zone = ZoneId.systemDefault()
    private val allDays = DayOfWeek.entries.toSet()

    // ── Planned skips and overnight rituals ──

    @Test
    fun `yesterday's skip survives midnight so an overnight ritual stays skipped`() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString(
            Constants.PrefsKeys.RITUAL_EXCEPTIONS,
            AppJson.json.encodeToString(mapOf("night" to listOf("20261123", "20261124", "20261126")))
        ).apply()

        val loaded = RitualExceptions.loadAll(prefs, todayKey = "20261125")

        assertEquals(setOf("20261124", "20261126"), loaded["night"])
    }

    @Test
    fun `overnight ritual skipped on Monday is not active at 00 30 on Tuesday`() {
        val ritual = Schedule(
            id = "night", name = "Night", blockerNames = listOf("Social"),
            days = allDays, startTime = "22:00", endTime = "06:00"
        )
        val prefs = FakeSharedPreferences()
        // 2026-11-23 is a Monday.
        prefs.edit().putString(
            Constants.PrefsKeys.RITUAL_EXCEPTIONS,
            AppJson.json.encodeToString(mapOf("night" to listOf("20261123")))
        ).apply()
        val tue0030 = ZonedDateTime.of(2026, 11, 24, 0, 30, 0, 0, zone).toInstant().toEpochMilli()

        val skipped = RitualExceptions.loadAll(prefs, todayKey = "20261124")["night"].orEmpty()

        assertFalse(isScheduleActiveAt(ritual, tue0030, skipped))
    }

    @Test
    fun `a restore keeps planned skips but drops restored ones inside 24 hours`() {
        val ritual = Schedule(
            id = "r", name = "R", blockerNames = listOf("Social"),
            days = allDays, startTime = "09:00", endTime = "12:00"
        )
        val prefs = FakeSharedPreferences()
        prefs.edit()
            .putString(Constants.PrefsKeys.SCHEDULES, AppJson.json.encodeToString(listOf(ritual)))
            // As written by a restore: tomorrow (too soon) and in three days (fine).
            .putString(
                Constants.PrefsKeys.RITUAL_EXCEPTIONS,
                AppJson.json.encodeToString(mapOf("r" to listOf("20261127", "20261129")))
            )
            .apply()
        val thu10am = ZonedDateTime.of(2026, 11, 26, 10, 0, 0, 0, zone).toInstant().toEpochMilli()

        RitualExceptions.holdForRestore(
            prefs, previous = mapOf("r" to setOf("20261128")), nowMillis = thu10am, zoneId = zone
        )

        assertEquals(
            setOf("20261128", "20261129"),
            RitualExceptions.loadAll(prefs, todayKey = "20261126")["r"]
        )
    }

    // ── Refill gate: loosening waits ──

    private val quiet22to7 = WellRefillGate(quietHoursEnabled = true, quietStartMinute = 22 * 60, quietEndMinute = 7 * 60)

    @Test
    fun `turning quiet hours off waits while turning them on applies at once`() {
        val off = WellRefillGate()

        val loosen = WellMath.splitGate(quiet22to7, off, wellEnabled = true)
        assertTrue(loosen.immediate.quietHoursEnabled)
        assertFalse(loosen.pending!!.quietHoursEnabled)

        val tighten = WellMath.splitGate(off, quiet22to7, wellEnabled = true)
        assertTrue(tighten.immediate.quietHoursEnabled)
        assertNull(tighten.pending)
    }

    @Test
    fun `widening quiet hours applies at once and narrowing waits`() {
        val wider = quiet22to7.copy(quietStartMinute = 21 * 60, quietEndMinute = 8 * 60)
        val narrower = quiet22to7.copy(quietStartMinute = 23 * 60)

        assertNull(WellMath.splitGate(quiet22to7, wider, wellEnabled = true).pending)
        val narrow = WellMath.splitGate(quiet22to7, narrower, wellEnabled = true)
        assertEquals(22 * 60, narrow.immediate.quietStartMinute)
        assertEquals(23 * 60, narrow.pending!!.quietStartMinute)
    }

    @Test
    fun `a shorter or dropped morning focus waits and a longer one applies at once`() {
        val morning20 = WellRefillGate(morningFocusRequired = true, morningFocusMinutes = 20)

        assertNull(WellMath.splitGate(morning20, morning20.copy(morningFocusMinutes = 30), true).pending)
        val shorter = WellMath.splitGate(morning20, morning20.copy(morningFocusMinutes = 10), true)
        assertEquals(20, shorter.immediate.morningFocusMinutes)
        assertEquals(10, shorter.pending!!.morningFocusMinutes)
        val dropped = WellMath.splitGate(morning20, WellRefillGate(), true)
        assertTrue(dropped.immediate.morningFocusRequired)
        assertFalse(dropped.pending!!.morningFocusRequired)
    }

    @Test
    fun `with the Well off any gate change applies at once`() {
        val split = WellMath.splitGate(quiet22to7, WellRefillGate(), wellEnabled = false)
        assertFalse(split.immediate.quietHoursEnabled)
        assertNull(split.pending)
    }

    @Test
    fun `a queued gate loosening applies after 24 hours and can be withdrawn`() {
        val prefs = FakeSharedPreferences()
        val t0 = 1_800_000_000_000L
        ManaWell.requestSettings(prefs, WellSettings(enabled = true), t0)
        ManaWell.updateRefillGate(prefs, false, 0, 0, morningFocusRequired = true, morningFocusMinutes = 15, now = t0)

        val state = ManaWell.updateRefillGate(prefs, false, 0, 0, morningFocusRequired = false, morningFocusMinutes = 15, now = t0 + 1)
        assertTrue(state.enforced.morningFocusRequired)
        assertEquals(t0 + 1 + WellMath.REVISION_DELAY_MS, state.pending!!.appliesAtMillis)
        assertTrue(ManaWell.refillGate(prefs, t0 + WellMath.REVISION_DELAY_MS).morningFocusRequired)
        assertFalse(ManaWell.refillGate(prefs, t0 + 1 + WellMath.REVISION_DELAY_MS).morningFocusRequired)

        ManaWell.updateRefillGate(prefs, false, 0, 0, morningFocusRequired = true, morningFocusMinutes = 15, now = t0 + 2 * WellMath.REVISION_DELAY_MS)
        ManaWell.updateRefillGate(prefs, false, 0, 0, morningFocusRequired = false, morningFocusMinutes = 15, now = t0 + 2 * WellMath.REVISION_DELAY_MS)
        ManaWell.cancelPendingGateRevision(prefs)
        val kept = ManaWell.refillGateState(prefs, t0 + 4 * WellMath.REVISION_DELAY_MS)
        assertTrue(kept.enforced.morningFocusRequired)
        assertNull(kept.pending)
    }

    @Test
    fun `a restore cannot loosen the gate of an enforced Well`() {
        val prefs = FakeSharedPreferences()
        val t0 = 1_800_000_000_000L
        ManaWell.requestSettings(prefs, WellSettings(enabled = true), t0)
        ManaWell.updateRefillGate(prefs, true, 22 * 60, 7 * 60, false, 10, t0)

        val held = ManaWell.holdGateForRestore(prefs, WellRefillGate(), t0)

        assertNotNull(held)
        assertTrue(held!!.enforced.quietHoursEnabled)
        assertFalse(held.pending!!.gate.quietHoursEnabled)
    }

    // ── Auto-backup back-off ──

    @Test
    fun `a failed auto-backup retries an hour later, not every minute`() {
        val prefs = FakeSharedPreferences()
        val t0 = 1_700_000_000_000L
        AutoBackupManager.setTreeUri(prefs, "content://tree/primary%3ABackups")
        AutoBackupManager.setCadence(prefs, AutoBackupCadence.DAILY)
        prefs.edit().putLong(Constants.PrefsKeys.AUTO_BACKUP_LAST_FAILURE_MILLIS, t0).apply()

        assertFalse(AutoBackupManager.isDue(prefs, t0 + 60_000L))
        assertEquals(t0 + AutoBackupManager.RETRY_AFTER_FAILURE_MS, AutoBackupManager.nextDueMillis(prefs, t0 + 60_000L))
        assertTrue(AutoBackupManager.isDue(prefs, t0 + AutoBackupManager.RETRY_AFTER_FAILURE_MS))
    }

    // ── Focus tab setup on upgrade ──

    @Test
    fun `upgrade copies the old Focus tab setup into its own keys`() {
        val prefs = FakeSharedPreferences()
        prefs.edit()
            .putInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, 25)
            .putBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, false)
            .putString(Constants.PrefsKeys.ACTIVE_BLOCKERS, AppJson.json.encodeToString(listOf("Social")))
            .apply()

        Migrations.seedLastManualSetup(prefs)

        assertEquals(25, prefs.getInt(Constants.PrefsKeys.LAST_MANUAL_DURATION_MINUTES, 0))
        assertFalse(prefs.getBoolean(Constants.PrefsKeys.LAST_MANUAL_BREAKS_ENABLED, true))
        assertEquals(
            AppJson.json.encodeToString(listOf("Social")),
            prefs.getString(Constants.PrefsKeys.LAST_MANUAL_BLOCKERS, null)
        )
    }

    @Test
    fun `upgrade during a ritual copies only the duration and never overwrites`() {
        val prefs = FakeSharedPreferences()
        prefs.edit()
            .putInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, 25)
            .putBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, false)
            .putString(Constants.PrefsKeys.ACTIVE_BLOCKERS, AppJson.json.encodeToString(listOf("Ritual")))
            .putString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, "r")
            .putInt(Constants.PrefsKeys.LAST_MANUAL_DURATION_MINUTES, 50)
            .apply()

        Migrations.seedLastManualSetup(prefs)

        assertEquals(50, prefs.getInt(Constants.PrefsKeys.LAST_MANUAL_DURATION_MINUTES, 0))
        assertFalse(prefs.contains(Constants.PrefsKeys.LAST_MANUAL_BREAKS_ENABLED))
        assertFalse(prefs.contains(Constants.PrefsKeys.LAST_MANUAL_BLOCKERS))
    }

    // ── Substitutes earn like productive apps ──

    @Test
    fun `Well app substitutes earn productive time but Well apps never do`() {
        val prefs = FakeSharedPreferences()
        ProductiveApps.save(prefs, listOf("notes", "chat"))
        com.infinicada.focuspocus.limit.WellApps.save(
            prefs,
            listOf(
                com.infinicada.focuspocus.limit.WellApp("insta", substitutePackage = "kindle"),
                com.infinicada.focuspocus.limit.WellApp("chat", substitutePackage = "insta")
            )
        )

        assertEquals(setOf("notes", "kindle"), ProductiveApps.earningPackages(prefs))
    }

    // ── Ending pacts from the notification ──

    @Test
    fun `taking all pacts returns the running ones and leaves none behind`() {
        val prefs = FakeSharedPreferences()
        val manager = PactManager(prefs)
        val now = 1_700_000_000_000L
        manager.grantAllowance("a", 10, now)
        manager.grantAllowance("b", 2, now - 5 * 60_000L)

        val taken = manager.takeAllActiveAllowances(now)

        assertEquals(setOf("a"), taken.keys)
        assertTrue(manager.getActiveAllowances(now - 10 * 60_000L).isEmpty())
    }
}
