package com.infinicada.focuspocus.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.infinicada.focuspocus.AppTimeLimitManager
import com.infinicada.focuspocus.AppUtils
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.DeviceOwnerManager
import com.infinicada.focuspocus.FocusPocusApplication
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.UsageStatsHelper
import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.limit.GuardLiveState
import com.infinicada.focuspocus.limit.GuardStatus
import com.infinicada.focuspocus.limit.ManaWell
import com.infinicada.focuspocus.limit.OpenReflexTracker
import com.infinicada.focuspocus.limit.PactManager
import com.infinicada.focuspocus.limit.PactRevisionManager
import com.infinicada.focuspocus.limit.PendingPactRevision
import com.infinicada.focuspocus.limit.SessionCooldownManager
import com.infinicada.focuspocus.limit.WellMath
import com.infinicada.focuspocus.limit.WellSnapshot
import com.infinicada.focuspocus.limit.WellUnlocks
import com.infinicada.focuspocus.model.PactGroup
import com.infinicada.focuspocus.data.BlockerListRepository
import com.infinicada.focuspocus.data.ConditionalUnlockRepository
import com.infinicada.focuspocus.data.InsightsRepository
import com.infinicada.focuspocus.data.PresetRepository
import com.infinicada.focuspocus.data.SettingsRepository
import com.infinicada.focuspocus.data.ScheduleRepository
import com.infinicada.focuspocus.data.TalismanRepository
import com.infinicada.focuspocus.model.AppInfo
import com.infinicada.focuspocus.model.AppTimeLimit
import com.infinicada.focuspocus.model.ConditionalUnlock
import com.infinicada.focuspocus.model.FocusPreset
import com.infinicada.focuspocus.model.Schedule
import com.infinicada.focuspocus.navigation.PactsRoute
import com.infinicada.focuspocus.navigation.SpellbookRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SpellbookViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as FocusPocusApplication).container

    private val appPrefs =
        application.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private val openReflexTracker = OpenReflexTracker(appPrefs, Gson())
    private val pactManager = PactManager(appPrefs, Gson())
    private val sessionCooldownManager = SessionCooldownManager(appPrefs, Gson())
    private val pactRevisionManager = PactRevisionManager(appPrefs, Gson())

    init {
        // Write through any pact revision that came due while the app was
        // closed, before the flows below snapshot the stores. (Init blocks and
        // property initializers run in declaration order.) The suspension sync
        // itself doesn't feed the snapshots, so it can leave the main thread.
        if (pactRevisionManager.applyDueRevisions()) {
            syncWardenGreying()
        }
    }

    /** Today's open/reflex counters per package, for the Pacts dashboard. */
    fun getTodayOpenStats(): Map<String, AppOpenStats> = openReflexTracker.getAllStats()

    /**
     * Live enforcement snapshot for every guarded app — active pact allowances,
     * seals, and foreground minutes (queried once for all packages, and only
     * when some guard actually carries a daily limit). Read-only: cooldowns are
     * peeked, never pruned, so the UI can't race the service's writes.
     */
    fun getGuardLiveState(): Map<String, GuardLiveState> {
        val now = System.currentTimeMillis()
        val configs = _appTimeLimitConfigs.value
        val groups = _pactGroups.value
        val groupMembers = groups.flatMap { group ->
            _blockerLists.value.find { it.name == group.blockerName }?.effectiveApps ?: emptySet()
        }
        val cooldowns = sessionCooldownManager.peekActiveCooldowns(now)
        val allowances = pactManager.getActiveAllowances(now)
        val anyDailyLimit = configs.values.any { it.dailyLimitMinutes > 0 } ||
            groups.any { it.dailyLimitMinutes > 0 }
        val usedToday = if (anyDailyLimit) {
            AppTimeLimitManager.getAllUsedMinutesToday(getApplication())
        } else {
            emptyMap()
        }
        return (configs.keys + groupMembers).associateWith { pkg ->
            GuardLiveState(
                allowanceExpiryMillis = allowances[pkg],
                cooldownExpiryMillis = cooldowns[pkg]?.cooldownExpiryMillis,
                usedMinutesToday = usedToday[pkg] ?: 0
            )
        }
    }

    /**
     * The Mana Well as of now — terms, level and today's draws. A handful of
     * primitive prefs; the dashboard takes it alongside the guard snapshot
     * and lets the level keep refilling on its own clock in between.
     */
    fun getWellSnapshot(): WellSnapshot = ManaWell.snapshot(appPrefs)

    /**
     * Pays any conditional-unlock rule that is satisfied, covers a Well-bound
     * pact app, and hasn't paid yet today (WellUnlocks) — the dashboard's
     * twin of the service's check at the pact gate, so the Well card and the
     * request dialog count minutes earned even when the launcher path never
     * runs (Warden greying). Returns the minutes added. Walks the day's
     * usage events when a rule is still pending, so call it off the main
     * thread.
     */
    fun creditWellUnlocks(): Int {
        val rules = _conditionalUnlocks.value
        if (rules.isEmpty()) return 0
        val wellBound = GuardStatus.pactGatedConfigs(
            _appTimeLimitConfigs.value, _pactGroups.value, _blockerLists.value
        ).filterValues { !it.wellExempt }.keys
        return WellUnlocks.creditSatisfied(
            prefs = appPrefs,
            rules = rules,
            isWellBound = { it in wellBound },
            usedMillisToday = { UsageStatsHelper.getPackageUsageToday(getApplication(), it) }
        )
    }

    private val _pactGroups = MutableStateFlow(pactManager.getGroups())
    val pactGroups: StateFlow<List<PactGroup>> = _pactGroups.asStateFlow()

    private val _pendingPactRevisions = MutableStateFlow(pactRevisionManager.getRevisions())
    val pendingPactRevisions: StateFlow<List<PendingPactRevision>> =
        _pendingPactRevisions.asStateFlow()

    /**
     * Saving over an existing circle is a pact modification, so it queues for
     * 24 h instead of applying; the circle's current terms stay enforced until
     * then. A brand-new circle, or terms identical to the enforced ones
     * (which just withdraws any pending request), applies immediately.
     */
    fun savePactGroup(group: PactGroup) {
        val existing = _pactGroups.value.find { it.blockerName == group.blockerName }
        if (existing != null && existing != group) {
            pactRevisionManager.queueCircleRevision(group.blockerName, group)
            onRevisionQueued(removal = false)
            return
        }
        if (existing == group) {
            // Re-confirming the enforced terms = "keep current terms".
            pactRevisionManager.cancelCircleRevision(group.blockerName)
            _pendingPactRevisions.value = pactRevisionManager.getRevisions()
            _dataVersion.value++
            return
        }
        pactManager.saveGroup(group)
        _pactGroups.value = pactManager.getGroups()
        _dataVersion.value++
        syncWardenGreying()
    }

    /** Removing a circle is always a pact modification — it queues for 24 h. */
    fun deletePactGroup(blockerName: String) {
        if (_pactGroups.value.none { it.blockerName == blockerName }) return
        pactRevisionManager.queueCircleRevision(blockerName, null)
        onRevisionQueued(removal = true)
    }

    fun cancelCirclePactRevision(blockerName: String) {
        pactRevisionManager.cancelCircleRevision(blockerName)
        _pendingPactRevisions.value = pactRevisionManager.getRevisions()
        _dataVersion.value++
    }

    fun cancelAppPactRevision(packageName: String) {
        pactRevisionManager.cancelAppRevision(packageName)
        _pendingPactRevisions.value = pactRevisionManager.getRevisions()
        _dataVersion.value++
    }

    /**
     * Writes through any pact revision whose 24 h has elapsed and refreshes
     * every snapshot derived from the stores. Called from the dashboard's
     * minute tick so a due change lands without waiting for a restart; the
     * accessibility service runs the same reconciliation for enforcement.
     */
    fun applyDuePactRevisions() {
        if (!pactRevisionManager.applyDueRevisions()) return
        _appTimeLimitConfigs.value = insightsRepo.getAppTimeLimitConfigs()
        _appTimeLimits.value = _appTimeLimitConfigs.value.mapValues { (_, v) -> v.dailyLimitMinutes }
        _pactGroups.value = pactManager.getGroups()
        _pendingPactRevisions.value = pactRevisionManager.getRevisions()
        _dataVersion.value++
        syncWardenGreying()
    }

    private fun onRevisionQueued(removal: Boolean) {
        _pendingPactRevisions.value = pactRevisionManager.getRevisions()
        _dataVersion.value++
        Toast.makeText(getApplication(), getApplication<Application>().getString(
            if (removal) R.string.pact_revision_queued_removal_toast
            else R.string.pact_revision_queued_change_toast
        ), Toast.LENGTH_LONG).show()
    }

    /**
     * Reconciles Warden-mode greying after any edit that changes which apps are
     * pact-gated (configs, groups, or the enchantment membership behind a
     * circle) — a newly pact'd app greys out immediately, a released one
     * un-greys. No-op when the device isn't provisioned. Runs off the main
     * thread: on a provisioned device the sync enumerates launchable packages
     * and queries usage stats, which would jank every save tap.
     */
    private fun syncWardenGreying() {
        viewModelScope.launch(Dispatchers.Default) {
            DeviceOwnerManager.syncSuspensions(getApplication())
        }
    }

    /**
     * The Pacts dashboard's request-time flow: the in-app counterpart of the
     * pact overlay, for apps the OS itself refuses to open under Warden greying.
     * Grants the allowance, lifts the suspension, and opens the app.
     *
     * Mirrors the enforcement layer's lapse→seal conversion first, so a stale
     * dashboard (or a dead accessibility service) can't chain a new pact past a
     * seal that should be running. Returns false if the app turned out to be
     * sealed — the dashboard refreshes to show the seal instead.
     */
    fun requestPactTime(packageName: String, minutes: Int): Boolean {
        val now = System.currentTimeMillis()
        pactManager.takeLapsedAllowance(packageName, now)?.let { lapsedExpiry ->
            effectivePactConfig(packageName)?.let { config ->
                sessionCooldownManager.startSeals(
                    listOfNotNull(
                        GuardStatus.lapseSealRequest(
                            packageName = packageName,
                            config = config,
                            lapsedExpiry = lapsedExpiry,
                            wellEnabled = ManaWell.isEnabled(appPrefs, now),
                            groupSealEnabled = settingsRepo.getGroupSealEnabled(),
                            groupSealMinutes = settingsRepo.getGroupSealDurationMinutes()
                        )
                    )
                )
            }
        }
        val seal = sessionCooldownManager.getCooldownState(packageName, now)
        if (seal != null) {
            Toast.makeText(getApplication(), getApplication<Application>().getString(
                R.string.pacts_request_sealed_toast,
                GuardStatus.minutesUntil(seal.cooldownExpiryMillis, now)
            ), Toast.LENGTH_SHORT).show()
            _dataVersion.value++
            return false
        }
        // Mana Well: a Well-bound pact is paid from the shared pool, and an
        // allowance the pool can't cover is never granted. The dialog only
        // offers what the Well held when it opened, so a refusal here means
        // it was drained in between (the launcher overlay, a second request).
        val wellBound = effectivePactConfig(packageName)?.let { ManaWell.isBound(appPrefs, it, now) } == true
        if (wellBound && !ManaWell.tryDraw(appPrefs, minutes, now)) {
            Toast.makeText(
                getApplication(), R.string.well_draw_refused_toast, Toast.LENGTH_LONG
            ).show()
            _dataVersion.value++
            return false
        }
        pactManager.grantAllowance(packageName, minutes, now)
        _dataVersion.value++
        // Under Warden greying the OS refuses to open a still-suspended app,
        // so the launch must chain after the sync completes — not go through
        // the fire-and-forget syncWardenGreying.
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                DeviceOwnerManager.syncSuspensions(getApplication())
            }
            launchApp(packageName)
        }
        return true
    }

    /**
     * What ending pacts early did: apps newly sealed, running pacts ended
     * without a seal, and whole minutes returned to the Mana Well.
     */
    data class SealResult(
        val sealedCount: Int,
        val refundedMinutes: Int,
        val endedUnsealedCount: Int = 0
    )

    /**
     * Panic action: seals every pact-gated app (explicit pacts and live circle
     * members) right now, revoking any running allowances. Apps already sealed
     * keep their current seal. Uses the non-escalating panic seal — choosing
     * protection is not an offence. Seals pacts paid from the Mana Well too:
     * this is the one place the user asks for the lock itself.
     */
    fun sealAllPacts(): SealResult = endPacts(
        GuardStatus.pactGatedConfigs(
            _appTimeLimitConfigs.value, _pactGroups.value, _blockerLists.value
        ),
        alwaysSeal = true
    )

    /**
     * "I'm done": ends [packageName]'s running pact now. Without this a pact
     * could only run out its clock — and a pact paid from the Mana Well would
     * be worth using to the last minute. Ending early gives the unused time
     * back instead.
     *
     * What follows is what running out would have brought
     * ([GuardStatus.sealsWhenPactEnds]): a pact that seals is sealed, on the
     * panic seal's terms; a pact paid from the Well just closes — otherwise
     * handing minutes back would lock the app while spending them all would
     * not.
     */
    fun endPactNow(packageName: String): SealResult = endPacts(
        listOfNotNull(effectivePactConfig(packageName)?.let { packageName to it }).toMap(),
        alwaysSeal = false
    )

    /** [endPactNow] for a circle: ends every member's running pact. */
    fun endCirclePactsNow(blockerName: String): SealResult {
        val group = _pactGroups.value.find { it.blockerName == blockerName }
            ?: return SealResult(0, 0)
        val running = pactManager.getActiveAllowances().keys
        return endPacts(
            GuardStatus.circleMemberPackages(group, _blockerLists.value, _appTimeLimitConfigs.value)
                .filter { it in running }
                .associateWith { group.toAppTimeLimit(it) },
            alwaysSeal = false
        )
    }

    /**
     * Revokes the running allowances of [targets] (package → governing pact
     * config) and seals them — all of them when [alwaysSeal], otherwise only
     * those whose pact ends in a seal anyway — in one write per store. Time
     * left on allowances that were paid from the Mana Well goes back into it
     * ([WellMath.refundableMillis]): which allowances those are is read off
     * the governing config, so the rare allowance that predates the Well
     * being switched on is refunded too — bounded by the capacity, like
     * every refund.
     */
    private fun endPacts(targets: Map<String, AppTimeLimit>, alwaysSeal: Boolean): SealResult {
        if (targets.isEmpty()) return SealResult(0, 0)
        val now = System.currentTimeMillis()
        val active = pactManager.getActiveAllowances(now)
        val wellEnabled = ManaWell.isEnabled(appPrefs, now)
        val refundMillis = if (wellEnabled) {
            WellMath.refundableMillis(
                revoked = targets.filterValues { !it.wellExempt }.keys.mapNotNull { active[it] },
                stillRunning = (active - targets.keys).values,
                now = now
            )
        } else {
            0L
        }
        pactManager.revokeAllowances(targets.keys)
        val alreadySealed = sessionCooldownManager.peekActiveCooldowns(now).keys
        val groupSealEnabled = settingsRepo.getGroupSealEnabled()
        val requests = targets
            .filter { (pkg, config) ->
                pkg !in alreadySealed &&
                    (alwaysSeal || GuardStatus.sealsWhenPactEnds(config, wellEnabled, groupSealEnabled))
            }
            .map { (pkg, config) ->
                SessionCooldownManager.SealRequest(pkg, config, now, countsAsOffence = false)
            }
        sessionCooldownManager.startSeals(requests)
        ManaWell.refund(appPrefs, refundMillis, now)
        syncWardenGreying()
        _dataVersion.value++
        val sealed = requests.mapTo(mutableSetOf()) { it.packageName }
        return SealResult(
            sealedCount = requests.size,
            refundedMinutes = (refundMillis / WellMath.MINUTE_MS).toInt(),
            endedUnsealedCount = targets.keys.count { it in active && it !in sealed && it !in alreadySealed }
        )
    }

    /**
     * Group Seal mode's grant action: opens every pact-gated app that isn't
     * currently sealed for the same shared window (the dashboard's fallback
     * for the primary entry point — tapping a pact app from the launcher —
     * which the service's group-aware overlay handles directly). No partial
     * per-app requesting exists alongside this while the mode is on, or the
     * app-hopping gap it closes reopens. Returns how many apps were opened,
     * or null when the Mana Well refused to pay for the window.
     *
     * With the Well on, the window is a single draw of at most what the Well
     * holds, and the group is the Well-bound apps only (exempt pacts keep
     * their own per-app requests).
     *
     * Suspending because the eligibility snapshot reads the stores and (when
     * any guard carries a daily limit) walks the day's usage-stats event
     * stream — the same work the dashboard's own refresh deliberately takes
     * off the main thread. The state reads below are re-done on the caller's
     * thread only after the heavy part is finished.
     */
    suspend fun groupOpenPacts(): Int? {
        val now = System.currentTimeMillis()
        val well = ManaWell.snapshot(appPrefs, now)
        val minutes = settingsRepo.getGroupSealOpenWindowMinutes().let { window ->
            if (well.enabled) minOf(window, well.availableMinutesAt(now)) else window
        }
        val targets = withContext(Dispatchers.Default) {
            GuardStatus.groupOpenEligiblePackages(
                _appTimeLimitConfigs.value,
                _pactGroups.value,
                _blockerLists.value,
                getGuardLiveState(),
                now,
                wellEnabled = well.enabled
            )
        }
        if (targets.isEmpty()) return 0
        if (well.enabled && !ManaWell.tryDraw(appPrefs, minutes, now)) {
            Toast.makeText(
                getApplication(), R.string.well_draw_refused_toast, Toast.LENGTH_LONG
            ).show()
            _dataVersion.value++
            return null
        }
        // One store write for the shared window, not one per app.
        pactManager.grantAllowances(targets, minutes, now)
        syncWardenGreying()
        _dataVersion.value++
        return targets.size
    }

    /** The pact settings governing [packageName] — resolvePactConfig's precedence. */
    private fun effectivePactConfig(packageName: String): AppTimeLimit? {
        val configs = _appTimeLimitConfigs.value
        configs[packageName]?.let { return if (it.pactModeEnabled) it else null }
        return _pactGroups.value
            .firstOrNull { packageName in GuardStatus.circleMemberPackages(it, _blockerLists.value, configs) }
            ?.toAppTimeLimit(packageName)
    }

    private fun launchApp(packageName: String) {
        val app = getApplication<Application>()
        try {
            app.packageManager.getLaunchIntentForPackage(packageName)?.let {
                it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(it)
            }
        } catch (e: Exception) {
            android.util.Log.e("SpellbookViewModel", "Error launching $packageName", e)
        }
    }
    private val blockerRepo: BlockerListRepository = container.blockers
    private val scheduleRepo: ScheduleRepository = container.schedules
    private val presetRepo: PresetRepository = container.presets
    private val talismanRepo: TalismanRepository = container.talismans
    private val insightsRepo: InsightsRepository = container.insights
    private val conditionalUnlockRepo: ConditionalUnlockRepository = container.conditionalUnlocks
    private val settingsRepo: SettingsRepository = container.settings

    private val _blockerLists = MutableStateFlow(blockerRepo.getBlockers())
    val blockerLists: StateFlow<List<Blocker>> = _blockerLists.asStateFlow()

    private val _schedules = MutableStateFlow(scheduleRepo.getSchedules())
    val schedules: StateFlow<List<Schedule>> = _schedules.asStateFlow()

    private val _focusPresets = MutableStateFlow(presetRepo.getPresets())
    val focusPresets: StateFlow<List<FocusPreset>> = _focusPresets.asStateFlow()

    private val _namedTags = MutableStateFlow(talismanRepo.getNamedTags())
    val namedTags: StateFlow<List<NamedTag>> = _namedTags.asStateFlow()

    private val _appTimeLimitConfigs = MutableStateFlow(insightsRepo.getAppTimeLimitConfigs())
    val appTimeLimitConfigs: StateFlow<Map<String, AppTimeLimit>> = _appTimeLimitConfigs.asStateFlow()

    // Derived flat map (packageName → daily minutes) for screens that don't need cooldown info.
    private val _appTimeLimits = MutableStateFlow(
        _appTimeLimitConfigs.value.mapValues { (_, v) -> v.dailyLimitMinutes }
    )
    val appTimeLimits: StateFlow<Map<String, Int>> = _appTimeLimits.asStateFlow()

    private val _conditionalUnlocks = MutableStateFlow(conditionalUnlockRepo.getConditionalUnlocks())
    val conditionalUnlocks: StateFlow<List<ConditionalUnlock>> = _conditionalUnlocks.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    // Spellbook navigation
    private val _spellbookRoute = MutableStateFlow<SpellbookRoute>(SpellbookRoute.Overview)
    val spellbookRoute: StateFlow<SpellbookRoute> = _spellbookRoute.asStateFlow()

    // Pacts (HOME) tab navigation: dashboard overview vs. the guard editor
    private val _pactsRoute = MutableStateFlow<PactsRoute>(PactsRoute.Overview)
    val pactsRoute: StateFlow<PactsRoute> = _pactsRoute.asStateFlow()

    private val _selectedBlocker = MutableStateFlow<Blocker?>(null)
    val selectedBlocker: StateFlow<Blocker?> = _selectedBlocker.asStateFlow()

    // Data version counter — incremented on any data mutation
    private val _dataVersion = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _dataVersion.asStateFlow()

    init {
        cleanupOrphanedData()
        loadInstalledApps()
    }

    private fun cleanupOrphanedData() {
        val blockerNames = _blockerLists.value.map { it.name }.toSet()
        val talismanIds = _namedTags.value.map { it.id }.toSet()
        _schedules.value = scheduleRepo.cleanupOrphanedSchedules(_schedules.value, blockerNames, talismanIds)
        _focusPresets.value = presetRepo.cleanupOrphanedPresets(_focusPresets.value, talismanIds)
    }

    /**
     * Loads every launchable app for the pickers, minus stock system apps
     * (see [AppUtils.isStockSystemApp]) — OS plumbing the user never chose and
     * shouldn't have to wade through or accidentally block. Whitelist
     * enforcement skips the same set, keeping "what the picker offers" and
     * "what a whitelist may block" in lockstep.
     *
     * Deliberate broad package visibility: an app blocker's whole purpose is
     * letting the user choose from everything launchable on the device, and the
     * manifest's `<queries>` ACTION_MAIN filter grants exactly that without
     * QUERY_ALL_PACKAGES. Narrowing it would hide the very apps a user wants to
     * guard, so the lint warning is suppressed rather than worked around.
     */
    @Suppress("QueryPermissionsNeeded")
    fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter {
                        try {
                            !AppUtils.isStockSystemApp(it) &&
                                pm.getLaunchIntentForPackage(it.packageName) != null
                        } catch (_: Exception) {
                            // The framework can throw NPE ("class name is null") for packages in
                            // a transient state (mid-update/uninstall); skip them instead of crashing.
                            false
                        }
                    }
                    .map { AppInfo(name = it.loadLabel(pm).toString(), packageName = it.packageName, category = it.category) }
                    .sortedBy { it.name.lowercase() }
            }
            if (apps != _installedApps.value) {
                _installedApps.value = apps
            }
        }
    }

    // Navigation
    fun navigateTo(route: SpellbookRoute) {
        _spellbookRoute.value = route
    }

    fun navigateToPacts(route: PactsRoute) {
        _pactsRoute.value = route
    }

    fun handlePactsBack(): Boolean {
        if (_pactsRoute.value is PactsRoute.Overview) return false
        _pactsRoute.value = PactsRoute.Overview
        return true
    }

    fun setSelectedBlocker(blocker: Blocker?) {
        _selectedBlocker.value = blocker
    }

    fun handleBack(): Boolean {
        val current = _spellbookRoute.value
        if (current is SpellbookRoute.Overview) return false
        _spellbookRoute.value = when (current) {
            is SpellbookRoute.CreateEnchantment, is SpellbookRoute.EditEnchantment -> SpellbookRoute.EnchantmentsList
            is SpellbookRoute.CreateQuickSpell, is SpellbookRoute.EditQuickSpell -> SpellbookRoute.QuickSpellsList
            is SpellbookRoute.CreateRitual, is SpellbookRoute.EditRitual -> SpellbookRoute.RitualsList
            else -> SpellbookRoute.Overview
        }
        return true
    }

    // CRUD operations
    fun saveBlocker(blocker: Blocker) {
        if (!blockerRepo.saveBlocker(blocker)) {
            Toast.makeText(getApplication(), getApplication<Application>().getString(
                R.string.toast_max_enchantments, com.infinicada.focuspocus.Constants.MAX_BLOCKERS
            ), Toast.LENGTH_SHORT).show()
            return
        }
        _blockerLists.value = blockerRepo.getBlockers()
        _dataVersion.value++
        // Editing an enchantment's app list changes its pact circle's membership.
        syncWardenGreying()
    }

    fun deleteBlocker(blocker: Blocker) {
        _blockerLists.value = blockerRepo.deleteBlocker(blocker)
        // A pact group is bound to its enchantment by name; deleting the
        // enchantment would leave the group behind, silently gating nothing.
        // This orphan cleanup is immediate (the circle already gates nothing),
        // and any queued revision for it would only resurrect a dead circle.
        if (pactManager.getGroups().any { it.blockerName == blocker.name }) {
            pactManager.deleteGroup(blocker.name)
            _pactGroups.value = pactManager.getGroups()
        }
        pactRevisionManager.cancelCircleRevision(blocker.name)
        _pendingPactRevisions.value = pactRevisionManager.getRevisions()
        _dataVersion.value++
        syncWardenGreying()
    }

    fun saveSchedule(schedule: Schedule) {
        if (!scheduleRepo.saveSchedule(schedule, _schedules.value)) {
            Toast.makeText(getApplication(), getApplication<Application>().getString(
                R.string.toast_max_rituals, com.infinicada.focuspocus.Constants.MAX_SCHEDULES
            ), Toast.LENGTH_SHORT).show()
            return
        }
        _schedules.value = scheduleRepo.getSchedules()
        _dataVersion.value++
        // The next-transition alarm may now point at the wrong moment.
        com.infinicada.focuspocus.RitualAlarmScheduler.scheduleNext(getApplication())
    }

    fun deleteSchedule(schedule: Schedule) {
        _schedules.value = scheduleRepo.deleteSchedule(schedule, _schedules.value)
        // Cancel notifications
        val notificationManager = getApplication<Application>()
            .getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        if (notificationManager != null) {
            notificationManager.cancel(
                com.infinicada.focuspocus.RitualNotifier.notificationId(schedule.id, isEndNotification = false)
            )
            notificationManager.cancel(
                com.infinicada.focuspocus.RitualNotifier.notificationId(schedule.id, isEndNotification = true)
            )
        }
        _dataVersion.value++
        com.infinicada.focuspocus.RitualAlarmScheduler.scheduleNext(getApplication())
    }

    fun saveFocusPreset(preset: FocusPreset) {
        if (!presetRepo.savePreset(preset, _focusPresets.value)) {
            Toast.makeText(getApplication(), getApplication<Application>().getString(
                R.string.toast_max_quick_spells, com.infinicada.focuspocus.Constants.MAX_PRESETS
            ), Toast.LENGTH_SHORT).show()
            return
        }
        _focusPresets.value = presetRepo.getPresets()
        _dataVersion.value++
    }

    fun deleteFocusPreset(preset: FocusPreset) {
        _focusPresets.value = presetRepo.deletePreset(preset, _focusPresets.value)
        _dataVersion.value++
    }

    fun saveNamedTag(tagId: String, name: String) {
        val tag = NamedTag(tagId, name)
        if (!talismanRepo.saveNamedTag(tag, _namedTags.value)) {
            Toast.makeText(getApplication(), getApplication<Application>().getString(
                R.string.toast_max_talismans, com.infinicada.focuspocus.Constants.MAX_NAMED_TAGS
            ), Toast.LENGTH_SHORT).show()
            return
        }
        _namedTags.value = talismanRepo.getNamedTags()
        _dataVersion.value++
    }

    fun deleteNamedTag(tag: NamedTag) {
        _namedTags.value = talismanRepo.deleteNamedTag(tag, _namedTags.value)
        _dataVersion.value++
    }

    /**
     * Onboarding: seal [packages] behind the default pact (up to 15 minutes per
     * pact, 30-minute seal, no daily backstop). Each is an ordinary per-app
     * config, so the guard editor tunes them like any other pact.
     */
    fun createDefaultPacts(packages: List<String>) {
        packages.take(com.infinicada.focuspocus.Constants.MAX_APP_TIME_LIMITS).forEach { pkg ->
            saveAppTimeLimitConfig(
                AppTimeLimit(
                    packageName = pkg,
                    dailyLimitMinutes = 0,
                    sessionLimitMinutes = 0,
                    cooldownMinutes = 30,
                    pactModeEnabled = true,
                    pactMaxMinutes = PactManager.DEFAULT_MAX_MINUTES
                )
            )
        }
    }

    /**
     * Saving over an app whose enforced guard is a pact — an explicit
     * pact-style config, or live membership in a pact circle that this config
     * would override — is a pact modification, so it queues for 24 h. Terms
     * identical to the enforced config just withdraw any pending request.
     * Everything else (new guards, ward edits) applies immediately.
     */
    fun saveAppTimeLimitConfig(config: AppTimeLimit) {
        val existing = _appTimeLimitConfigs.value[config.packageName]
        val pactGoverned = PactRevisionManager.requiresDelayForApp(
            config.packageName, _appTimeLimitConfigs.value, _pactGroups.value, _blockerLists.value
        )
        if (pactGoverned && existing != config) {
            pactRevisionManager.queueAppRevision(config.packageName, config)
            onRevisionQueued(removal = false)
            return
        }
        if (pactGoverned && existing == config) {
            pactRevisionManager.cancelAppRevision(config.packageName)
            _pendingPactRevisions.value = pactRevisionManager.getRevisions()
            _dataVersion.value++
            return
        }
        if (!insightsRepo.saveAppTimeLimitConfig(config, _appTimeLimitConfigs.value)) {
            Toast.makeText(getApplication(), getApplication<Application>().getString(
                R.string.toast_max_time_limits, com.infinicada.focuspocus.Constants.MAX_APP_TIME_LIMITS
            ), Toast.LENGTH_SHORT).show()
            return
        }
        // An immediate save supersedes any request queued while the app was
        // still pact-governed (e.g. its circle has since dissolved) — a stale
        // revision firing later must not clobber this newer state.
        pactRevisionManager.cancelAppRevision(config.packageName)
        _pendingPactRevisions.value = pactRevisionManager.getRevisions()
        _appTimeLimitConfigs.value = insightsRepo.getAppTimeLimitConfigs()
        _appTimeLimits.value = _appTimeLimitConfigs.value.mapValues { (_, v) -> v.dailyLimitMinutes }
        _dataVersion.value++
        syncWardenGreying()
    }

    /** Removing a pact-style config queues for 24 h; removing a ward is immediate. */
    fun deleteAppTimeLimit(packageName: String) {
        if (_appTimeLimitConfigs.value[packageName]?.pactModeEnabled == true) {
            pactRevisionManager.queueAppRevision(packageName, null)
            onRevisionQueued(removal = true)
            return
        }
        pactRevisionManager.cancelAppRevision(packageName)
        _pendingPactRevisions.value = pactRevisionManager.getRevisions()
        _appTimeLimitConfigs.value = insightsRepo.deleteAppTimeLimitConfig(packageName, _appTimeLimitConfigs.value)
        _appTimeLimits.value = _appTimeLimitConfigs.value.mapValues { (_, v) -> v.dailyLimitMinutes }
        _dataVersion.value++
        syncWardenGreying()
    }

    fun saveConditionalUnlock(rule: ConditionalUnlock) {
        if (!conditionalUnlockRepo.saveConditionalUnlock(rule, _conditionalUnlocks.value)) {
            Toast.makeText(getApplication(), getApplication<Application>().getString(
                R.string.toast_max_conditional_unlocks, com.infinicada.focuspocus.Constants.MAX_CONDITIONAL_UNLOCKS
            ), Toast.LENGTH_SHORT).show()
            return
        }
        _conditionalUnlocks.value = conditionalUnlockRepo.getConditionalUnlocks()
        _dataVersion.value++
    }

    fun deleteConditionalUnlock(rule: ConditionalUnlock) {
        _conditionalUnlocks.value = conditionalUnlockRepo.deleteConditionalUnlock(rule, _conditionalUnlocks.value)
        _dataVersion.value++
    }
}
