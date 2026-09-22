package com.infinicada.focuspocus

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.limit.GuardStatus
import com.infinicada.focuspocus.limit.PactManager
import com.infinicada.focuspocus.limit.PactRevisionManager
import com.infinicada.focuspocus.limit.PendingPactRevision
import com.infinicada.focuspocus.model.AppTimeLimit
import com.infinicada.focuspocus.model.PactGroup

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
        Constants.PrefsKeys.GROUP_SEAL_ENABLED,
        Constants.PrefsKeys.GROUP_SEAL_OPEN_WINDOW_MINUTES,
        Constants.PrefsKeys.GROUP_SEAL_DURATION_MINUTES,
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
     * Pacts enforced on this device are the one exception to plain replace:
     * see [holdEnforcedPacts].
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

        val heldPacts = holdEnforcedPacts(prefs, gson, file.prefs, now)

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
            heldPacts?.forEach { (key, value) -> putString(key, value) }
        }
        return ImportResult.Success(restored)
    }

    /**
     * A restore must not be a way around the pact cooling-off: every pact the
     * device currently enforces keeps its current terms, and wherever the
     * backup's terms differ they are queued as a normal 24 h revision instead
     * (a pact missing from the backup queues as a removal) — exactly what
     * editing the pact in the app would do. Enchantments bound to an enforced
     * circle keep their current app list too, since shrinking the list would
     * loosen the circle immediately.
     *
     * Returns the store values to write over the restored ones, or null when
     * no pact is enforced and the restore is a plain replace.
     */
    private fun holdEnforcedPacts(
        prefs: SharedPreferences,
        gson: Gson,
        backup: Map<String, PrefEntry?>,
        now: Long
    ): Map<String, String>? {
        val currentConfigs = AppTimeLimitManager.getTimeLimitConfigs(prefs, gson)
        val currentGroups = PactManager(prefs, gson).getGroups()
        val currentBlockers = BlockerRepository.getBlockers(prefs)
        val gated = GuardStatus.pactGatedPackages(currentConfigs, currentGroups, currentBlockers)
        if (gated.isEmpty() && currentGroups.isEmpty()) return null

        fun backupString(key: String): String? =
            backup[key]?.takeIf { it.type == "string" }?.value
        fun <T> parse(key: String, type: java.lang.reflect.Type): T? = try {
            backupString(key)?.let { gson.fromJson<T>(it, type) }
        } catch (e: Exception) {
            null
        }

        // Same fallback as AppTimeLimitManager's migration: an old backup may
        // carry only the legacy flat daily-limit map.
        val incomingConfigs: MutableMap<String, AppTimeLimit> =
            (parse<Map<String, AppTimeLimit>>(
                Constants.PrefsKeys.APP_TIME_LIMIT_CONFIGS,
                object : TypeToken<Map<String, AppTimeLimit>>() {}.type
            ) ?: parse<Map<String, Int>>(
                Constants.PrefsKeys.APP_TIME_LIMITS,
                object : TypeToken<Map<String, Int>>() {}.type
            )?.mapValues { (pkg, minutes) -> AppTimeLimit(packageName = pkg, dailyLimitMinutes = minutes) }
                ?: emptyMap()).toMutableMap()
        @Suppress("SENSELESS_COMPARISON")
        val incomingGroups: MutableList<PactGroup> = (parse<List<PactGroup>>(
            Constants.PrefsKeys.PACT_GROUPS, object : TypeToken<List<PactGroup>>() {}.type
        ) ?: emptyList()).filterNotNull().filter { it.blockerName != null }.toMutableList()
        val incomingBlockers: MutableList<Blocker> = Blocker.sanitize(
            parse<List<Blocker>>(Constants.PrefsKeys.BLOCKER_LISTS, object : TypeToken<List<Blocker>>() {}.type)
        ).toMutableList()

        val revisions = PactRevisionManager(prefs, gson).getRevisions().toMutableList()
        val appliesAt = now + PactRevisionManager.REVISION_DELAY_MS

        for (group in currentGroups) {
            val incoming = incomingGroups.find { it.blockerName == group.blockerName }
            if (incoming != group) {
                incomingGroups.removeAll { it.blockerName == group.blockerName }
                incomingGroups += group
                revisions.removeAll { it.packageName == null && it.blockerName == group.blockerName }
                revisions += PendingPactRevision(
                    blockerName = group.blockerName,
                    newGroup = incoming,
                    requestedAtMillis = now,
                    appliesAtMillis = appliesAt
                )
            }
            currentBlockers.find { it.name == group.blockerName }?.let { current ->
                val index = incomingBlockers.indexOfFirst { it.name == current.name }
                if (index >= 0) incomingBlockers[index] = current else incomingBlockers += current
            }
        }

        for (pkg in gated) {
            val current = currentConfigs[pkg]
            val incoming = incomingConfigs[pkg]
            if (incoming == current) continue
            if (current != null) incomingConfigs[pkg] = current else incomingConfigs.remove(pkg)
            revisions.removeAll { it.packageName == pkg }
            revisions += PendingPactRevision(
                packageName = pkg,
                newConfig = incoming,
                requestedAtMillis = now,
                appliesAtMillis = appliesAt
            )
        }

        return mapOf(
            Constants.PrefsKeys.APP_TIME_LIMIT_CONFIGS to gson.toJson(incomingConfigs),
            // Kept in lockstep with the config map, as saveTimeLimitConfigs does.
            Constants.PrefsKeys.APP_TIME_LIMITS to
                gson.toJson(incomingConfigs.mapValues { (_, v) -> v.dailyLimitMinutes }),
            Constants.PrefsKeys.PACT_GROUPS to gson.toJson(incomingGroups),
            Constants.PrefsKeys.BLOCKER_LISTS to gson.toJson(incomingBlockers),
            Constants.PrefsKeys.PACT_PENDING_REVISIONS to gson.toJson(revisions)
        )
    }
}
