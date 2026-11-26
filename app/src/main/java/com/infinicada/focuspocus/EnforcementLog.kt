package com.infinicada.focuspocus

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class EnforcementLogEntry(
    val timestampMillis: Long = 0L,
    val category: String = "",
    val message: String = ""
)

/**
 * Lightweight local ring buffer of enforcement events (last [MAX_ENTRIES] entries)
 * surfaced under Settings -> Protection Health so missed rituals, OEM service kills,
 * exact alarm wakeups, and Warden syncs are easy to diagnose on real devices.
 */
object EnforcementLog {
    private const val TAG = "EnforcementLog"
    const val MAX_ENTRIES = 50

    const val CAT_SERVICE = "SERVICE"
    const val CAT_ALARM = "ALARM"
    const val CAT_SESSION = "SESSION"
    const val CAT_RITUAL = "RITUAL"
    const val CAT_PACT = "PACT"
    const val CAT_PROTECTION = "PROTECTION"
    const val CAT_BACKUP = "BACKUP"

    @Synchronized
    fun record(
        prefs: SharedPreferences,
        category: String,
        message: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        try {
            val entries = loadChronological(prefs).toMutableList()
            entries.add(EnforcementLogEntry(nowMillis, category, message))
            val trimmed = if (entries.size > MAX_ENTRIES) {
                entries.takeLast(MAX_ENTRIES)
            } else {
                entries
            }
            prefs.edit {
                putString(Constants.PrefsKeys.ENFORCEMENT_LOG, AppJson.json.encodeToString(trimmed))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record enforcement log entry", e)
        }
    }

    /** Returns entries newest-first for display. */
    fun load(prefs: SharedPreferences): List<EnforcementLogEntry> =
        loadChronological(prefs).asReversed()

    fun clear(prefs: SharedPreferences) {
        prefs.edit { remove(Constants.PrefsKeys.ENFORCEMENT_LOG) }
    }

    private fun loadChronological(prefs: SharedPreferences): List<EnforcementLogEntry> {
        val json = prefs.getString(Constants.PrefsKeys.ENFORCEMENT_LOG, null) ?: return emptyList()
        return try {
            AppJson.decodeListLenient(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse enforcement log", e)
            emptyList()
        }
    }
}
