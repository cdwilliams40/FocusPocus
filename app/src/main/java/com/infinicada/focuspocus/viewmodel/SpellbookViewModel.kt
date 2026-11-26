package com.infinicada.focuspocus.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infinicada.focuspocus.AppUtils
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.BlockerRepository
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.DeviceOwnerManager
import com.infinicada.focuspocus.FocusPocusApplication
import com.infinicada.focuspocus.Talisman
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
import com.infinicada.focuspocus.data.BlockerListRepository
import com.infinicada.focuspocus.data.ScheduleRepository
import com.infinicada.focuspocus.data.TalismanRepository
import com.infinicada.focuspocus.model.AppInfo
import com.infinicada.focuspocus.model.Schedule
import com.infinicada.focuspocus.navigation.SpellbookRoute
import com.infinicada.focuspocus.navigation.WellRoute
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

    private val openReflexTracker = OpenReflexTracker(appPrefs)
    private val pactManager = PactManager(appPrefs)

    init {
        if (WellApps.applyDueRemovals(appPrefs)) {
            syncWardenGreying()
        }
    }

    /** Today's open counters per package, for the dashboard. */
    fun getTodayOpenStats(): Map<String, AppOpenStats> = openReflexTracker.getAllStats()

    /** Running pacts: package → epoch millis the pact ends. */
    fun getActivePacts(): Map<String, Long> = pactManager.getActiveAllowances()

    fun getWellSnapshot(): WellSnapshot = ManaWell.snapshot(appPrefs)

    fun creditProductive(): Long =
        com.infinicada.focuspocus.ProductiveApps.creditToday(getApplication(), appPrefs).manaEarned

    private val _productiveApps = MutableStateFlow(com.infinicada.focuspocus.ProductiveApps.load(appPrefs))
    val productiveApps: StateFlow<List<String>> = _productiveApps.asStateFlow()

    fun setProductiveApps(packages: List<String>) {
        com.infinicada.focuspocus.ProductiveApps.save(appPrefs, packages)
        _productiveApps.value = com.infinicada.focuspocus.ProductiveApps.load(appPrefs)
        _dataVersion.value++
    }

    private val _wellApps = MutableStateFlow(WellApps.load(appPrefs))
    val wellApps: StateFlow<List<WellApp>> = _wellApps.asStateFlow()

    private val _pendingRemovals = MutableStateFlow(WellApps.pendingRemovals(appPrefs))
    val pendingRemovals: StateFlow<Map<String, Long>> = _pendingRemovals.asStateFlow()

    private fun isBlockedBySession(packageName: String): Boolean =
        BlockerRepository.sessionBlockerFor(appPrefs, packageName) {
            AppUtils.isWhitelistBlockable(getApplication(), it)
        } != null

    fun getSessionBlockedWellApps(): Set<String> =
        WellApps.packages(appPrefs).filterTo(mutableSetOf()) { isBlockedBySession(it) }

    private fun reloadWellApps() {
        _wellApps.value = WellApps.load(appPrefs)
        _pendingRemovals.value = WellApps.pendingRemovals(appPrefs)
        _dataVersion.value++
    }

    fun addWellApps(packages: Collection<String>) {
        packages.forEach { WellApps.add(appPrefs, it) }
        reloadWellApps()
        syncWardenGreying()
    }

    fun setWellAppSubstitute(packageName: String, substitute: String?) {
        WellApps.setSubstitute(appPrefs, packageName, substitute)
        reloadWellApps()
    }

    fun requestWellAppRemoval(packageName: String) {
        WellApps.requestRemoval(appPrefs, packageName)
        reloadWellApps()
        com.infinicada.focuspocus.RitualAlarmScheduler.scheduleNext(getApplication())
        Toast.makeText(
            getApplication(), R.string.pact_revision_queued_removal_toast, Toast.LENGTH_LONG
        ).show()
    }

    fun cancelWellAppRemoval(packageName: String) {
        WellApps.cancelRemoval(appPrefs, packageName)
        reloadWellApps()
        com.infinicada.focuspocus.RitualAlarmScheduler.scheduleNext(getApplication())
    }

    fun applyDueWellRemovals() {
        if (!WellApps.applyDueRemovals(appPrefs)) return
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
     * the app. False when the Well couldn't pay (drained in between) or the
     * running focus session blocks the app.
     */
    fun requestPactTime(packageName: String, minutes: Int): Boolean {
        // The session would close the app the moment it opened: refuse
        // before anything is drawn rather than spend Well minutes on it.
        if (isBlockedBySession(packageName)) {
            Toast.makeText(
                getApplication(),
                getApplication<Application>().getString(
                    R.string.home_request_blocked_by_session,
                    AppUtils.getAppName(getApplication(), packageName)
                ),
                Toast.LENGTH_LONG
            ).show()
            _dataVersion.value++
            return false
        }
        val now = System.currentTimeMillis()
        pactManager.takeLapsedAllowance(packageName, now)
        if (!ManaWell.tryDraw(appPrefs, minutes, now, packageName = packageName)) {
            Toast.makeText(
                getApplication(), R.string.well_draw_refused_toast, Toast.LENGTH_LONG
            ).show()
            _dataVersion.value++
            return false
        }
        pactManager.grantAllowance(packageName, minutes, now)
        com.infinicada.focuspocus.PactNotifier.update(getApplication())
        com.infinicada.focuspocus.RitualAlarmScheduler.scheduleNext(getApplication())
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
        com.infinicada.focuspocus.PactNotifier.update(getApplication())
        com.infinicada.focuspocus.RitualAlarmScheduler.scheduleNext(getApplication())
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
    private val talismanRepo: TalismanRepository = container.talismans

    private val _blockerLists = MutableStateFlow(blockerRepo.getBlockers())
    val blockerLists: StateFlow<List<Blocker>> = _blockerLists.asStateFlow()

    private val _schedules = MutableStateFlow(scheduleRepo.getSchedules())
    val schedules: StateFlow<List<Schedule>> = _schedules.asStateFlow()

    private val _ritualExceptions = MutableStateFlow(com.infinicada.focuspocus.RitualExceptions.loadAll(appPrefs))
    val ritualExceptions: StateFlow<Map<String, Set<String>>> = _ritualExceptions.asStateFlow()

    private val _talismans = MutableStateFlow(talismanRepo.getTalismans())
    val talismans: StateFlow<List<Talisman>> = _talismans.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    // Spellbook navigation
    private val _spellbookRoute = MutableStateFlow<SpellbookRoute>(SpellbookRoute.Overview)
    val spellbookRoute: StateFlow<SpellbookRoute> = _spellbookRoute.asStateFlow()

    // Well tab navigation: dashboard overview vs. the Well apps editor
    private val _wellRoute = MutableStateFlow<WellRoute>(WellRoute.Overview)
    val wellRoute: StateFlow<WellRoute> = _wellRoute.asStateFlow()

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
        val talismanIds = _talismans.value.map { it.id }.toSet()
        _schedules.value = scheduleRepo.cleanupOrphanedSchedules(_schedules.value, blockerNames, talismanIds)
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

    fun navigateToWell(route: WellRoute) {
        _wellRoute.value = route
    }

    fun handleWellBack(): Boolean {
        if (_wellRoute.value is WellRoute.Overview) return false
        _wellRoute.value = WellRoute.Overview
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

    /** Schedules a one-day skip for [schedule] on [dayKey] (enforces >=24h advance notice). */
    fun requestRitualSkip(schedule: Schedule, dayKey: String): Boolean {
        val ok = com.infinicada.focuspocus.RitualExceptions.requestSkip(appPrefs, schedule, dayKey)
        if (ok) {
            _ritualExceptions.value = com.infinicada.focuspocus.RitualExceptions.loadAll(appPrefs)
            _dataVersion.value++
            com.infinicada.focuspocus.RitualAlarmScheduler.scheduleNext(getApplication())
        }
        return ok
    }

    /** Cancels a planned skip for [scheduleId] on [dayKey]. */
    fun cancelRitualSkip(scheduleId: String, dayKey: String) {
        com.infinicada.focuspocus.RitualExceptions.cancelSkip(appPrefs, scheduleId, dayKey)
        _ritualExceptions.value = com.infinicada.focuspocus.RitualExceptions.loadAll(appPrefs)
        _dataVersion.value++
        com.infinicada.focuspocus.RitualAlarmScheduler.scheduleNext(getApplication())
    }

    /** Binds a newly scanned tag. A re-scan of a known tag renames it and keeps its setup. */
    fun saveTalisman(tagId: String, name: String) {
        val tag = _talismans.value.find { it.id == tagId }?.copy(name = name) ?: Talisman(tagId, name)
        if (!talismanRepo.saveTalisman(tag, _talismans.value)) {
            Toast.makeText(getApplication(), getApplication<Application>().getString(
                R.string.toast_max_talismans, com.infinicada.focuspocus.Constants.MAX_NAMED_TAGS
            ), Toast.LENGTH_SHORT).show()
            return
        }
        _talismans.value = talismanRepo.getTalismans()
        _dataVersion.value++
    }

    /** Saves a talisman's session setup (enchantments, duration, breaks, lock). */
    fun saveTalismanSetup(talisman: Talisman) {
        talismanRepo.saveTalisman(talisman, _talismans.value)
        _talismans.value = talismanRepo.getTalismans()
        _dataVersion.value++
    }

    fun deleteTalisman(tag: Talisman) {
        _talismans.value = talismanRepo.deleteTalisman(tag, _talismans.value)
        _dataVersion.value++
    }

    /** Onboarding: puts [packages] behind the Well. */
    fun createDefaultPacts(packages: List<String>) = addWellApps(packages)

}
