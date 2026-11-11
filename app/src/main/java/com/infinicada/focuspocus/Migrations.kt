package com.infinicada.focuspocus

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.infinicada.focuspocus.limit.WellApp
import com.infinicada.focuspocus.limit.WellApps

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

        // Wards, pacts with their own terms, pact circles, Group Seal and
        // seal-lifted alerts (all replaced by the Well's app list in 2.0)
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

        // Quick Spells, tag-only sessions and the single-enchantment session
        // key (replaced by talisman setups and ACTIVE_BLOCKERS in 2.0)
        const val FOCUS_PRESETS = "focusPresets"
        const val FOCUS_TAG_ID = "focusTagId"
        const val ACTIVE_BLOCKER = "activeBlocker"
    }

    private val steps: List<(SharedPreferences) -> Unit> = listOf(
        // 1: boons, sigils, streak milestones and the daily wrap-up are gone.
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
        // 2: the Well is the only always-on limit. Every pact app and every
        // pact-circle member becomes a Well app (keeping its substitute), a
        // pending removal of a pact keeps its original time, and the Well is
        // switched on. Wards and every seal/circle/Group Seal store go.
        { prefs -> migrateToWellApps(prefs) },
        // 3: Quick Spells are gone and talismans carry their own setup. A
        // talisman bound to a spell takes its enchantments, breaks and
        // duration; one without takes the enchantments selected on the Focus
        // tab. A tag-only session running now becomes an ordinary recorded
        // session started by that talisman.
        { prefs ->
            migrateTalismans(prefs)
            convertRunningTagSession(prefs)
            migrateActiveBlocker(prefs)
        }
    )

    val CURRENT_VERSION: Int get() = steps.size

    /**
     * Step 2. The old stores are read as JSON trees rather than through the
     * deleted model classes; only three fields matter.
     */
    internal fun migrateToWellApps(prefs: SharedPreferences) {
        val gson = Gson()
        val apps = LinkedHashMap<String, WellApp>()
        WellApps.load(prefs, gson).forEach { apps[it.packageName] = it }

        // Per-app pacts. A ward (no pact mode) is dropped.
        parse(prefs, LegacyKeys.APP_TIME_LIMIT_CONFIGS)?.takeIf { it.isJsonObject }
            ?.asJsonObject?.entrySet()?.forEach { (pkg, value) ->
                val config = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                if (config.bool("pactModeEnabled") && pkg !in apps) {
                    apps[pkg] = WellApp(pkg, config.str("pactAlternativePackage"))
                }
            }

        // Pact circles: their enchantment's current apps.
        val blockers = BlockerRepository.getBlockers(prefs)
        val circleMembers = HashMap<String, Set<String>>()
        parse(prefs, LegacyKeys.PACT_GROUPS)?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { value ->
            val group = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val blockerName = group.str("blockerName") ?: return@forEach
            val members = blockers.firstOrNull { it.name == blockerName }?.effectiveApps.orEmpty().toSet()
            circleMembers[blockerName] = members
            val substitute = group.str("pactAlternativePackage")
            members.forEach { pkg -> if (pkg !in apps) apps[pkg] = WellApp(pkg, substitute) }
        }

        // A pact on its way out (removed, or turned into a ward) keeps its
        // date. Edits that kept a pact a pact have nothing left to change.
        val removals = HashMap(WellApps.pendingRemovals(prefs, gson))
        parse(prefs, LegacyKeys.PACT_PENDING_REVISIONS)?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { value ->
            val revision = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val appliesAt = revision.long("appliesAtMillis").takeIf { it > 0L } ?: return@forEach
            val newConfig = revision.get("newConfig")?.takeIf { it.isJsonObject }?.asJsonObject
            val newGroup = revision.get("newGroup")?.takeIf { it.isJsonObject }
            val pkg = revision.str("packageName")
            val blockerName = revision.str("blockerName")
            val targets = when {
                pkg != null && (newConfig == null || !newConfig.bool("pactModeEnabled")) -> setOf(pkg)
                pkg == null && blockerName != null && newGroup == null -> circleMembers[blockerName].orEmpty()
                else -> emptySet()
            }
            targets.filter { it in apps }.forEach { removals.merge(it, appliesAt, ::minOf) }
        }

        WellApps.save(prefs, gson, apps.values.toList())
        prefs.edit {
            if (removals.isEmpty()) remove(Constants.PrefsKeys.WELL_APP_REMOVALS)
            else putString(Constants.PrefsKeys.WELL_APP_REMOVALS, gson.toJson(removals))
            putBoolean(Constants.PrefsKeys.WELL_ENABLED, true)
            // A queued "switch the Well off" can no longer apply.
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

    /**
     * Step 3, first half. Also run by backup import over a file from before
     * talismans had setups. Leaves talismans that already have one alone.
     */
    internal fun migrateTalismans(prefs: SharedPreferences) {
        val gson = Gson()
        val talismans = PrefsHelper.load<List<Talisman>>(
            prefs, gson, Constants.PrefsKeys.NAMED_TAGS,
            object : com.google.gson.reflect.TypeToken<List<Talisman>>() {}.type
        ).orEmpty().filterNotNull()
        val presets = parse(prefs, LegacyKeys.FOCUS_PRESETS)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }.orEmpty()
        val focusTabSelection = activeBlockerNames(prefs)

        val updated = talismans.map { talisman ->
            if (talisman.blockerNames != null) return@map talisman
            // handleNfcTag used to take the first spell bound to a tag.
            val preset = presets.firstOrNull { it.str("talismanId") == talisman.id }
            val action = preset?.str("action") ?: "TOGGLE"
            if (preset == null || action == "TEMP_DISABLE") {
                talisman.copy(blockerNames = focusTabSelection)
            } else {
                val names = preset.get("blockerNames")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { e -> e.takeIf { it.isJsonPrimitive }?.asString }
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
            if (updated.isNotEmpty()) putString(Constants.PrefsKeys.NAMED_TAGS, gson.toJson(updated))
            remove(LegacyKeys.FOCUS_PRESETS)
        }
    }

    /** Step 3, second half: a running tag-only session becomes a real one. */
    private fun convertRunningTagSession(prefs: SharedPreferences) {
        val tagId = prefs.getString(LegacyKeys.FOCUS_TAG_ID, null)
        prefs.edit { remove(LegacyKeys.FOCUS_TAG_ID) }
        if (tagId == null || SessionManager.isSessionActive(prefs)) return
        val blockers = activeBlockerNames(prefs)
        if (blockers.isEmpty()) return
        SessionManager.startSession(prefs, blockers, talismanId = tagId)
    }

    /** Step 3: the single-enchantment key folds into ACTIVE_BLOCKERS. */
    private fun migrateActiveBlocker(prefs: SharedPreferences) {
        val single = prefs.getString(LegacyKeys.ACTIVE_BLOCKER, null)
        prefs.edit {
            if (single != null && !prefs.contains(Constants.PrefsKeys.ACTIVE_BLOCKERS)) {
                putString(Constants.PrefsKeys.ACTIVE_BLOCKERS, Gson().toJson(listOf(single)))
            }
            remove(LegacyKeys.ACTIVE_BLOCKER)
        }
    }

    private fun activeBlockerNames(prefs: SharedPreferences): List<String> =
        parse(prefs, Constants.PrefsKeys.ACTIVE_BLOCKERS)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { e -> e.takeIf { it.isJsonPrimitive }?.asString }
            ?.ifEmpty { null }
            ?: listOfNotNull(prefs.getString(LegacyKeys.ACTIVE_BLOCKER, null))

    private fun parse(prefs: SharedPreferences, key: String): JsonElement? = try {
        prefs.getString(key, null)?.let { JsonParser.parseString(it) }
    } catch (e: Exception) {
        Log.w(TAG, "Unreadable $key, skipped", e)
        null
    }

    private fun JsonObject.str(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject.bool(name: String): Boolean =
        get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() } == true

    private fun JsonObject.int(name: String): Int =
        get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() } ?: 0

    private fun JsonObject.long(name: String): Long =
        get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() } ?: 0L

    @Synchronized
    fun run(prefs: SharedPreferences) {
        val from = prefs.getInt(Constants.PrefsKeys.SCHEMA_VERSION, 0)
        if (from >= steps.size) return
        for (version in from until steps.size) {
            steps[version](prefs)
            // commit, not apply: a later step must never run over data whose
            // earlier step isn't on disk yet.
            prefs.edit(commit = true) { putInt(Constants.PrefsKeys.SCHEMA_VERSION, version + 1) }
            Log.i(TAG, "Migrated prefs to schema ${version + 1}")
        }
    }
}
