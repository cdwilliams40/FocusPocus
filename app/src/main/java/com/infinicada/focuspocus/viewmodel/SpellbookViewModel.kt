package com.infinicada.focuspocus.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.infinicada.focuspocus.AppTimeLimitManager
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.DeviceOwnerManager
import com.infinicada.focuspocus.FocusPocusApplication
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.limit.GuardLiveState
import com.infinicada.focuspocus.limit.GuardStatus
import com.infinicada.focuspocus.limit.OpenReflexTracker
import com.infinicada.focuspocus.limit.PactManager
import com.infinicada.focuspocus.limit.PactRevisionManager
import com.infinicada.focuspocus.limit.PendingPactRevision
import com.infinicada.focuspocus.limit.SessionCooldownManager
import com.infinicada.focuspocus.model.PactGroup
import com.infinicada.focuspocus.data.BlockerListRepository
import com.infinicada.focuspocus.data.ConditionalUnlockRepository
import com.infinicada.focuspocus.data.InsightsRepository
import com.infinicada.focuspocus.data.PresetRepository
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
            effectivePactConfig(packageName)?.let {
                sessionCooldownManager.startCooldown(packageName, it, lapsedExpiry)
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
     * Panic action: seals every pact-gated app (explicit pacts and live circle
     * members) right now, revoking any running allowances. Apps already sealed
     * keep their current seal. Uses the non-escalating panic seal — choosing
     * protection is not an offence. Returns how many apps were newly sealed.
     */
    fun sealAllPacts(): Int {
        val now = System.currentTimeMillis()
        val targets = GuardStatus.pactGatedConfigs(
            _appTimeLimitConfigs.value, _pactGroups.value, _blockerLists.value
        )
        var sealed = 0
        targets.forEach { (pkg, config) ->
            pactManager.revokeAllowance(pkg)
            if (sessionCooldownManager.getCooldownState(pkg, now) == null) {
                sessionCooldownManager.startPanicSeal(pkg, config, now)
                sealed++
            }
        }
        if (targets.isNotEmpty()) {
            syncWardenGreying()
            _dataVersion.value++
        }
        return sealed
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
     * Loads every launchable app for the pickers.
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
            _installedApps.value = apps
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
            val hash = schedule.id.fold(0) { acc, c -> acc * 31 + c.code }
            val baseId = (hash and 0x7FFFFFFE)
            notificationManager.cancel(baseId)
            notificationManager.cancel(baseId + 1)
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
