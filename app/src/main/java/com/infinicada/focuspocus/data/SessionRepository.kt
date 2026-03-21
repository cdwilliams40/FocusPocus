package com.infinicada.focuspocus.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.BlockEvent
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.DndController
import com.infinicada.focuspocus.FocusSession
import com.infinicada.focuspocus.PrefsHelper
import com.infinicada.focuspocus.SessionManager
import com.infinicada.focuspocus.SessionRecorder
import com.infinicada.focuspocus.calculateCurrentStreak

class SessionRepository(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    // Session lifecycle
    fun startSession(blockerNames: List<String>, durationMinutes: Int = 0, breaksEnabled: Boolean = true) {
        SessionManager.startSession(prefs, blockerNames, durationMinutes = durationMinutes, breaksEnabled = breaksEnabled)
    }

    fun stopSession() {
        SessionManager.stopSession(context, prefs, gson)
    }

    fun isSessionActive(): Boolean = SessionManager.isSessionActive(prefs)

    fun recordSession(): List<FocusSession> = SessionRecorder.record(prefs, gson)

    // Session state
    fun getManualFocusMode(): Boolean =
        prefs.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)

    fun setManualFocusMode(active: Boolean) {
        prefs.edit().putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, active).apply()
    }

    fun getActiveBlockerName(): String? =
        prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null)

    fun getActiveBlockerNames(): List<String> {
        val json = prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKERS, null)
        if (json != null) {
            return try {
                gson.fromJson(json, Array<String>::class.java)?.toList() ?: emptyList()
            } catch (e: Exception) {
                android.util.Log.e("SessionRepository", "Error parsing active blockers JSON", e)
                // Fall back to single blocker pref
                val single = prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null)
                if (single != null) listOf(single) else emptyList()
            }
        }
        // Fallback to old single-blocker pref
        val single = prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null)
        return if (single != null) listOf(single) else emptyList()
    }

    fun setActiveBlockerNames(names: List<String>) {
        prefs.edit()
            .putString(Constants.PrefsKeys.ACTIVE_BLOCKERS, gson.toJson(names))
            .putString(Constants.PrefsKeys.ACTIVE_BLOCKER, names.firstOrNull())
            .apply()
    }

    fun getActiveScheduleId(): String? =
        prefs.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)

    fun getFocusDurationMinutes(): Int =
        prefs.getInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, 0)

    fun setFocusDurationMinutes(minutes: Int) {
        prefs.edit().putInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, minutes).apply()
    }

    fun getFocusTimeRemaining(): Int =
        prefs.getInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, 0)

    fun setFocusTimeRemaining(seconds: Int) {
        prefs.edit().putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, seconds).apply()
    }

    fun getSessionBreaksEnabled(): Boolean =
        prefs.getBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, true)

    fun setSessionBreaksEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, enabled).apply()
    }

    fun getSessionStartTime(): Long =
        prefs.getLong(Constants.PrefsKeys.SESSION_START_TIME, 0L)

    // Break state
    fun getIsOnBreak(): Boolean =
        prefs.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)

    fun setIsOnBreak(isOnBreak: Boolean) {
        prefs.edit().putBoolean(Constants.PrefsKeys.IS_ON_BREAK, isOnBreak).apply()
    }

    fun getBreaksUsedThisSession(): Int =
        prefs.getInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, 0)

    fun setBreaksUsedThisSession(count: Int) {
        prefs.edit().putInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, count).apply()
    }

    fun getBreakTimeRemaining(): Int =
        prefs.getInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, 0)

    fun setBreakTimeRemaining(seconds: Int) {
        prefs.edit().putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, seconds).apply()
    }

    // Emergency break
    fun getLastEmergencyBreakMillis(): Long =
        prefs.getLong(Constants.PrefsKeys.LAST_EMERGENCY_BREAK_MILLIS, 0L)

    fun setLastEmergencyBreakMillis(millis: Long) {
        prefs.edit().putLong(Constants.PrefsKeys.LAST_EMERGENCY_BREAK_MILLIS, millis).apply()
    }

    // Focus tag
    fun getFocusTagId(): String? =
        prefs.getString(Constants.PrefsKeys.FOCUS_TAG_ID, null)

    fun setFocusTagId(tagId: String?) {
        if (tagId != null) {
            prefs.edit().putString(Constants.PrefsKeys.FOCUS_TAG_ID, tagId).apply()
        } else {
            prefs.edit().remove(Constants.PrefsKeys.FOCUS_TAG_ID).apply()
        }
    }

    // Session history
    fun getFocusSessions(): List<FocusSession> {
        val type = object : TypeToken<List<FocusSession>>() {}.type
        return PrefsHelper.load<List<FocusSession>>(prefs, gson, Constants.PrefsKeys.FOCUS_SESSIONS, type)
            ?: emptyList()
    }

    fun getLongestStreak(): Int =
        prefs.getInt(Constants.PrefsKeys.LONGEST_STREAK, 0)

    fun getCurrentStreak(): Int = calculateCurrentStreak(getFocusSessions())

    // Block events
    fun getBlockEvents(): List<BlockEvent> {
        val type = object : TypeToken<List<BlockEvent>>() {}.type
        return PrefsHelper.load<List<BlockEvent>>(prefs, gson, Constants.PrefsKeys.BLOCK_EVENTS, type)
            ?: emptyList()
    }

    // Batch write for focus mode state changes
    fun writeFocusModeState(
        manualFocusMode: Boolean,
        activeBlockerNames: List<String>,
        activeScheduleId: String?,
        isOnBreak: Boolean = false,
        breakTimeRemaining: Int = 0,
        breaksUsedThisSession: Int = 0,
        focusTimeRemaining: Int = 0
    ) {
        val editor = prefs.edit()
            .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, manualFocusMode)

        if (activeScheduleId == null) {
            editor.putString(Constants.PrefsKeys.ACTIVE_BLOCKERS, gson.toJson(activeBlockerNames))
            editor.putString(Constants.PrefsKeys.ACTIVE_BLOCKER, activeBlockerNames.firstOrNull())
        }

        if (!manualFocusMode) {
            editor
                .putInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, breaksUsedThisSession)
                .putBoolean(Constants.PrefsKeys.IS_ON_BREAK, isOnBreak)
                .putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, breakTimeRemaining)
                .putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, focusTimeRemaining)
        }

        editor.apply()
        DndController.updateDndState(context)
    }

    // Break state batch write
    fun writeBreakState(isOnBreak: Boolean, breakTimeRemaining: Int, breaksUsed: Int) {
        prefs.edit()
            .putBoolean(Constants.PrefsKeys.IS_ON_BREAK, isOnBreak)
            .putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, breakTimeRemaining)
            .putInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, breaksUsed)
            .apply()
        DndController.updateDndState(context)
    }

    // Clean up dangling references
    fun clearDanglingActiveBlocker(blockerNames: Set<String>) {
        val activeNames = getActiveBlockerNames()
        if (activeNames.isEmpty()) return
        if (activeNames.none { it in blockerNames }) {
            prefs.edit()
                .remove(Constants.PrefsKeys.ACTIVE_BLOCKER)
                .remove(Constants.PrefsKeys.ACTIVE_BLOCKERS)
                .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
                .apply()
        }
    }

    fun clearDanglingActiveSchedule(scheduleIds: Set<String>): String? {
        val activeId = getActiveScheduleId() ?: return null
        if (activeId !in scheduleIds) {
            prefs.edit().remove(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID).apply()
            return null
        }
        return activeId
    }

    // Listener for accessibility service changes to active schedule
    fun registerScheduleIdChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterScheduleIdChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
