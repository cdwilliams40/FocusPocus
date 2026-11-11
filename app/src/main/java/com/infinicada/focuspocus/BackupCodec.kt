package com.infinicada.focuspocus

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.infinicada.focuspocus.limit.ManaWell
import com.infinicada.focuspocus.limit.WellApp
import com.infinicada.focuspocus.limit.WellApps
import com.infinicada.focuspocus.limit.WellSettings
import com.infinicada.focuspocus.limit.WellSettingsState

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
    /**
     * 2: pacts, wards and circles became the Well's app list.
     * 3: Quick Spells became talisman setups.
     * 4: conditional unlocks became productive apps.
     */
    const val FORMAT_VERSION = 4

    /** What a version-1 file carried instead of [Constants.PrefsKeys.WELL_APPS]. */
    private val V1_LIMIT_KEYS = setOf(
        Migrations.LegacyKeys.APP_TIME_LIMIT_CONFIGS,
        Migrations.LegacyKeys.PACT_GROUPS
    )

    /** What a file before version 3 carried instead of talisman setups. */
    private val V2_PRESET_KEYS = setOf(Migrations.LegacyKeys.FOCUS_PRESETS)

    /** What a file before version 4 carried instead of productive apps. */
    private val V3_UNLOCK_KEYS = setOf(Migrations.LegacyKeys.CONDITIONAL_UNLOCKS)

    /** Configuration and history that travels in a backup. */
    val EXPORT_KEYS: Set<String> = setOf(
        // Spellbook configuration
        Constants.PrefsKeys.BLOCKER_LISTS,
        Constants.PrefsKeys.SCHEDULES,
        Constants.PrefsKeys.NAMED_TAGS,
        Constants.PrefsKeys.WELL_APPS,
        Constants.PrefsKeys.PRODUCTIVE_APPS,
        Constants.PrefsKeys.MANA_HISTORY,
        Constants.PrefsKeys.WELL_HISTORY,
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
        // The Mana Well's terms travel; its level, today's draws and any
        // queued loosening are live state and stay on the device.
        Constants.PrefsKeys.WELL_ENABLED,
        Constants.PrefsKeys.WELL_CAPACITY_MINUTES,
        Constants.PrefsKeys.WELL_REFILL_INTERVAL_MINUTES,
        Constants.PrefsKeys.PROGRESSION_ENABLED,
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
        Constants.PrefsKeys.MANA_BALANCE,
        Constants.PrefsKeys.MANA_LIFETIME_EARNED,
        Constants.PrefsKeys.MANA_LEDGER,
        Constants.PrefsKeys.TRIALS
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
        /**
         * Refused because a focus session is running: a restore replaces
         * enchantments and pacts wholesale, which would dissolve the running
         * session's blocks and skip the pact cooling-off ledger.
         */
        object SessionActive : ImportResult()
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
     *
     * Commitments enforced on this device are the exception to plain
     * replace: see [holdWellApps] and [holdEnforcedWell].
     *
     * A version-1 file's pacts and circles are restored through the same
     * migration an upgraded install runs (Migrations.migrateToWellApps).
     */
    fun import(
        prefs: SharedPreferences,
        gson: Gson,
        json: String,
        now: Long = System.currentTimeMillis()
    ): ImportResult {
        val file = try {
            gson.fromJson(json, BackupFile::class.java)
        } catch (e: JsonSyntaxException) {
            null
        } ?: return ImportResult.InvalidFormat
        if (file.format != FORMAT || file.prefs == null) return ImportResult.InvalidFormat
        if (file.formatVersion > FORMAT_VERSION) return ImportResult.UnsupportedVersion

        val currentApps = WellApps.load(prefs, gson)
        val currentRemovals = WellApps.pendingRemovals(prefs, gson)
        val heldWell = holdEnforcedWell(prefs, file.prefs, now)
        val restoredKeys = EXPORT_KEYS +
            (if (file.formatVersion < 2) V1_LIMIT_KEYS else emptySet()) +
            (if (file.formatVersion < 3) V2_PRESET_KEYS else emptySet()) +
            (if (file.formatVersion < 4) V3_UNLOCK_KEYS else emptySet())

        var restored = 0
        // commit = true, not apply(): the caller restarts the process
        // immediately, and the data must be on disk before that happens.
        prefs.edit(commit = true) {
            EXPORT_KEYS.forEach { remove(it) }
            for ((key, entry) in file.prefs) {
                if (key !in restoredKeys) continue
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
            heldWell?.let { ManaWell.writeState(this, it) }
            // Always on since 2.0, whatever the file says.
            putBoolean(Constants.PrefsKeys.WELL_ENABLED, true)
        }
        if (file.formatVersion < 2) Migrations.migrateToWellApps(prefs)
        if (file.formatVersion < 3) Migrations.migrateTalismans(prefs)
        if (file.formatVersion < 4) Migrations.migrateConditionalUnlocks(prefs)
        holdWellApps(prefs, gson, currentApps, currentRemovals, now)
        return ImportResult.Success(restored)
    }

    /**
     * A restore must not be a way around the 24 h delay on taking an app off
     * the Well: every app the device has now stays, and one the backup leaves
     * out is queued for removal exactly as removing it in the app would be.
     * Apps the backup adds go on at once — that only makes things stricter.
     */
    private fun holdWellApps(
        prefs: SharedPreferences,
        gson: Gson,
        currentApps: List<WellApp>,
        currentRemovals: Map<String, Long>,
        now: Long
    ) {
        if (currentApps.isEmpty()) return
        val restored = WellApps.load(prefs, gson)
        val restoredPackages = restored.mapTo(mutableSetOf()) { it.packageName }
        val kept = currentApps.filter { it.packageName !in restoredPackages }
        WellApps.save(prefs, gson, restored + kept)
        val removals = WellApps.pendingRemovals(prefs, gson).toMutableMap()
        kept.forEach { app ->
            removals[app.packageName] = currentRemovals[app.packageName] ?: (now + WellApps.REMOVAL_DELAY_MS)
        }
        prefs.edit(commit = true) {
            if (removals.isEmpty()) remove(Constants.PrefsKeys.WELL_APP_REMOVALS)
            else putString(Constants.PrefsKeys.WELL_APP_REMOVALS, gson.toJson(removals))
        }
    }

    /**
     * The Mana Well's terms get exactly what changing them in Settings would:
     * the backup's tighter terms apply at once, its looser ones are queued
     * behind the usual 24 h. The Well is always on since 2.0, whatever an
     * older backup says.
     *
     * Returns the terms to write over the restored ones, or null when the
     * Well isn't on and the restore is a plain replace.
     */
    private fun holdEnforcedWell(
        prefs: SharedPreferences,
        backup: Map<String, PrefEntry?>,
        now: Long
    ): WellSettingsState? {
        fun backupValue(key: String, type: String): String? =
            backup[key]?.takeIf { it.type == type }?.value
        val defaults = WellSettings()
        val incoming = WellSettings(
            enabled = true,
            capacityMinutes = backupValue(Constants.PrefsKeys.WELL_CAPACITY_MINUTES, "int")
                ?.toIntOrNull() ?: defaults.capacityMinutes,
            refillIntervalMinutes =
                backupValue(Constants.PrefsKeys.WELL_REFILL_INTERVAL_MINUTES, "int")
                    ?.toIntOrNull() ?: defaults.refillIntervalMinutes
        )
        return ManaWell.holdForRestore(prefs, incoming, now)
    }
}
