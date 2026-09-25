package com.infinicada.focuspocus

import com.google.gson.Gson
import com.infinicada.focuspocus.limit.CooldownState
import com.infinicada.focuspocus.model.AppTimeLimit
import com.infinicada.focuspocus.model.FocusPreset
import com.infinicada.focuspocus.model.PresetAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StatusWidgetTest {

    private lateinit var prefs: FakeSharedPreferences
    private val gson = Gson()
    private val now = System.currentTimeMillis()

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
    }

    private fun resolve(usedToday: Map<String, Int> = emptyMap()) =
        StatusWidget.resolveState(prefs, gson, now) { usedToday }

    private fun pact(pkg: String, backstop: Int = 0) = AppTimeLimit(
        packageName = pkg,
        dailyLimitMinutes = backstop,
        pactModeEnabled = true,
        pactMaxMinutes = 15
    )

    private fun savePresets(vararg presets: FocusPreset) =
        prefs.putString(Constants.PrefsKeys.FOCUS_PRESETS, gson.toJson(presets.toList()))

    @Test
    fun `idle with no guards offers the first castable spell and no headline`() {
        savePresets(
            FocusPreset(name = "Break", durationMinutes = 0, breaksEnabled = false, action = PresetAction.TEMP_DISABLE),
            FocusPreset(name = "Deep Work", durationMinutes = 50, breaksEnabled = true)
        )

        val state = resolve()

        assertNull(state.session)
        assertEquals("Deep Work", state.quickSpellName)
        assertNull(state.headline)
    }

    @Test
    fun `only break spells leave nothing to cast`() {
        savePresets(
            FocusPreset(name = "Break", durationMinutes = 0, breaksEnabled = false, action = PresetAction.TEMP_DISABLE)
        )
        assertNull(resolve().quickSpellName)
    }

    @Test
    fun `a running session is carried through with its countdown`() {
        val end = now + 25 * 60_000L
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        prefs.putString(Constants.PrefsKeys.ACTIVE_BLOCKERS, gson.toJson(listOf("Social")))
        prefs.putLong(Constants.PrefsKeys.SESSION_START_TIME, now - 60_000L)
        prefs.putLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, end)

        val session = resolve().session

        assertNotNull(session)
        assertEquals("Social", session!!.sessionName)
        assertEquals(end, session.countdownEndMillis)
    }

    @Test
    fun `headline counts seals, running pacts, and spent limits`() {
        AppTimeLimitManager.saveTimeLimitConfigs(
            prefs, gson,
            mapOf(
                "sealed.app" to pact("sealed.app"),
                "open.app" to pact("open.app"),
                "spent.app" to pact("spent.app", backstop = 30),
                "quiet.app" to pact("quiet.app")
            )
        )
        prefs.putString(
            Constants.PrefsKeys.APP_COOLDOWN_STATES,
            gson.toJson(mapOf("sealed.app" to CooldownState("sealed.app", now + 10 * 60_000L)))
        )
        prefs.putString(
            Constants.PrefsKeys.PACT_ALLOWANCES,
            gson.toJson(mapOf("open.app" to now + 5 * 60_000L))
        )

        val headline = resolve(usedToday = mapOf("spent.app" to 45)).headline

        assertNotNull(headline)
        assertEquals(1, headline!!.sealedCount)
        assertEquals(1, headline.pactActiveCount)
        assertEquals(1, headline.overLimitCount)
    }

    @Test
    fun `usage stats are only queried when a guard has a daily limit`() {
        AppTimeLimitManager.saveTimeLimitConfigs(prefs, gson, mapOf("a" to pact("a")))
        var queried = false
        StatusWidget.resolveState(prefs, gson, now) { queried = true; emptyMap() }
        assertFalse(queried)

        AppTimeLimitManager.saveTimeLimitConfigs(prefs, gson, mapOf("a" to pact("a", backstop = 30)))
        StatusWidget.resolveState(prefs, gson, now) { queried = true; emptyMap() }
        assertTrue(queried)
    }

    @Test
    fun `streak and mana follow progression settings`() {
        prefs.putString(
            Constants.PrefsKeys.FOCUS_SESSIONS,
            gson.toJson(listOf(FocusSession(now - 30 * 60_000L, now, 30, "Social", 0)))
        )
        prefs.putLong(Constants.PrefsKeys.MANA_BALANCE, 340L)

        val on = resolve()
        assertEquals(1, on.streak)
        assertEquals(340L, on.manaBalance)

        prefs.putBoolean(Constants.PrefsKeys.PROGRESSION_ENABLED, false)
        assertNull(resolve().manaBalance)
    }
}
