package com.infinicada.focuspocus.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.infinicada.focuspocus.AdvanceOutcome
import com.infinicada.focuspocus.AppJson
import com.infinicada.focuspocus.BlockEvent
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.FocusSession
import com.infinicada.focuspocus.PrefsHelper
import com.infinicada.focuspocus.RecordResult
import com.infinicada.focuspocus.SessionManager
import com.infinicada.focuspocus.SessionRules
import com.infinicada.focuspocus.SessionState
import com.infinicada.focuspocus.calculateCurrentStreak
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString

class SessionRepository(
    private val context: Context,
    private val prefs: SharedPreferences,
    val store: SessionStore = SessionStore(prefs)
) {
    val state: StateFlow<SessionState> get() = store.state
    val foregroundSummaries: SharedFlow<RecordResult> get() = store.foregroundSummaries

    fun startSession(
        blockerNames: List<String>,
        durationMinutes: Int = 0,
        breaksEnabled: Boolean = true,
        talismanId: String? = null
    ) {
        SessionManager.startSession(
            context = context,
            sharedPreferences = prefs,
            blockerNames = blockerNames,
            durationMinutes = durationMinutes,
            breaksEnabled = breaksEnabled,
            talismanId = talismanId
        )
        store.refresh()
    }

    fun stopSession(): RecordResult {
        val result = SessionManager.stopSession(context, prefs)
        store.refresh()
        return result
    }

    fun takeBreak(breakDurationMinutes: Int): Boolean {
        val started = SessionManager.takeBreak(context, prefs, breakDurationMinutes)
        if (started) store.refresh()
        return started
    }

    fun endBreak(): Boolean {
        val ended = SessionManager.endBreak(context, prefs)
        if (ended) store.refresh()
        return ended
    }

    fun emergencyStop(): RecordResult {
        val result = SessionManager.emergencyStop(context, prefs)
        store.refresh()
        return result
    }

    fun advance(): AdvanceOutcome {
        val outcome = SessionManager.advance(context, prefs)
        if (outcome.changed) store.refresh()
        return outcome
    }

    fun refreshState() {
        store.refresh()
    }

    fun isSessionActive(): Boolean = SessionManager.isSessionActive(prefs)

    fun getActiveScheduleId(): String? =
        (store.state.value as? SessionState.Active)?.scheduleId
            ?: prefs.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)

    fun getActiveTalismanId(): String? = SessionManager.activeTalismanId(prefs)

    fun getActiveBlockerNames(): List<String> =
        (SessionStore.read(prefs) as? SessionState.Active)?.blockerNames ?: emptyList()

    fun getEffectiveFocusTimeRemaining(nowMillis: Long = System.currentTimeMillis()): Int =
        SessionRules.effectiveFocusTimeRemainingSeconds(SessionStore.read(prefs), nowMillis)

    fun getEffectiveBreakTimeRemaining(nowMillis: Long = System.currentTimeMillis()): Int =
        SessionRules.effectiveBreakTimeRemainingSeconds(SessionStore.read(prefs), nowMillis)

    fun getLastManualBlockerNames(): List<String> {
        val json = prefs.getString(Constants.PrefsKeys.LAST_MANUAL_BLOCKERS, null) ?: return emptyList()
        return try {
            AppJson.decodeListLenient(json)
        } catch (e: Exception) {
            android.util.Log.e("SessionRepository", "Error parsing last manual blockers JSON", e)
            emptyList()
        }
    }

    fun setLastManualBlockerNames(names: List<String>) {
        prefs.edit { putString(Constants.PrefsKeys.LAST_MANUAL_BLOCKERS, AppJson.json.encodeToString(names)) }
    }

    fun getLastManualDurationMinutes(): Int =
        prefs.getInt(Constants.PrefsKeys.LAST_MANUAL_DURATION_MINUTES, 0)

    fun setLastManualDurationMinutes(minutes: Int) {
        prefs.edit { putInt(Constants.PrefsKeys.LAST_MANUAL_DURATION_MINUTES, minutes) }
    }

    fun getLastManualBreaksEnabled(): Boolean =
        prefs.getBoolean(Constants.PrefsKeys.LAST_MANUAL_BREAKS_ENABLED, true)

    fun setLastManualBreaksEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(Constants.PrefsKeys.LAST_MANUAL_BREAKS_ENABLED, enabled) }
    }

    fun getLastEmergencyBreakMillis(): Long =
        prefs.getLong(Constants.PrefsKeys.LAST_EMERGENCY_BREAK_MILLIS, 0L)

    fun getFocusSessions(): List<FocusSession> =
        PrefsHelper.loadListLenient<FocusSession>(prefs, Constants.PrefsKeys.FOCUS_SESSIONS) ?: emptyList()

    fun getLongestStreak(): Int = prefs.getInt(Constants.PrefsKeys.LONGEST_STREAK, 0)

    fun getCurrentStreak(): Int = calculateCurrentStreak(getFocusSessions())

    fun getBlockEvents(): List<BlockEvent> =
        PrefsHelper.loadListLenient<BlockEvent>(prefs, Constants.PrefsKeys.BLOCK_EVENTS) ?: emptyList()

    fun clearDanglingActiveBlocker(blockerNames: Set<String>) {
        val activeNames = getActiveBlockerNames()
        if (activeNames.isEmpty()) return
        if (activeNames.none { it in blockerNames }) {
            if (isSessionActive()) {
                // Nothing it blocked exists any more: clear it without
                // recording or crediting it, and without dismissing its ritual.
                SessionManager.discardSession(context, prefs)
                store.refresh()
            } else {
                prefs.edit { remove(Constants.PrefsKeys.ACTIVE_BLOCKERS) }
                store.refresh()
            }
        }
    }

    fun clearDanglingActiveSchedule(scheduleIds: Set<String>): String? {
        val activeId = getActiveScheduleId() ?: return null
        if (activeId !in scheduleIds) {
            stopSession()
            return null
        }
        return activeId
    }
}
