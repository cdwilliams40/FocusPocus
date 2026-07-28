package com.infinicada.focuspocus

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * Grimoire export/import: serializes the app's configuration and history to a
 * self-describing JSON document and restores it with replace semantics.
 *
 * Only keys on the explicit [EXPORT_KEYS] list travel — live enforcement and
 * session state (running sessions, active seals and allowances, Warden
 * bookkeeping, daily-rollover markers) deliberately stays on the device: a
 * backup restored days later must not resurrect a stale seal or a phantom
 * running session. Unknown keys in a backup file are ignored, so a newer
 * build's export degrades gracefully on an older build within the same
 * format version.
 *
 * Values are stored as (type, string) pairs rather than raw JSON values
 * because Gson round-trips untyped numbers as doubles — "5" must come back an
 * Int for `getInt` to find it.
 */
object BackupCodec {

    const val FORMAT = "focus-pocus-backup"
    const val FORMAT_VERSION = 1

    /** Configuration and history that travels in a backup. */
    val EXPORT_KEYS: Set<String> = setOf(
        // Spellbook configuration
        Constants.PrefsKeys.BLOCKER_LISTS,
        Constants.PrefsKeys.SCHEDULES,
        Constants.PrefsKeys.FOCUS_PRESETS,
        Constants.PrefsKeys.NAMED_TAGS,
        Constants.PrefsKeys.APP_TIME_LIMITS,
        Constants.PrefsKeys.APP_TIME_LIMIT_CONFIGS,
        Constants.PrefsKeys.PACT_GROUPS,
        Constants.PrefsKeys.CONDITIONAL_UNLOCKS,
        // Settings
        Constants.PrefsKeys.THEME_MODE,
        Constants.PrefsKeys.MUTE_BLOCKED_NOTIFICATIONS,
        Constants.PrefsKeys.BREAK_DURATION_MINUTES,
        Constants.PrefsKeys.MAX_BREAKS_PER_SESSION,
        Constants.PrefsKeys.AUTO_BREAK_ENABLED,
        Constants.PrefsKeys.AUTO_BREAK_INTERVAL_MINUTES,
        Constants.PrefsKeys.EMERGENCY_BREAK_CADENCE_WEEKS,
        Constants.PrefsKeys.HIDE_STOP_BUTTON,
        Constants.PrefsKeys.NFC_LOCK_MODE,
        Constants.PrefsKeys.SEAL_LIFTED_ALERTS_ENABLED,
        Constants.PrefsKeys.PROGRESSION_ENABLED,
        Constants.PrefsKeys.WRAPUP_ENABLED,
        Constants.PrefsKeys.TRIAL_ALERTS_ENABLED,
        Constants.PrefsKeys.ANALYTICS_CONSENT,
        Constants.PrefsKeys.ANALYTICS_CONSENT_SHOWN,
        Constants.PrefsKeys.ONBOARDING_COMPLETED,
        Constants.PrefsKeys.ONBOARDING_VERSION,
        Constants.PrefsKeys.PROGRESSION_INTRO_SHOWN,
        Constants.PrefsKeys.PACTS_HOME_INTRO_SHOWN,
        Constants.PrefsKeys.DEVICE_OWNER_ENFORCEMENT,
        Constants.PrefsKeys.DEVICE_OWNER_SUSPEND_PACTS,
        Constants.PrefsKeys.INSIGHTS_TIME_RANGE,
        // History & progression
        Constants.PrefsKeys.FOCUS_SESSIONS,
        Constants.PrefsKeys.LONGEST_STREAK,
        Constants.PrefsKeys.BLOCK_EVENTS,
        Constants.PrefsKeys.APP_OPEN_STATS,
        Constants.PrefsKeys.LAST_EMERGENCY_BREAK_MILLIS,
        Constants.PrefsKeys.LAST_SESSION_RECORDED_DATE,
        Constants.PrefsKeys.MANA_BALANCE,
        Constants.PrefsKeys.MANA_LIFETIME_EARNED,
        Constants.PrefsKeys.MANA_LEDGER,
        Constants.PrefsKeys.BOONS,
        Constants.PrefsKeys.TRIALS,
        Constants.PrefsKeys.UNLOCKED_SIGILS,
        Constants.PrefsKeys.HIGHEST_STREAK_MILESTONE_PAID
    )

    data class PrefEntry(val type: String?, val value: String?)

    data class BackupFile(
        val format: String? = null,
        val formatVersion: Int = 0,
        val appVersionCode: Int = 0,
        val exportedAtMillis: Long = 0L,
        val prefs: Map<String, PrefEntry>? = null
    )

    sealed class ImportResult {
        data class Success(val restoredKeys: Int) : ImportResult()
        object InvalidFormat : ImportResult()
        object UnsupportedVersion : ImportResult()
    }

    fun export(
        prefs: SharedPreferences,
        gson: Gson,
        appVersionCode: Int,
        now: Long = System.currentTimeMillis()
    ): String {
        val all = prefs.all
        val entries = mutableMapOf<String, PrefEntry>()
        for (key in EXPORT_KEYS) {
            val value = all[key] ?: continue
            val entry = when (value) {
                is String -> PrefEntry("string", value)
                is Boolean -> PrefEntry("boolean", value.toString())
                is Int -> PrefEntry("int", value.toString())
                is Long -> PrefEntry("long", value.toString())
                is Float -> PrefEntry("float", value.toString())
                else -> continue
            }
            entries[key] = entry
        }
        return gson.toJson(
            BackupFile(
                format = FORMAT,
                formatVersion = FORMAT_VERSION,
                appVersionCode = appVersionCode,
                exportedAtMillis = now,
                prefs = entries
            )
        )
    }

    /**
     * Replace-restores [json] into [prefs]: every [EXPORT_KEYS] key is removed
     * first, then the file's values (filtered to [EXPORT_KEYS]) are written,
     * all in one synchronous commit so the process can safely restart right
     * after. Keys outside the export list — including live enforcement
     * state — are never touched.
     */
    fun import(prefs: SharedPreferences, gson: Gson, json: String): ImportResult {
        val file = try {
            gson.fromJson(json, BackupFile::class.java)
        } catch (e: JsonSyntaxException) {
            null
        } ?: return ImportResult.InvalidFormat
        if (file.format != FORMAT || file.prefs == null) return ImportResult.InvalidFormat
        if (file.formatVersion > FORMAT_VERSION) return ImportResult.UnsupportedVersion

        var restored = 0
        // commit = true, not apply(): the caller restarts the process
        // immediately, and the data must be on disk before that happens.
        prefs.edit(commit = true) {
            EXPORT_KEYS.forEach { remove(it) }
            for ((key, entry) in file.prefs) {
                if (key !in EXPORT_KEYS) continue
                val value = entry?.value ?: continue
                val applied = when (entry.type) {
                    "string" -> { putString(key, value); true }
                    "boolean" -> value.toBooleanStrictOrNull()
                        ?.let { putBoolean(key, it); true } ?: false
                    "int" -> value.toIntOrNull()?.let { putInt(key, it); true } ?: false
                    "long" -> value.toLongOrNull()?.let { putLong(key, it); true } ?: false
                    "float" -> value.toFloatOrNull()?.let { putFloat(key, it); true } ?: false
                    else -> false
                }
                if (applied) restored++
            }
        }
        return ImportResult.Success(restored)
    }
}
