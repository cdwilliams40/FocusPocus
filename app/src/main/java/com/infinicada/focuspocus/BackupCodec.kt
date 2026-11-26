package com.infinicada.focuspocus

import android.content.SharedPreferences
import androidx.core.content.edit
import com.infinicada.focuspocus.limit.ManaWell
import com.infinicada.focuspocus.limit.WellApp
import com.infinicada.focuspocus.limit.WellApps
import com.infinicada.focuspocus.limit.WellGateState
import com.infinicada.focuspocus.limit.WellRefillGate
import com.infinicada.focuspocus.limit.WellSettings
import com.infinicada.focuspocus.limit.WellSettingsState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * Grimoire export/import: serializes the app's configuration and history to a
 * self-describing JSON document and restores it with replace semantics.
 */
object BackupCodec {

    const val FORMAT = "focus-pocus-backup"
    const val FORMAT_VERSION = 4

    private val V1_LIMIT_KEYS = setOf(
        Migrations.LegacyKeys.APP_TIME_LIMIT_CONFIGS,
        Migrations.LegacyKeys.PACT_GROUPS
    )
    private val V2_PRESET_KEYS = setOf(Migrations.LegacyKeys.FOCUS_PRESETS)
    private val V3_UNLOCK_KEYS = setOf(Migrations.LegacyKeys.CONDITIONAL_UNLOCKS)

    /** Configuration and history that travels in a backup. */
    val EXPORT_KEYS: Set<String> = setOf(
        Constants.PrefsKeys.BLOCKER_LISTS,
        Constants.PrefsKeys.SCHEDULES,
        Constants.PrefsKeys.NAMED_TAGS,
        Constants.PrefsKeys.WELL_APPS,
        Constants.PrefsKeys.PRODUCTIVE_APPS,
        Constants.PrefsKeys.MANA_HISTORY,
        Constants.PrefsKeys.WELL_HISTORY,
        Constants.PrefsKeys.THEME_MODE,
        Constants.PrefsKeys.MUTE_BLOCKED_NOTIFICATIONS,
        Constants.PrefsKeys.BREAK_DURATION_MINUTES,
        Constants.PrefsKeys.MAX_BREAKS_PER_SESSION,
        Constants.PrefsKeys.AUTO_BREAK_ENABLED,
        Constants.PrefsKeys.AUTO_BREAK_INTERVAL_MINUTES,
        Constants.PrefsKeys.EMERGENCY_BREAK_CADENCE_WEEKS,
        Constants.PrefsKeys.HIDE_STOP_BUTTON,
        Constants.PrefsKeys.NFC_LOCK_MODE,
        Constants.PrefsKeys.WELL_ENABLED,
        Constants.PrefsKeys.WELL_CAPACITY_MINUTES,
        Constants.PrefsKeys.WELL_REFILL_INTERVAL_MINUTES,
        Constants.PrefsKeys.WELL_QUIET_HOURS_ENABLED,
        Constants.PrefsKeys.WELL_QUIET_START_MINUTE,
        Constants.PrefsKeys.WELL_QUIET_END_MINUTE,
        Constants.PrefsKeys.WELL_MORNING_FOCUS_REQUIRED,
        Constants.PrefsKeys.WELL_MORNING_FOCUS_MINUTES,
        Constants.PrefsKeys.RITUAL_EXCEPTIONS,
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
        Constants.PrefsKeys.LAST_MANUAL_BLOCKERS,
        Constants.PrefsKeys.LAST_MANUAL_DURATION_MINUTES,
        Constants.PrefsKeys.LAST_MANUAL_BREAKS_ENABLED,
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

    @Serializable
    data class PrefEntry(val type: String? = null, val value: String? = null)

    @Serializable
    data class BackupFile(
        val format: String? = null,
        val formatVersion: Int = 0,
        val appVersionCode: Int = 0,
        val exportedAtMillis: Long = 0L,
        val prefs: Map<String, PrefEntry?>? = null
    )

    sealed class ImportResult {
        data class Success(val restoredKeys: Int) : ImportResult()
        data object InvalidFormat : ImportResult()
        data object UnsupportedVersion : ImportResult()
        data object SessionActive : ImportResult()
    }

    fun export(
        prefs: SharedPreferences,
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
        return AppJson.json.encodeToString(
            BackupFile(
                format = FORMAT,
                formatVersion = FORMAT_VERSION,
                appVersionCode = appVersionCode,
                exportedAtMillis = now,
                prefs = entries
            )
        )
    }

    fun import(
        prefs: SharedPreferences,
        json: String,
        now: Long = System.currentTimeMillis()
    ): ImportResult {
        val file = try {
            AppJson.json.decodeFromString<BackupFile>(json)
        } catch (_: Exception) {
            null
        } ?: return ImportResult.InvalidFormat
        if (file.format != FORMAT || file.prefs == null) return ImportResult.InvalidFormat
        if (file.formatVersion > FORMAT_VERSION) return ImportResult.UnsupportedVersion

        val currentApps = WellApps.load(prefs)
        val currentRemovals = WellApps.pendingRemovals(prefs)
        val heldWell = holdEnforcedWell(prefs, file.prefs, now)
        val heldGate = holdEnforcedGate(prefs, file.prefs, now)
        val currentSkips = RitualExceptions.loadAll(prefs)
        val restoredKeys = EXPORT_KEYS +
            (if (file.formatVersion < 2) V1_LIMIT_KEYS else emptySet()) +
            (if (file.formatVersion < 3) V2_PRESET_KEYS else emptySet()) +
            (if (file.formatVersion < 4) V3_UNLOCK_KEYS else emptySet())

        var restored = 0
        prefs.edit(commit = true) {
            EXPORT_KEYS.forEach { remove(it) }
            for ((key, entry) in file.prefs) {
                if (key !in restoredKeys || entry == null) continue
                val value = entry.value ?: continue
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
            heldGate?.let { ManaWell.writeGateState(this, it) }
            putBoolean(Constants.PrefsKeys.WELL_ENABLED, true)
        }
        if (file.formatVersion < 2) Migrations.migrateToWellApps(prefs)
        if (file.formatVersion < 3) Migrations.migrateTalismans(prefs)
        if (file.formatVersion < 4) {
            Migrations.migrateConditionalUnlocks(prefs)
            Migrations.dropUnfinishedTrials(prefs)
        }
        Migrations.dropReflexTrials(prefs)
        holdWellApps(prefs, currentApps, currentRemovals, now)
        RitualExceptions.holdForRestore(prefs, currentSkips, now)
        return ImportResult.Success(restored)
    }

    private fun holdWellApps(
        prefs: SharedPreferences,
        currentApps: List<WellApp>,
        currentRemovals: Map<String, Long>,
        now: Long
    ) {
        if (currentApps.isEmpty()) return
        val restored = WellApps.load(prefs)
        val restoredPackages = restored.mapTo(mutableSetOf()) { it.packageName }
        val kept = currentApps.filter { it.packageName !in restoredPackages }
        WellApps.save(prefs, restored + kept)
        val removals = WellApps.pendingRemovals(prefs).toMutableMap()
        kept.forEach { app ->
            removals[app.packageName] = currentRemovals[app.packageName] ?: (now + WellApps.REMOVAL_DELAY_MS)
        }
        prefs.edit(commit = true) {
            if (removals.isEmpty()) remove(Constants.PrefsKeys.WELL_APP_REMOVALS)
            else putString(Constants.PrefsKeys.WELL_APP_REMOVALS, AppJson.json.encodeToString(removals))
        }
    }

    /** The refill gate a restore may write without loosening an enforced Well. */
    private fun holdEnforcedGate(
        prefs: SharedPreferences,
        backup: Map<String, PrefEntry?>,
        now: Long
    ): WellGateState? {
        fun backupValue(key: String, type: String): String? =
            backup[key]?.takeIf { it.type == type }?.value
        val defaults = WellRefillGate()
        val incoming = WellRefillGate(
            quietHoursEnabled = backupValue(Constants.PrefsKeys.WELL_QUIET_HOURS_ENABLED, "boolean")
                ?.toBooleanStrictOrNull() ?: defaults.quietHoursEnabled,
            quietStartMinute = backupValue(Constants.PrefsKeys.WELL_QUIET_START_MINUTE, "int")
                ?.toIntOrNull() ?: defaults.quietStartMinute,
            quietEndMinute = backupValue(Constants.PrefsKeys.WELL_QUIET_END_MINUTE, "int")
                ?.toIntOrNull() ?: defaults.quietEndMinute,
            morningFocusRequired = backupValue(Constants.PrefsKeys.WELL_MORNING_FOCUS_REQUIRED, "boolean")
                ?.toBooleanStrictOrNull() ?: defaults.morningFocusRequired,
            morningFocusMinutes = backupValue(Constants.PrefsKeys.WELL_MORNING_FOCUS_MINUTES, "int")
                ?.toIntOrNull() ?: defaults.morningFocusMinutes
        )
        return ManaWell.holdGateForRestore(prefs, incoming, now)
    }

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
            refillIntervalMinutes = backupValue(Constants.PrefsKeys.WELL_REFILL_INTERVAL_MINUTES, "int")
                ?.toIntOrNull() ?: defaults.refillIntervalMinutes
        )
        return ManaWell.holdForRestore(prefs, incoming, now)
    }
}
