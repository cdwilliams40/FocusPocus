package com.infinicada.focuspocus

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.infinicada.focuspocus.limit.WellApp
import com.infinicada.focuspocus.limit.WellApps
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Brings stored data up to the schema this build expects. Every step is keyed
 * on [Constants.PrefsKeys.SCHEMA_VERSION] and safe to run twice: a step that
 * dies halfway re-runs on the next process start, and backup import runs the
 * same steps over restored data.
 *
 * Add a step by appending to [steps]; never reorder or remove one.
 */
object Migrations {
    private const val TAG = "Migrations"

    /** Prefs keys of features removed in 2.0. Kept here, not in Constants, so
     *  nothing else can start using them again. */
    internal object LegacyKeys {
        const val BOONS = "boons"
        const val UNLOCKED_SIGILS = "unlockedSigils"
        const val HIGHEST_STREAK_MILESTONE_PAID = "highestStreakMilestonePaid"
        const val LAST_SESSION_RECORDED_DATE = "lastSessionRecordedDate"
        const val LAST_WRAPUP_DATE = "lastWrapupDate"
        const val WRAPUP_ENABLED = "wrapupEnabled"

        const val APP_TIME_LIMITS = "appTimeLimits"
        const val APP_TIME_LIMIT_CONFIGS = "appTimeLimitConfigs"
        const val APP_COOLDOWN_STATES = "appCooldownStates"
        const val LAST_COOLDOWN_RESET_DATE = "lastCooldownResetDate"
        const val PACT_GROUPS = "pactGroups"
        const val PACT_PENDING_REVISIONS = "pactPendingRevisions"
        const val SEAL_LIFTED_ALERTS_ENABLED = "sealLiftedAlertsEnabled"
        const val GROUP_SEAL_ENABLED = "groupSealEnabled"
        const val GROUP_SEAL_OPEN_WINDOW_MINUTES = "groupSealOpenWindowMinutes"
        const val GROUP_SEAL_DURATION_MINUTES = "groupSealDurationMinutes"

        const val FOCUS_PRESETS = "focusPresets"
        const val FOCUS_TAG_ID = "focusTagId"
        const val ACTIVE_BLOCKER = "activeBlocker"

        const val CONDITIONAL_UNLOCKS = "conditionalUnlocks"
        const val WELL_UNLOCK_CREDIT_DATE = "wellUnlockCreditDate"
        const val WELL_UNLOCK_CREDITED_RULES = "wellUnlockCreditedRules"
    }

    private val steps: List<(SharedPreferences) -> Unit> = listOf(
        { prefs ->
            prefs.edit {
                remove(LegacyKeys.BOONS)
                remove(LegacyKeys.UNLOCKED_SIGILS)
                remove(LegacyKeys.HIGHEST_STREAK_MILESTONE_PAID)
                remove(LegacyKeys.LAST_SESSION_RECORDED_DATE)
                remove(LegacyKeys.LAST_WRAPUP_DATE)
                remove(LegacyKeys.WRAPUP_ENABLED)
            }
        },
        { prefs -> migrateToWellApps(prefs) },
        { prefs ->
            migrateTalismans(prefs)
            convertRunningTagSession(prefs)
            migrateActiveBlocker(prefs)
        },
        { prefs ->
            migrateConditionalUnlocks(prefs)
            dropUnfinishedTrials(prefs)
        },
        { prefs ->
            dropReflexTrials(prefs)
        },
        { prefs -> seedLastManualSetup(prefs) }
    )

    val CURRENT_VERSION: Int get() = steps.size

    internal fun migrateToWellApps(prefs: SharedPreferences) {
        val apps = LinkedHashMap<String, WellApp>()
        WellApps.load(prefs).forEach { apps[it.packageName] = it }

        (parse(prefs, LegacyKeys.APP_TIME_LIMIT_CONFIGS) as? JsonObject)?.forEach { (pkg, value) ->
            val config = value as? JsonObject ?: return@forEach
            if (config.bool("pactModeEnabled") && pkg !in apps) {
                apps[pkg] = WellApp(pkg, config.str("pactAlternativePackage"))
            }
        }

        val blockers = BlockerRepository.getBlockers(prefs)
        val circleMembers = HashMap<String, Set<String>>()
        (parse(prefs, LegacyKeys.PACT_GROUPS) as? JsonArray)?.forEach { value ->
            val group = value as? JsonObject ?: return@forEach
            val blockerName = group.str("blockerName") ?: return@forEach
            val members = blockers.firstOrNull { it.name == blockerName }?.effectiveApps.orEmpty().toSet()
            circleMembers[blockerName] = members
            val substitute = group.str("pactAlternativePackage")
            members.forEach { pkg -> if (pkg !in apps) apps[pkg] = WellApp(pkg, substitute) }
        }

        val removals = HashMap(WellApps.pendingRemovals(prefs))
        (parse(prefs, LegacyKeys.PACT_PENDING_REVISIONS) as? JsonArray)?.forEach { value ->
            val revision = value as? JsonObject ?: return@forEach
            val appliesAt = revision.long("appliesAtMillis").takeIf { it > 0L } ?: return@forEach
            val newConfig = revision["newConfig"] as? JsonObject
            val newGroup = revision["newGroup"] as? JsonObject
            val pkg = revision.str("packageName")
            val blockerName = revision.str("blockerName")
            val targets = when {
                pkg != null && (newConfig == null || !newConfig.bool("pactModeEnabled")) -> setOf(pkg)
                pkg == null && blockerName != null && newGroup == null -> circleMembers[blockerName].orEmpty()
                else -> emptySet()
            }
            targets.filter { it in apps }.forEach { removals.merge(it, appliesAt, ::minOf) }
        }

        WellApps.save(prefs, apps.values.toList())
        prefs.edit {
            if (removals.isEmpty()) remove(Constants.PrefsKeys.WELL_APP_REMOVALS)
            else putString(Constants.PrefsKeys.WELL_APP_REMOVALS, AppJson.json.encodeToString(removals))
            putBoolean(Constants.PrefsKeys.WELL_ENABLED, true)
            if (prefs.contains(Constants.PrefsKeys.WELL_PENDING_ENABLED)) {
                putBoolean(Constants.PrefsKeys.WELL_PENDING_ENABLED, true)
            }
            listOf(
                LegacyKeys.APP_TIME_LIMITS, LegacyKeys.APP_TIME_LIMIT_CONFIGS,
                LegacyKeys.APP_COOLDOWN_STATES, LegacyKeys.LAST_COOLDOWN_RESET_DATE,
                LegacyKeys.PACT_GROUPS, LegacyKeys.PACT_PENDING_REVISIONS,
                LegacyKeys.SEAL_LIFTED_ALERTS_ENABLED, LegacyKeys.GROUP_SEAL_ENABLED,
                LegacyKeys.GROUP_SEAL_OPEN_WINDOW_MINUTES, LegacyKeys.GROUP_SEAL_DURATION_MINUTES
            ).forEach { remove(it) }
        }
    }

    internal fun migrateTalismans(prefs: SharedPreferences) {
        val talismans = PrefsHelper.loadListLenient<Talisman>(prefs, Constants.PrefsKeys.NAMED_TAGS).orEmpty()
        val presets = (parse(prefs, LegacyKeys.FOCUS_PRESETS) as? JsonArray)
            ?.mapNotNull { it as? JsonObject }.orEmpty()
        val focusTabSelection = activeBlockerNames(prefs)

        val updated = talismans.map { talisman ->
            if (talisman.blockerNames != null) return@map talisman
            val preset = presets.firstOrNull { it.str("talismanId") == talisman.id }
            val action = preset?.str("action") ?: "TOGGLE"
            if (preset == null || action == "TEMP_DISABLE") {
                talisman.copy(blockerNames = focusTabSelection)
            } else {
                val names = (preset["blockerNames"] as? JsonArray)
                    ?.mapNotNull { e -> (e as? JsonPrimitive)?.contentOrNull }
                    .orEmpty()
                    .ifEmpty { listOfNotNull(preset.str("blockerName")) }
                val minutes = if (action == "TEMP_ENABLE") {
                    preset.int("tempDurationMinutes").takeIf { it > 0 } ?: 30
                } else {
                    preset.int("durationMinutes")
                }
                talisman.copy(
                    blockerNames = names,
                    durationMinutes = minutes,
                    breaksEnabled = preset.bool("breaksEnabled"),
                    requireToEnd = true
                )
            }
        }
        prefs.edit {
            if (updated.isNotEmpty()) {
                putString(Constants.PrefsKeys.NAMED_TAGS, AppJson.json.encodeToString(updated))
            }
            remove(LegacyKeys.FOCUS_PRESETS)
        }
    }

    internal fun migrateConditionalUnlocks(prefs: SharedPreferences) {
        val required = (parse(prefs, LegacyKeys.CONDITIONAL_UNLOCKS) as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.str("requiredAppPackage") }
            .orEmpty()
        if (required.isNotEmpty()) {
            ProductiveApps.save(prefs, ProductiveApps.load(prefs) + required)
        }
        prefs.edit {
            remove(LegacyKeys.CONDITIONAL_UNLOCKS)
            remove(LegacyKeys.WELL_UNLOCK_CREDIT_DATE)
            remove(LegacyKeys.WELL_UNLOCK_CREDITED_RULES)
        }
    }

    internal fun dropUnfinishedTrials(prefs: SharedPreferences) {
        val trials = parse(prefs, Constants.PrefsKeys.TRIALS) as? JsonArray ?: return
        val kept = trials.mapNotNull { e ->
            val trial = e as? JsonObject ?: return@mapNotNull null
            if (trial.bool("claimed") || trial.int("progress") >= trial.int("target").coerceAtLeast(1)) trial
            else null
        }
        prefs.edit { putString(Constants.PrefsKeys.TRIALS, JsonArray(kept).toString()) }
    }

    internal fun dropReflexTrials(prefs: SharedPreferences) {
        val trials = parse(prefs, Constants.PrefsKeys.TRIALS) as? JsonArray ?: return
        val kept = trials.mapNotNull { e ->
            val trial = e as? JsonObject ?: return@mapNotNull null
            val isReflex = trial.str("type") == "NO_REFLEX_OPENS"
            if (!isReflex || trial.bool("claimed") || trial.int("progress") >= trial.int("target").coerceAtLeast(1)) {
                trial
            } else null
        }
        prefs.edit { putString(Constants.PrefsKeys.TRIALS, JsonArray(kept).toString()) }
    }

    private fun convertRunningTagSession(prefs: SharedPreferences) {
        val tagId = prefs.getString(LegacyKeys.FOCUS_TAG_ID, null)
        prefs.edit { remove(LegacyKeys.FOCUS_TAG_ID) }
        if (tagId == null || SessionManager.isSessionActive(prefs)) return
        val blockers = activeBlockerNames(prefs)
        if (blockers.isEmpty()) return
        SessionManager.startSession(prefs, blockers, talismanId = tagId)
    }

    /**
     * The Focus tab used to remember its setup in the running session's keys
     * (duration, breaks, selected enchantments); it now has keys of its own.
     * Copies the old values across so an upgrade keeps the user's usual
     * session. A ritual or talisman session writes those same keys with its
     * own setup, so while one runs only the duration (a manual choice) is
     * copied. Keys already set are left alone, so a second run changes nothing.
     */
    internal fun seedLastManualSetup(prefs: SharedPreferences) {
        val runningNotManual =
            prefs.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null) != null ||
                prefs.getString(Constants.PrefsKeys.ACTIVE_TALISMAN_ID, null) != null
        prefs.edit {
            if (!prefs.contains(Constants.PrefsKeys.LAST_MANUAL_DURATION_MINUTES)) {
                val duration = prefs.getInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, 0)
                if (duration > 0) putInt(Constants.PrefsKeys.LAST_MANUAL_DURATION_MINUTES, duration)
            }
            if (runningNotManual) return@edit
            if (!prefs.contains(Constants.PrefsKeys.LAST_MANUAL_BREAKS_ENABLED) &&
                prefs.contains(Constants.PrefsKeys.SESSION_BREAKS_ENABLED)
            ) {
                putBoolean(
                    Constants.PrefsKeys.LAST_MANUAL_BREAKS_ENABLED,
                    prefs.getBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, true)
                )
            }
            if (!prefs.contains(Constants.PrefsKeys.LAST_MANUAL_BLOCKERS)) {
                val blockers = activeBlockerNames(prefs)
                if (blockers.isNotEmpty()) {
                    putString(Constants.PrefsKeys.LAST_MANUAL_BLOCKERS, AppJson.json.encodeToString(blockers))
                }
            }
        }
    }

    private fun migrateActiveBlocker(prefs: SharedPreferences) {
        val single = prefs.getString(LegacyKeys.ACTIVE_BLOCKER, null)
        prefs.edit {
            if (single != null && !prefs.contains(Constants.PrefsKeys.ACTIVE_BLOCKERS)) {
                putString(Constants.PrefsKeys.ACTIVE_BLOCKERS, AppJson.json.encodeToString(listOf(single)))
            }
            remove(LegacyKeys.ACTIVE_BLOCKER)
        }
    }

    private fun activeBlockerNames(prefs: SharedPreferences): List<String> =
        (parse(prefs, Constants.PrefsKeys.ACTIVE_BLOCKERS) as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.ifEmpty { null }
            ?: listOfNotNull(prefs.getString(LegacyKeys.ACTIVE_BLOCKER, null))

    private fun parse(prefs: SharedPreferences, key: String): JsonElement? = try {
        prefs.getString(key, null)?.let { AppJson.json.parseToJsonElement(it) }
    } catch (e: Exception) {
        Log.w(TAG, "Unreadable $key, skipped", e)
        null
    }

    private fun JsonObject.str(name: String): String? =
        (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.bool(name: String): Boolean =
        (get(name) as? JsonPrimitive)?.booleanOrNull == true

    private fun JsonObject.int(name: String): Int =
        (get(name) as? JsonPrimitive)?.intOrNull ?: 0

    private fun JsonObject.long(name: String): Long =
        (get(name) as? JsonPrimitive)?.longOrNull ?: 0L

    @Synchronized
    fun run(prefs: SharedPreferences) {
        val from = prefs.getInt(Constants.PrefsKeys.SCHEMA_VERSION, 0)
        if (from >= steps.size) return
        for (version in from until steps.size) {
            steps[version](prefs)
            prefs.edit(commit = true) { putInt(Constants.PrefsKeys.SCHEMA_VERSION, version + 1) }
            Log.i(TAG, "Migrated prefs to schema ${version + 1}")
        }
    }
}
