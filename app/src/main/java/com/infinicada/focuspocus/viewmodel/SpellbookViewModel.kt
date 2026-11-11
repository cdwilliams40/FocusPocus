package com.infinicada.focuspocus.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.infinicada.focuspocus.AppUtils
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.DeviceOwnerManager
import com.infinicada.focuspocus.FocusPocusApplication
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.UsageStatsHelper
import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.limit.ManaWell
import com.infinicada.focuspocus.limit.OpenReflexTracker
import com.infinicada.focuspocus.limit.PactManager
import com.infinicada.focuspocus.limit.WellApp
import com.infinicada.focuspocus.limit.WellApps
import com.infinicada.focuspocus.limit.WellMath
import com.infinicada.focuspocus.limit.WellSnapshot
import com.infinicada.focuspocus.limit.WellUnlocks
import com.infinicada.focuspocus.data.BlockerListRepository
import com.infinicada.focuspocus.data.ConditionalUnlockRepository
import com.infinicada.focuspocus.data.PresetRepository
import com.infinicada.focuspocus.data.ScheduleRepository
import com.infinicada.focuspocus.data.TalismanRepository
import com.infinicada.focuspocus.model.AppInfo
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

    private val gson = Gson()
    private val openReflexTracker = OpenReflexTracker(appPrefs, gson)
    private val pactManager = PactManager(appPrefs, gson)

    init {
        // Take off any Well app whose 24 h removal came due while the app was
        // closed, before the flows below snapshot the stores. (Init blocks and
        // property initializers run in declaration order.)
        if (WellApps.applyDueRemovals(appPrefs, gson)) {
            syncWardenGreying()
        }
    }

    /** Today's open/reflex counters per package, for the dashboard. */
    fun getTodayOpenStats(): Map<String, AppOpenStats> = openReflexTracker.getAllStats()

    /** Running pacts: package → epoch millis the pact ends. */
    fun getActivePacts(): Map<String, Long> = pactManager.getActiveAllowances()

    /**
     * The Mana Well as of now — terms, level and today's draws. A handful of
     * primitive prefs; the dashboard lets the level keep refilling on its
     * own clock in between.
     */
    fun getWellSnapshot(): WellSnapshot = ManaWell.snapshot(appPrefs)

    /**
     * Pays any conditional-unlock rule that is satisfied, covers a Well app,
     * and hasn't paid yet today (WellUnlocks) — the dashboard's twin of the
     * service's check before a pact is offered, so the Well card counts
     * minutes earned even when the launcher path never runs (Warden
     * greying). Returns the minutes added. Walks the day's usage events when
     * a rule is still pending, so call it off the main thread.
     */
    fun creditWellUnlocks(): Int {
        val rules = _conditionalUnlocks.value
        if (rules.isEmpty()) return 0
        val wellPackages = _wellApps.value.mapTo(mutableSetOf()) { it.packageName }
        return WellUnlocks.creditSatisfied(
            prefs = appPrefs,
            rules = rules,
            isWellBound = { it in wellPackages },
            usedMillisToday = { UsageStatsHelper.getPackageUsageToday(getApplication(), it) }
        )
    }

    private val _wellApps = MutableStateFlow(WellApps.load(appPrefs, gson))
    val wellApps: StateFlow<List<WellApp>> = _wellApps.asStateFlow()

    /** Queued removals: package → epoch millis it comes off the Well. */
    private val _pendingRemovals = MutableStateFlow(WellApps.pendingRemovals(appPrefs, gson))
    val pendingRemovals: StateFlow<Map<String, Long>> = _pendingRemovals.asStateFlow()

    private fun reloadWellApps() {
        _wellApps.value = WellApps.load(appPrefs, gson)
        _pendingRemovals.value = WellApps.pendingRemovals(appPrefs, gson)
        _dataVersion.value++
    }

    /** Puts [packages] behind the Well right away (stricter, so no delay). */
    fun addWellApps(packages: Collection<String>) {
        packages.forEach { WellApps.add(appPrefs, gson, it) }
        reloadWellApps()
        syncWardenGreying()
    }

    fun setWellAppSubstitute(packageName: String, substitute: String?) {
        WellApps.setSubstitute(appPrefs, gson, packageName, substitute)
        reloadWellApps()
    }

    /** Taking an app off the Well waits 24 h. */
    fun requestWellAppRemoval(packageName: String) {
        WellApps.requestRemoval(appPrefs, gson, packageName)
        reloadWellApps()
        Toast.makeText(
            getApplication(), R.string.pact_revision_queued_removal_toast, Toast.LENGTH_LONG
        ).show()
    }

    fun cancelWellAppRemoval(packageName: String) {
        WellApps.cancelRemoval(appPrefs, gson, packageName)
        reloadWellApps()
    }

    /**
     * Takes off any app whose removal delay has passed. Called from the
     * dashboard's minute tick so a due change lands without a restart; the
     * accessibility service runs the same reconciliation for enforcement.
     */
    fun applyDueWellRemovals() {
        if (!WellApps.applyDueRemovals(appPrefs, gson)) return
        reloadWellApps()
        syncWardenGreying()
    }

    /**
     * Reconciles Warden-mode greying after any edit that changes which apps
     * are behind the Well. No-op when the device isn't provisioned. Runs off
     * the main thread: on a provisioned device the sync enumerates launchable
     * packages, which would jank every save tap.
     */
    private fun syncWardenGreying() {
        viewModelScope.launch(Dispatchers.Default) {
            DeviceOwnerManager.syncSuspensions(getApplication())
        }
    }

    /**
     * The dashboard's request-time flow: the in-app counterpart of the pact
     * overlay, for apps the OS itself refuses to open under Warden greying.
     * Draws from the Well, starts the pact, lifts the suspension, and opens
     * the app. False when the Well couldn't pay (drained in between).
     */
    fun requestPactTime(packageName: String, minutes: Int): Boolean {
        val now = System.currentTimeMillis()
        pactManager.takeLapsedAllowance(packageName, now)
        if (!ManaWell.tryDraw(appPrefs, minutes, now)) {
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
     * "I'm done": ends [packageName]'s running pact now and pours the unused
     * time back into the Well ([WellMath.refundableMillis], bounded by the
     * capacity). Returns the whole minutes returned.
     */
    fun endPactNow(packageName: String): Int {
        val now = System.currentTimeMillis()
        val active = pactManager.getActiveAllowances(now)
        val expiry = active[packageName] ?: return 0
        val refundMillis = WellMath.refundableMillis(
            revoked = listOf(expiry),
            stillRunning = (active - packageName).values,
            now = now
        )
        pactManager.revokeAllowance(packageName)
        ManaWell.refund(appPrefs, refundMillis, now)
        syncWardenGreying()
        _dataVersion.value++
        return (refundMillis / WellMath.MINUTE_MS).toInt()
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
    private val conditionalUnlockRepo: ConditionalUnlockRepository = container.conditionalUnlocks

    private val _blockerLists = MutableStateFlow(blockerRepo.getBlockers())
    val blockerLists: StateFlow<List<Blocker>> = _blockerLists.asStateFlow()

    private val _schedules = MutableStateFlow(scheduleRepo.getSchedules())
    val schedules: StateFlow<List<Schedule>> = _schedules.asStateFlow()

    private val _focusPresets = MutableStateFlow(presetRepo.getPresets())
    val focusPresets: StateFlow<List<FocusPreset>> = _focusPresets.asStateFlow()

    private val _namedTags = MutableStateFlow(talismanRepo.getNamedTags())
    val namedTags: StateFlow<List<NamedTag>> = _namedTags.asStateFlow()

    private val _conditionalUnlocks = MutableStateFlow(conditionalUnlockRepo.getConditionalUnlocks())
    val conditionalUnlocks: StateFlow<List<ConditionalUnlock>> = _conditionalUnlocks.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    // Spellbook navigation
    private val _spellbookRoute = MutableStateFlow<SpellbookRoute>(SpellbookRoute.Overview)
    val spellbookRoute: StateFlow<SpellbookRoute> = _spellbookRoute.asStateFlow()

    // Home tab navigation: dashboard overview vs. the Well apps editor
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
    }

    fun deleteBlocker(blocker: Blocker) {
        _blockerLists.value = blockerRepo.deleteBlocker(blocker)
        _dataVersion.value++
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

    /** Onboarding: puts [packages] behind the Well. */
    fun createDefaultPacts(packages: List<String>) = addWellApps(packages)

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
