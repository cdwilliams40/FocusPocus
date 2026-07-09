package com.infinicada.focuspocus.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.FocusPocusApplication
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.R
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
import com.infinicada.focuspocus.navigation.SpellbookRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SpellbookViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as FocusPocusApplication).container
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
            is SpellbookRoute.ConditionalUnlocks -> SpellbookRoute.Overview
            else -> SpellbookRoute.Overview
        }
        return true
    }

    // CRUD operations
    fun saveBlocker(blocker: Blocker) {
        if (!blockerRepo.saveBlocker(blocker, _blockerLists.value)) {
            Toast.makeText(getApplication(), getApplication<Application>().getString(
                R.string.toast_max_enchantments, com.infinicada.focuspocus.Constants.MAX_BLOCKERS
            ), Toast.LENGTH_SHORT).show()
            return
        }
        _blockerLists.value = blockerRepo.getBlockers()
        _dataVersion.value++
    }

    fun deleteBlocker(blocker: Blocker) {
        _blockerLists.value = blockerRepo.deleteBlocker(blocker, _blockerLists.value)
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

    fun saveQrTalisman(tag: NamedTag) {
        if (!talismanRepo.saveQrTalisman(tag, _namedTags.value)) {
            Toast.makeText(getApplication(), getApplication<Application>().getString(
                R.string.toast_max_talismans, com.infinicada.focuspocus.Constants.MAX_NAMED_TAGS
            ), Toast.LENGTH_SHORT).show()
            return
        }
        _namedTags.value = talismanRepo.getNamedTags()
        _dataVersion.value++
    }

    fun saveAppTimeLimitConfig(config: AppTimeLimit) {
        if (!insightsRepo.saveAppTimeLimitConfig(config, _appTimeLimitConfigs.value)) {
            Toast.makeText(getApplication(), getApplication<Application>().getString(
                R.string.toast_max_time_limits, com.infinicada.focuspocus.Constants.MAX_APP_TIME_LIMITS
            ), Toast.LENGTH_SHORT).show()
            return
        }
        _appTimeLimitConfigs.value = insightsRepo.getAppTimeLimitConfigs()
        _appTimeLimits.value = _appTimeLimitConfigs.value.mapValues { (_, v) -> v.dailyLimitMinutes }
        _dataVersion.value++
    }

    fun deleteAppTimeLimit(packageName: String) {
        _appTimeLimitConfigs.value = insightsRepo.deleteAppTimeLimitConfig(packageName, _appTimeLimitConfigs.value)
        _appTimeLimits.value = _appTimeLimitConfigs.value.mapValues { (_, v) -> v.dailyLimitMinutes }
        _dataVersion.value++
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
