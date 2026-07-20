package com.infinicada.focuspocus.limit

import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.AppTimeLimitManager
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.PrefsHelper
import com.infinicada.focuspocus.model.AppTimeLimit
import com.infinicada.focuspocus.model.PactGroup

/**
 * One queued modification to an existing pact. Exactly one of [packageName]
 * (a per-app pact config) or [blockerName] (a pact circle) identifies the
 * target; [newConfig]/[newGroup] carry the requested terms, and both null
 * means the request is a removal.
 */
data class PendingPactRevision(
    val packageName: String? = null,
    val blockerName: String? = null,
    val newConfig: AppTimeLimit? = null,
    val newGroup: PactGroup? = null,
    val requestedAtMillis: Long = 0L,
    val appliesAtMillis: Long = 0L
) {
    val isRemoval: Boolean
        get() = newConfig == null && newGroup == null
}

/**
 * The pact cooling-off ledger: a pact is a commitment, so loosening or breaking
 * it cannot be an impulse action. Any save or delete that targets an app or
 * circle whose *currently enforced* guard is a pact is queued here instead of
 * hitting the stores, and [applyDueRevisions] writes it through once
 * [REVISION_DELAY_MS] (24 h) has passed. Until then the existing terms stay
 * fully enforced.
 *
 * Creating a brand-new guard, and any edit to a ward, stays immediate —
 * raising protection should never have to wait.
 *
 * One revision per target: re-queuing replaces the previous request and
 * restarts the clock; cancelling simply keeps the current terms, so a cancel
 * never needs a cooling-off of its own.
 */
class PactRevisionManager(
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    private val tag = "PactRevisionManager"

    fun getRevisions(): List<PendingPactRevision> {
        val type = object : TypeToken<List<PendingPactRevision>>() {}.type
        val revisions = PrefsHelper.load(prefs, gson, Constants.PrefsKeys.PACT_PENDING_REVISIONS, type)
            ?: emptyList<PendingPactRevision>()
        // Same defence as PactManager.getGroups: Gson fills fields via Unsafe,
        // so a record from a corrupted store can carry no target at all. Such
        // an entry can neither be shown nor applied — drop it.
        return revisions.filterNotNull()
            .filter { it.packageName != null || it.blockerName != null }
    }

    fun revisionForApp(packageName: String): PendingPactRevision? =
        getRevisions().firstOrNull { it.packageName == packageName }

    fun revisionForCircle(blockerName: String): PendingPactRevision? =
        getRevisions().firstOrNull { it.packageName == null && it.blockerName == blockerName }

    /**
     * Queues [newConfig] (null = removal) for [packageName], replacing any
     * previous request for the same app and restarting its 24 h clock.
     */
    fun queueAppRevision(
        packageName: String,
        newConfig: AppTimeLimit?,
        now: Long = System.currentTimeMillis()
    ): PendingPactRevision {
        val revision = PendingPactRevision(
            packageName = packageName,
            newConfig = newConfig,
            requestedAtMillis = now,
            appliesAtMillis = now + REVISION_DELAY_MS
        )
        saveRevisions(getRevisions().filter { it.packageName != packageName } + revision)
        Log.d(tag, "Pact revision queued for $packageName (removal=${revision.isRemoval})")
        return revision
    }

    /**
     * Queues [newGroup] (null = removal) for the circle on [blockerName],
     * replacing any previous request for the same circle and restarting its
     * 24 h clock.
     */
    fun queueCircleRevision(
        blockerName: String,
        newGroup: PactGroup?,
        now: Long = System.currentTimeMillis()
    ): PendingPactRevision {
        val revision = PendingPactRevision(
            blockerName = blockerName,
            newGroup = newGroup,
            requestedAtMillis = now,
            appliesAtMillis = now + REVISION_DELAY_MS
        )
        saveRevisions(
            getRevisions().filter { it.packageName != null || it.blockerName != blockerName } + revision
        )
        Log.d(tag, "Pact revision queued for circle $blockerName (removal=${revision.isRemoval})")
        return revision
    }

    /** Drops the pending request for [packageName]: the current terms stand. */
    fun cancelAppRevision(packageName: String) {
        val all = getRevisions()
        val remaining = all.filter { it.packageName != packageName }
        if (remaining.size != all.size) {
            saveRevisions(remaining)
            Log.d(tag, "Pact revision cancelled for $packageName")
        }
    }

    /** Drops the pending request for the circle on [blockerName]. */
    fun cancelCircleRevision(blockerName: String) {
        val all = getRevisions()
        val remaining = all.filter { it.packageName != null || it.blockerName != blockerName }
        if (remaining.size != all.size) {
            saveRevisions(remaining)
            Log.d(tag, "Pact revision cancelled for circle $blockerName")
        }
    }

    /**
     * Writes every revision whose 24 h has elapsed through to the real stores
     * (config map and pact groups) and removes it from the queue. Returns true
     * when anything was applied so callers can refresh caches, resync Warden
     * greying, and re-read their snapshots. Safe to call from both the UI and
     * the enforcement service — whoever gets there first applies.
     */
    fun applyDueRevisions(now: Long = System.currentTimeMillis()): Boolean {
        val all = getRevisions()
        val due = all.filter { it.appliesAtMillis <= now }
        if (due.isEmpty()) return false

        val configRevisions = due.filter { it.packageName != null }
        if (configRevisions.isNotEmpty()) {
            val configs = AppTimeLimitManager.getTimeLimitConfigs(prefs, gson).toMutableMap()
            configRevisions.forEach { revision ->
                val pkg = revision.packageName ?: return@forEach
                val config = revision.newConfig
                if (config == null) {
                    configs.remove(pkg)
                } else if (pkg in configs || configs.size < Constants.MAX_APP_TIME_LIMITS) {
                    configs[pkg] = config
                } else {
                    // A queued creation (circle member gaining an explicit
                    // config) can race the store filling up in the meantime.
                    Log.w(tag, "Dropping due revision for $pkg: config store is full")
                }
            }
            AppTimeLimitManager.saveTimeLimitConfigs(prefs, gson, configs)
        }

        val pactManager = PactManager(prefs, gson)
        due.filter { it.packageName == null && it.blockerName != null }.forEach { revision ->
            val group = revision.newGroup
            if (group != null) {
                pactManager.saveGroup(group)
            } else {
                pactManager.deleteGroup(revision.blockerName!!)
            }
        }

        saveRevisions(all - due.toSet())
        Log.d(tag, "Applied ${due.size} due pact revision(s)")
        return true
    }

    private fun saveRevisions(revisions: List<PendingPactRevision>) {
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.PACT_PENDING_REVISIONS, revisions)
    }

    companion object {
        /** How long a queued pact modification waits before taking effect. */
        const val REVISION_DELAY_MS = 24L * 60 * 60 * 1000

        /**
         * True when a save or delete targeting [packageName] must be queued:
         * the guard currently enforced for it is a pact, either an explicit
         * pact-style config or live membership in a pact circle (an explicit
         * config would override the circle, so creating one is a modification
         * of that pact too).
         */
        fun requiresDelayForApp(
            packageName: String,
            configs: Map<String, AppTimeLimit>,
            groups: List<PactGroup>,
            blockers: List<Blocker>
        ): Boolean = packageName in GuardStatus.pactGatedPackages(configs, groups, blockers)

        /** True when a save or delete of the circle on [blockerName] must be queued. */
        fun requiresDelayForCircle(blockerName: String, groups: List<PactGroup>): Boolean =
            groups.any { it.blockerName == blockerName }
    }
}
