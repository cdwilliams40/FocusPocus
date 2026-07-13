package com.infinicada.focuspocus.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.BlockEvent
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.DeviceOwnerManager
import com.infinicada.focuspocus.DndController
import com.infinicada.focuspocus.FocusSession
import com.infinicada.focuspocus.PrefsHelper
import com.infinicada.focuspocus.RecordResult
import com.infinicada.focuspocus.SessionManager
import com.infinicada.focuspocus.calculateCurrentStreak

class SessionRepository(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    // Session lifecycle
    fun startSession(blockerNames: List<String>, durationMinutes: Int = 0, breaksEnabled: Boolean = true) {
        SessionManager.startSession(prefs, blockerNames, durationMinutes = durationMinutes, breaksEnabled = breaksEnabled)
        DeviceOwnerManager.syncSuspensions(context)
    }

    fun stopSession(): RecordResult = SessionManager.stopSession(context, prefs, gson)

    fun isSessionActive(): Boolean = SessionManager.isSessionActive(prefs)

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
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
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

    fun getFocusEndTimeMillis(): Long =
        prefs.getLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, 0L)

    fun getBreakEndTimeMillis(): Long =
        prefs.getLong(Constants.PrefsKeys.BREAK_END_TIME_MILLIS, 0L)

    /**
     * Focus seconds remaining derived from the persisted wall-clock end time.
     * The stored countdown only ticks while the UI is alive, so after process
     * death it is stale; the end timestamp is authoritative. During a break the
     * countdown is paused and the stored (frozen) value is correct.
     */
    fun getEffectiveFocusTimeRemaining(): Int {
        if (getIsOnBreak()) return getFocusTimeRemaining()
        val endTime = getFocusEndTimeMillis()
        if (endTime <= 0L) return getFocusTimeRemaining()
        return (((endTime - System.currentTimeMillis()) / 1000L).toInt()).coerceAtLeast(0)
    }

    /** Break seconds remaining derived from the persisted wall-clock end time. */
    fun getEffectiveBreakTimeRemaining(): Int {
        if (!getIsOnBreak()) return 0
        val endTime = getBreakEndTimeMillis()
        if (endTime <= 0L) return getBreakTimeRemaining()
        return (((endTime - System.currentTimeMillis()) / 1000L).toInt()).coerceAtLeast(0)
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
        // Talisman sessions engage DND and device-owner suspensions like any other session.
        DndController.updateDndState(context)
        DeviceOwnerManager.syncSuspensions(context)
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
                .remove(Constants.PrefsKeys.BREAK_END_TIME_MILLIS)
                .remove(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS)
                .remove(Constants.PrefsKeys.FOCUS_SEGMENT_START_MILLIS)
        }

        editor.apply()
        DndController.updateDndState(context)
        DeviceOwnerManager.syncSuspensions(context)
    }

    // Break state batch write.
    // The focus countdown pauses during a break, so the focus end time is removed
    // when a break starts and recomputed from [focusTimeRemaining] when it ends.
    fun writeBreakState(
        isOnBreak: Boolean,
        breakTimeRemaining: Int,
        breaksUsed: Int,
        focusTimeRemaining: Int = 0
    ) {
        val editor = prefs.edit()
            .putBoolean(Constants.PrefsKeys.IS_ON_BREAK, isOnBreak)
            .putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, breakTimeRemaining)
            .putInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, breaksUsed)
        if (isOnBreak && breakTimeRemaining > 0) {
            editor.putLong(Constants.PrefsKeys.BREAK_END_TIME_MILLIS,
                System.currentTimeMillis() + breakTimeRemaining * 1000L)
            // Park the focus countdown at its frozen value while the break runs;
            // the end timestamp is re-derived from it when the break ends.
            editor.putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, focusTimeRemaining)
            editor.remove(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS)
        } else {
            editor.remove(Constants.PrefsKeys.BREAK_END_TIME_MILLIS)
            if (focusTimeRemaining > 0) {
                editor.putLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS,
                    System.currentTimeMillis() + focusTimeRemaining * 1000L)
            }
            // A fresh focus stretch begins when the break ends
            editor.putLong(Constants.PrefsKeys.FOCUS_SEGMENT_START_MILLIS, System.currentTimeMillis())
        }
        editor.apply()
        DndController.updateDndState(context)
        DeviceOwnerManager.syncSuspensions(context)
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
