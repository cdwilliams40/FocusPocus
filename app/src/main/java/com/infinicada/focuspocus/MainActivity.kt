package com.infinicada.focuspocus

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import com.infinicada.focuspocus.ui.screens.BlockerListScreen
import com.infinicada.focuspocus.ui.screens.BlockerSelectionDialog
import com.infinicada.focuspocus.ui.screens.CreateBlockerScreen
import com.infinicada.focuspocus.ui.screens.EditBlockerScreen
import com.infinicada.focuspocus.ui.screens.Greeting
import com.infinicada.focuspocus.ui.screens.ProfileScreen
import com.infinicada.focuspocus.ui.screens.ScheduleEditorScreen
import com.infinicada.focuspocus.ui.screens.ScheduleListScreen
import com.infinicada.focuspocus.ui.screens.UsageStatsScreen
import com.infinicada.focuspocus.ui.theme.FocusPocusTheme
import com.infinicada.focuspocus.ui.theme.ThemeMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var namedTags by mutableStateOf<List<NamedTag>>(emptyList())
    private var lastScannedTagId by mutableStateOf<String?>(null)
    private var focusTagId by mutableStateOf<String?>(null)
    private var blockerLists by mutableStateOf<List<Blocker>>(emptyList())
    private var schedules by mutableStateOf<List<Schedule>>(emptyList())
    private var focusPresets by mutableStateOf<List<FocusPreset>>(emptyList())
    private val gson = Gson()
    private var installedApps by mutableStateOf<List<AppInfo>>(emptyList())
    private var isServiceEnabled by mutableStateOf(false)
    private var activeScheduleId by mutableStateOf<String?>(null)
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var nfcTriggerCount by mutableStateOf(0)
    private var qrTriggerCount by mutableStateOf(0)
    private var autoTriggers by mutableStateOf<List<AutoTrigger>>(emptyList())
    private var appTimeLimits by mutableStateOf<Map<String, Int>>(emptyMap())
    // Incremented whenever a background service (WiFi/BT) changes focus state, so the
    // composable knows to re-sync its state from SharedPreferences.
    private var servicesTriggerCount by mutableStateOf(0)

    private lateinit var scanLauncher: ActivityResultLauncher<ScanOptions>

    // Watches SERVICES_TRIGGER_COUNT written by WifiTriggerService / BluetoothTriggerReceiver.
    // Registered in onResume / unregistered in onPause to avoid leaks.
    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Constants.PrefsKeys.SERVICES_TRIGGER_COUNT) {
            servicesTriggerCount++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scanLauncher = registerForActivityResult(ScanContract()) { result ->
            result.contents?.let { handleQrResult(it) }
        }

        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        focusTagId = sharedPreferences.getString(Constants.PrefsKeys.FOCUS_TAG_ID, null)
        activeScheduleId = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)
        themeMode = try {
            ThemeMode.valueOf(sharedPreferences.getString(Constants.PrefsKeys.THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }

        loadNamedTags()
        loadBlockerLists()
        loadSchedules()
        loadFocusPresets()
        loadAutoTriggers()
        loadAppTimeLimits()

        // Load installed apps off the main thread; lifecycleScope cancels automatically on destroy
        lifecycleScope.launch(Dispatchers.IO) {
            val apps = loadInstalledApps()
            withContext(Dispatchers.Main) { installedApps = apps }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        setContent {
            FocusPocusTheme(themeMode = themeMode) {
                FocusPocusApp(
                    focusTagId = focusTagId,
                    lastScannedTagId = lastScannedTagId,
                    namedTags = namedTags,
                    blockerLists = blockerLists,
                    installedApps = installedApps,
                    schedules = schedules,
                    focusPresets = focusPresets,
                    isServiceEnabled = isServiceEnabled,
                    activeScheduleId = activeScheduleId,
                    nfcTriggerCount = nfcTriggerCount,
                    servicesTriggerCount = servicesTriggerCount,
                    themeMode = themeMode,
                    onThemeModeChanged = { newMode ->
                        themeMode = newMode
                        sharedPreferences.edit().putString(Constants.PrefsKeys.THEME_MODE, newMode.name).apply()
                    },
                    onSaveTag = { name -> saveNamedTag(name) },
                    onDeleteTag = { tag -> deleteNamedTag(tag) },
                    onSaveBlocker = { newBlocker -> saveBlocker(newBlocker) },
                    onDeleteBlocker = { blockerToDelete -> deleteBlocker(blockerToDelete) },
                    onSaveSchedule = { newSchedule -> saveSchedule(newSchedule) },
                    onDeleteSchedule = { scheduleToDelete -> deleteSchedule(scheduleToDelete) },
                    onDispelSchedule = { dispelSchedule() },
                    onSaveFocusPreset = { preset -> saveFocusPreset(preset) },
                    onDeleteFocusPreset = { preset -> deleteFocusPreset(preset) },
                    onScanQrCode = { launchQrScanner() },
                    qrTriggerCount = qrTriggerCount,
                    autoTriggers = autoTriggers,
                    onSaveAutoTrigger = { trigger -> saveAutoTrigger(trigger) },
                    onDeleteAutoTrigger = { trigger -> deleteAutoTrigger(trigger) },
                    appTimeLimits = appTimeLimits,
                    onSaveAppTimeLimit = { pkg, limit -> saveAppTimeLimit(pkg, limit) },
                    onDeleteAppTimeLimit = { pkg -> deleteAppTimeLimit(pkg) },
                    modifier = Modifier
                )
            }
        }

        handleIntent(intent)
    }

    private fun loadSchedules() {
        val json = sharedPreferences.getString(Constants.PrefsKeys.SCHEDULES, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<Schedule>>() {}.type
                schedules = gson.fromJson(json, type)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error parsing schedules JSON: ${e.message}", e)
                schedules = emptyList()
                sharedPreferences.edit().remove(Constants.PrefsKeys.SCHEDULES).apply()
                Toast.makeText(this, "Ritual data was corrupted and has been reset", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveSchedule(newSchedule: Schedule) {
        val isUpdate = schedules.any { it.id == newSchedule.id }
        if (!isUpdate && schedules.size >= Constants.MAX_SCHEDULES) {
            Toast.makeText(this, "Maximum of ${Constants.MAX_SCHEDULES} rituals reached", Toast.LENGTH_SHORT).show()
            return
        }
        val updatedSchedules = schedules.filterNot { it.id == newSchedule.id } + newSchedule
        val json = gson.toJson(updatedSchedules)
        sharedPreferences.edit().putString(Constants.PrefsKeys.SCHEDULES, json).apply()
        schedules = updatedSchedules
    }

    private fun deleteSchedule(scheduleToDelete: Schedule) {
        val updatedSchedules = schedules.filterNot { it.id == scheduleToDelete.id }
        val json = gson.toJson(updatedSchedules)
        sharedPreferences.edit().putString(Constants.PrefsKeys.SCHEDULES, json).apply()
        schedules = updatedSchedules
    }

    private fun dispelSchedule() {
        activeScheduleId = null
        sharedPreferences.edit()
            .remove(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID)
            .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
            .apply()
        DndController.updateDndState(this)
    }

    private fun loadInstalledApps(): List<AppInfo> {
        val pm = packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter {
                pm.getLaunchIntentForPackage(it.packageName) != null
            }
            .map {
                AppInfo(
                    name = it.loadLabel(pm).toString(),
                    packageName = it.packageName
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    private fun loadBlockerLists() {
        val json = sharedPreferences.getString(Constants.PrefsKeys.BLOCKER_LISTS, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<Blocker>>() {}.type
                blockerLists = gson.fromJson(json, type)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error parsing blocker lists JSON: ${e.message}", e)
                blockerLists = listOf(Blocker("Default", BlockerMode.BLACKLIST, listOf("com.google.android.youtube")))
                sharedPreferences.edit().remove(Constants.PrefsKeys.BLOCKER_LISTS).apply()
                Toast.makeText(this, "Enchantment data was corrupted - restored defaults", Toast.LENGTH_LONG).show()
            }
        } else {
            blockerLists = listOf(Blocker("Default", BlockerMode.BLACKLIST, listOf("com.google.android.youtube")))
        }
    }

    private fun saveBlocker(newBlocker: Blocker) {
        val isUpdate = blockerLists.any { it.name == newBlocker.name }
        if (!isUpdate && blockerLists.size >= Constants.MAX_BLOCKERS) {
            Toast.makeText(this, "Maximum of ${Constants.MAX_BLOCKERS} enchantments reached", Toast.LENGTH_SHORT).show()
            return
        }
        val capped = newBlocker.copy(
            apps = newBlocker.apps.take(Constants.MAX_APPS_PER_BLOCKER),
            websites = newBlocker.websites?.take(Constants.MAX_WEBSITES_PER_BLOCKER)
        )
        val updatedBlockers = blockerLists.filterNot { it.name == capped.name } + capped
        val json = gson.toJson(updatedBlockers)
        sharedPreferences.edit().putString(Constants.PrefsKeys.BLOCKER_LISTS, json).apply()
        blockerLists = updatedBlockers
    }

    private fun deleteBlocker(blockerToDelete: Blocker) {
        val updatedBlockers = blockerLists.filterNot { it.name == blockerToDelete.name }
        val json = gson.toJson(updatedBlockers)
        sharedPreferences.edit().putString(Constants.PrefsKeys.BLOCKER_LISTS, json).apply()
        blockerLists = updatedBlockers
    }

    private fun loadFocusPresets() {
        val json = sharedPreferences.getString(Constants.PrefsKeys.FOCUS_PRESETS, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<FocusPreset>>() {}.type
                focusPresets = gson.fromJson(json, type)
                return
            } catch (e: Exception) {
                Log.e("MainActivity", "Error parsing focus presets JSON: ${e.message}", e)
                sharedPreferences.edit().remove(Constants.PrefsKeys.FOCUS_PRESETS).apply()
                Toast.makeText(this, "Quick Spell data was corrupted - restored defaults", Toast.LENGTH_LONG).show()
            }
        }
        // Default presets on first load or parse error
        focusPresets = listOf(
            FocusPreset(
                name = "Deep Work",
                blockerName = "Default",
                durationMinutes = 240,
                breaksEnabled = true
            ),
            FocusPreset(
                name = "Quick Focus",
                blockerName = "Default",
                durationMinutes = 25,
                breaksEnabled = true
            ),
            FocusPreset(
                name = "Sleep Mode",
                blockerName = "Default",
                durationMinutes = 480,
                breaksEnabled = false
            )
        )
        val defaultJson = gson.toJson(focusPresets)
        sharedPreferences.edit().putString(Constants.PrefsKeys.FOCUS_PRESETS, defaultJson).apply()
    }

    private fun saveFocusPreset(preset: FocusPreset) {
        val isUpdate = focusPresets.any { it.id == preset.id }
        if (!isUpdate && focusPresets.size >= Constants.MAX_PRESETS) {
            Toast.makeText(this, "Maximum of ${Constants.MAX_PRESETS} quick spells reached", Toast.LENGTH_SHORT).show()
            return
        }
        val updatedPresets = focusPresets.filterNot { it.id == preset.id } + preset
        val json = gson.toJson(updatedPresets)
        sharedPreferences.edit().putString(Constants.PrefsKeys.FOCUS_PRESETS, json).apply()
        focusPresets = updatedPresets
    }

    private fun deleteFocusPreset(preset: FocusPreset) {
        val updatedPresets = focusPresets.filterNot { it.id == preset.id }
        val json = gson.toJson(updatedPresets)
        sharedPreferences.edit().putString(Constants.PrefsKeys.FOCUS_PRESETS, json).apply()
        focusPresets = updatedPresets
    }

    private fun loadAutoTriggers() {
        val json = sharedPreferences.getString(Constants.PrefsKeys.AUTO_TRIGGERS, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<AutoTrigger>>() {}.type
                autoTriggers = gson.fromJson(json, type)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error parsing auto triggers JSON: ${e.message}", e)
                autoTriggers = emptyList()
                sharedPreferences.edit().remove(Constants.PrefsKeys.AUTO_TRIGGERS).apply()
            }
        }
    }

    private fun saveAutoTrigger(trigger: AutoTrigger) {
        val isUpdate = autoTriggers.any { it.id == trigger.id }
        if (!isUpdate && autoTriggers.size >= Constants.MAX_AUTO_TRIGGERS) {
            Toast.makeText(this, "Maximum of ${Constants.MAX_AUTO_TRIGGERS} auto triggers reached", Toast.LENGTH_SHORT).show()
            return
        }
        val updated = autoTriggers.filterNot { it.id == trigger.id } + trigger
        sharedPreferences.edit().putString(Constants.PrefsKeys.AUTO_TRIGGERS, gson.toJson(updated)).apply()
        autoTriggers = updated
        updateWifiTriggerService()
    }

    private fun deleteAutoTrigger(trigger: AutoTrigger) {
        val updated = autoTriggers.filterNot { it.id == trigger.id }
        sharedPreferences.edit().putString(Constants.PrefsKeys.AUTO_TRIGGERS, gson.toJson(updated)).apply()
        autoTriggers = updated
        updateWifiTriggerService()
    }

    private fun updateWifiTriggerService() {
        val hasWifiTriggers = autoTriggers.any { it.type == TriggerType.WIFI && it.enabled }
        val intent = Intent(this, WifiTriggerService::class.java)
        if (hasWifiTriggers) {
            androidx.core.content.ContextCompat.startForegroundService(this, intent)
        } else {
            stopService(intent)
        }
    }

    private fun loadAppTimeLimits() {
        appTimeLimits = AppTimeLimitManager.getTimeLimits(sharedPreferences, gson)
    }

    private fun saveAppTimeLimit(packageName: String, limitMinutes: Int) {
        val updated = appTimeLimits.toMutableMap()
        updated[packageName] = limitMinutes
        if (updated.size > Constants.MAX_APP_TIME_LIMITS) {
            Toast.makeText(this, "Maximum of ${Constants.MAX_APP_TIME_LIMITS} time limits reached", Toast.LENGTH_SHORT).show()
            return
        }
        AppTimeLimitManager.saveTimeLimits(sharedPreferences, gson, updated)
        appTimeLimits = updated
    }

    private fun deleteAppTimeLimit(packageName: String) {
        val updated = appTimeLimits.toMutableMap()
        updated.remove(packageName)
        AppTimeLimitManager.saveTimeLimits(sharedPreferences, gson, updated)
        appTimeLimits = updated
    }

    private fun loadNamedTags() {
        val json = sharedPreferences.getString(Constants.PrefsKeys.NAMED_TAGS, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<NamedTag>>() {}.type
                namedTags = gson.fromJson(json, type)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error parsing named tags JSON: ${e.message}", e)
                namedTags = emptyList()
                sharedPreferences.edit().remove(Constants.PrefsKeys.NAMED_TAGS).apply()
                Toast.makeText(this, "Talisman data was corrupted and has been reset", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveNamedTag(name: String) {
        lastScannedTagId?.let {
            val newTag = NamedTag(it, name)
            val isUpdate = namedTags.any { t -> t.id == newTag.id }
            if (!isUpdate && namedTags.size >= Constants.MAX_NAMED_TAGS) {
                Toast.makeText(this, "Maximum of ${Constants.MAX_NAMED_TAGS} talismans reached", Toast.LENGTH_SHORT).show()
                return
            }
            val updatedTags = namedTags.filterNot { t -> t.id == newTag.id } + newTag
            val json = gson.toJson(updatedTags)
            sharedPreferences.edit().putString(Constants.PrefsKeys.NAMED_TAGS, json).apply()
            namedTags = updatedTags
        }
    }

    private fun deleteNamedTag(tagToDelete: NamedTag) {
        val updatedTags = namedTags.filterNot { it.id == tagToDelete.id }
        val json = gson.toJson(updatedTags)
        sharedPreferences.edit().putString(Constants.PrefsKeys.NAMED_TAGS, json).apply()
        namedTags = updatedTags
    }

    /**
     * Shared preset toggle logic used by both NFC talisman and QR code scanning.
     * Returns true if the trigger count should be incremented.
     */
    private fun togglePreset(preset: FocusPreset): Boolean {
        val isManualFocusActive = sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)

        val tempDuration = preset.tempDurationMinutes ?: 30

        when (preset.action ?: PresetAction.TOGGLE) {
            PresetAction.TEMP_ENABLE -> {
                val blocker = blockerLists.find { it.name == preset.blockerName }
                if (blocker != null) {
                    val focusTimeRemaining = tempDuration * 60
                    sharedPreferences.edit()
                        .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
                        .putString(Constants.PrefsKeys.ACTIVE_BLOCKER, blocker.name)
                        .putInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, tempDuration)
                        .putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, focusTimeRemaining)
                        .putBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, preset.breaksEnabled)
                        .putLong(Constants.PrefsKeys.SESSION_START_TIME, System.currentTimeMillis())
                        .apply()
                    Toast.makeText(this, "${preset.name} Cast for $tempDuration min!", Toast.LENGTH_SHORT).show()
                    return true
                } else {
                    Toast.makeText(this, "Enchantment missing for ${preset.name}", Toast.LENGTH_SHORT).show()
                    return false
                }
            }
            PresetAction.TEMP_DISABLE -> {
                if (isManualFocusActive) {
                    sharedPreferences.edit()
                        .putBoolean(Constants.PrefsKeys.IS_ON_BREAK, true)
                        .putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, tempDuration * 60)
                        .apply()
                    DndController.updateDndState(this)
                    Toast.makeText(this, "Temporary break for $tempDuration min!", Toast.LENGTH_SHORT).show()
                    return true
                } else {
                    Toast.makeText(this, "No active focus to pause", Toast.LENGTH_SHORT).show()
                    return false
                }
            }
            PresetAction.TOGGLE, null -> {
                if (isManualFocusActive) {
                    SessionRecorder.record(sharedPreferences, gson)
                    sharedPreferences.edit()
                        .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
                        .putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, 0)
                        .apply()
                    Toast.makeText(this, "${preset.name} Dispelled!", Toast.LENGTH_SHORT).show()
                    return true
                } else {
                    val blocker = blockerLists.find { it.name == preset.blockerName }
                    if (blocker != null) {
                        val focusTimeRemaining = if (preset.durationMinutes > 0) preset.durationMinutes * 60 else 0
                        sharedPreferences.edit()
                            .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
                            .putString(Constants.PrefsKeys.ACTIVE_BLOCKER, blocker.name)
                            .putInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, preset.durationMinutes)
                            .putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, focusTimeRemaining)
                            .putBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, preset.breaksEnabled)
                            .putLong(Constants.PrefsKeys.SESSION_START_TIME, System.currentTimeMillis())
                            .apply()
                        Toast.makeText(this, "${preset.name} Cast!", Toast.LENGTH_SHORT).show()
                        return true
                    } else {
                        Toast.makeText(this, "Enchantment missing for ${preset.name}", Toast.LENGTH_SHORT).show()
                        return false
                    }
                }
            }
        }
    }

    private val validIdPattern = Regex("^[a-f0-9\\-]{1,64}$")

    private fun handleQrResult(contents: String) {
        val presetPrefix = "focuspocus://preset/"
        val talismanPrefix = "focuspocus://talisman/"
        when {
            contents.startsWith(presetPrefix) -> {
                val presetId = contents.removePrefix(presetPrefix)
                if (!validIdPattern.matches(presetId)) {
                    Toast.makeText(this, "Invalid QR code", Toast.LENGTH_SHORT).show()
                    return
                }
                val preset = focusPresets.find { it.id == presetId }
                if (preset == null) {
                    Toast.makeText(this, "Quick Spell not found", Toast.LENGTH_SHORT).show()
                    return
                }
                if (togglePreset(preset)) {
                    qrTriggerCount++
                }
            }
            contents.startsWith(talismanPrefix) -> {
                val talismanId = contents.removePrefix(talismanPrefix)
                if (!validIdPattern.matches(talismanId)) {
                    Toast.makeText(this, "Invalid QR code", Toast.LENGTH_SHORT).show()
                    return
                }
                val talisman = namedTags.find { it.id == talismanId }
                if (talisman == null) {
                    Toast.makeText(this, "Talisman not found", Toast.LENGTH_SHORT).show()
                    return
                }
                // Find preset bound to this talisman (same as NFC tap)
                val boundPreset = focusPresets.find { it.talismanId == talismanId }
                if (boundPreset != null) {
                    if (togglePreset(boundPreset)) {
                        qrTriggerCount++
                    }
                } else {
                    Toast.makeText(this, "No Quick Spell bound to ${talisman.name}", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                Toast.makeText(this, "Invalid QR code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "focuspocus") {
            val id = data.pathSegments.firstOrNull() ?: return
            if (!validIdPattern.matches(id)) return
            when (data.host) {
                "preset" -> handleQrResult("focuspocus://preset/$id")
                "talisman" -> handleQrResult("focuspocus://talisman/$id")
            }
        }
    }

    fun launchQrScanner() {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt("Scan a Focus Pocus QR code")
        options.setBeepEnabled(false)
        options.setOrientationLocked(true)
        scanLauncher.launch(options)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
        isServiceEnabled = isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)
        activeScheduleId = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)
        // Start observing service-triggered focus changes while the Activity is visible
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefChangeListener)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
    }

    override fun onTagDiscovered(tag: Tag?) {
        tag?.let {
            val tagIdBytes = it.id
            if (tagIdBytes == null || tagIdBytes.isEmpty()) {
                Log.w("MainActivity", "NFC tag has empty or null ID")
                return@let
            }
            val newTagId = tagIdBytes.toHexString()
            runOnUiThread {
                lastScannedTagId = newTagId

                // Check if an active schedule needs unbinding
                if (activeScheduleId != null) {
                    val schedule = schedules.find { s -> s.id == activeScheduleId }
                    if (schedule != null && schedule.unbindingTalismanId != null) {
                         if (schedule.unbindingTalismanId == newTagId) {
                             SessionRecorder.record(sharedPreferences, gson)
                             dispelSchedule()
                             Toast.makeText(this, "Ritual Dispelled!", Toast.LENGTH_SHORT).show()
                         } else {
                             Toast.makeText(this, "Wrong Talisman!", Toast.LENGTH_SHORT).show()
                         }
                         return@runOnUiThread
                    }
                }

                // Check if a preset is bound to this talisman
                val boundPreset = focusPresets.find { p -> p.talismanId == newTagId }
                if (boundPreset != null) {
                    if (togglePreset(boundPreset)) {
                        nfcTriggerCount++
                    }
                    return@runOnUiThread
                }

                val isNamed = namedTags.any { t -> t.id == newTagId }
                if (isNamed) {
                    if (focusTagId == null) {
                        focusTagId = newTagId
                        sharedPreferences.edit().putString(Constants.PrefsKeys.FOCUS_TAG_ID, newTagId).apply()
                    } else {
                        focusTagId = null
                        sharedPreferences.edit().remove(Constants.PrefsKeys.FOCUS_TAG_ID).apply()
                    }
                }
            }
        }
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val expectedComponentName = ComponentName(context, service)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName)
                return true
        }
        return false
    }
}

sealed class Screen {
    object BlockerList : Screen()
    object CreateBlocker : Screen()
    object EditBlocker : Screen()
}

sealed class ScheduleScreenRoute {
    object ScheduleList : ScheduleScreenRoute()
    object CreateSchedule : ScheduleScreenRoute()
    data class EditSchedule(val schedule: Schedule) : ScheduleScreenRoute()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusPocusApp(
    focusTagId: String?,
    lastScannedTagId: String?,
    namedTags: List<NamedTag>,
    blockerLists: List<Blocker>,
    installedApps: List<AppInfo>,
    schedules: List<Schedule>,
    focusPresets: List<FocusPreset>,
    isServiceEnabled: Boolean,
    activeScheduleId: String?,
    nfcTriggerCount: Int,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onSaveTag: (String) -> Unit,
    onDeleteTag: (NamedTag) -> Unit,
    onSaveBlocker: (Blocker) -> Unit,
    onDeleteBlocker: (Blocker) -> Unit,
    onSaveSchedule: (Schedule) -> Unit,
    onDeleteSchedule: (Schedule) -> Unit,
    onDispelSchedule: () -> Unit,
    onSaveFocusPreset: (FocusPreset) -> Unit,
    onDeleteFocusPreset: (FocusPreset) -> Unit,
    onScanQrCode: () -> Unit = {},
    qrTriggerCount: Int = 0,
    servicesTriggerCount: Int = 0,
    autoTriggers: List<AutoTrigger> = emptyList(),
    onSaveAutoTrigger: (AutoTrigger) -> Unit = {},
    onDeleteAutoTrigger: (AutoTrigger) -> Unit = {},
    appTimeLimits: Map<String, Int> = emptyMap(),
    onSaveAppTimeLimit: (String, Int) -> Unit = { _, _ -> },
    onDeleteAppTimeLimit: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentDestination by remember { mutableStateOf(AppDestinations.HOME) }
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) }

    var manualFocusMode by remember {
        mutableStateOf(sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false))
    }
    var activeManualBlocker by remember {
        val activeBlockerName = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null)
        mutableStateOf(blockerLists.find { it.name == activeBlockerName })
    }

    // Focus duration settings
    var focusDurationMinutes by remember {
        mutableIntStateOf(sharedPreferences.getInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, 0))
    }
    var focusTimeRemaining by remember {
        mutableIntStateOf(sharedPreferences.getInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, 0))
    }

    // Session breaks toggle (for manual focus mode)
    var sessionBreaksEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, true))
    }

    // Selected preset ID (null when "Custom" or no preset matches)
    var selectedPresetId by remember { mutableStateOf<String?>(null) }

    // Notification muting settings
    var muteBlockedNotifications by remember {
        mutableStateOf(sharedPreferences.getBoolean(Constants.PrefsKeys.MUTE_BLOCKED_NOTIFICATIONS, true))
    }
    val notificationManager = remember { context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    var isNotificationListenerEnabled by remember {
        mutableStateOf(notificationManager.isNotificationPolicyAccessGranted)
    }

    // Hide stop button setting
    var hideStopButton by remember {
        mutableStateOf(sharedPreferences.getBoolean(Constants.PrefsKeys.HIDE_STOP_BUTTON, false))
    }

    // NFC lock mode
    var nfcLockMode by remember {
        mutableStateOf(sharedPreferences.getBoolean(Constants.PrefsKeys.NFC_LOCK_MODE, false))
    }

    // Block events for enhanced statistics
    var blockEvents by remember {
        val json = sharedPreferences.getString(Constants.PrefsKeys.BLOCK_EVENTS, null)
        val events: List<BlockEvent> = if (json != null) {
            try {
                val type = object : TypeToken<List<BlockEvent>>() {}.type
                Gson().fromJson(json, type)
            } catch (e: Exception) { emptyList() }
        } else emptyList()
        mutableStateOf(events)
    }

    // Onboarding
    var onboardingCompleted by remember {
        mutableStateOf(sharedPreferences.getBoolean(Constants.PrefsKeys.ONBOARDING_COMPLETED, false))
    }

    // Emergency break settings
    var lastEmergencyBreakMillis by remember {
        mutableStateOf(sharedPreferences.getLong(Constants.PrefsKeys.LAST_EMERGENCY_BREAK_MILLIS, 0L))
    }
    var emergencyBreakCadenceWeeks by remember {
        mutableIntStateOf(sharedPreferences.getInt(Constants.PrefsKeys.EMERGENCY_BREAK_CADENCE_WEEKS, 2))
    }

    // Session history
    val gson = remember { Gson() }
    var focusSessions by remember {
        val json = sharedPreferences.getString(Constants.PrefsKeys.FOCUS_SESSIONS, null)
        val sessions: List<FocusSession> = if (json != null) {
            try {
                val type = object : TypeToken<List<FocusSession>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) { emptyList() }
        } else emptyList()
        mutableStateOf(sessions)
    }
    var longestStreak by remember {
        mutableIntStateOf(sharedPreferences.getInt(Constants.PrefsKeys.LONGEST_STREAK, 0))
    }
    val currentStreak = remember(focusSessions) { calculateCurrentStreak(focusSessions) }

    // Helper to record a completed session and immediately update composable state
    fun recordSession(blockerName: String, breaksUsed: Int) {
        val updated = SessionRecorder.record(sharedPreferences, gson)
        if (updated.isNotEmpty()) {
            focusSessions = updated
            longestStreak = sharedPreferences.getInt(Constants.PrefsKeys.LONGEST_STREAK, 0)
        }
    }

    // Break settings
    var breakDurationMinutes by remember {
        mutableIntStateOf(sharedPreferences.getInt(Constants.PrefsKeys.BREAK_DURATION_MINUTES, 5))
    }
    var maxBreaksPerSession by remember {
        mutableIntStateOf(sharedPreferences.getInt(Constants.PrefsKeys.MAX_BREAKS_PER_SESSION, 3))
    }

    // Break state
    var isOnBreak by remember { mutableStateOf(sharedPreferences.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)) }
    var breaksUsedThisSession by remember {
        mutableIntStateOf(sharedPreferences.getInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, 0))
    }
    var breakTimeRemaining by remember {
        mutableIntStateOf(sharedPreferences.getInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, 0))
    }

    // Break countdown timer
    LaunchedEffect(isOnBreak, breakTimeRemaining) {
        if (isOnBreak && breakTimeRemaining > 0) {
            delay(1000L)
            breakTimeRemaining -= 1
            sharedPreferences.edit().putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, breakTimeRemaining).apply()
        } else if (isOnBreak && breakTimeRemaining <= 0) {
            isOnBreak = false
            sharedPreferences.edit().putBoolean(Constants.PrefsKeys.IS_ON_BREAK, false).apply()
            DndController.updateDndState(context)
        }
    }

    // Focus session countdown timer
    LaunchedEffect(manualFocusMode, focusTimeRemaining, isOnBreak) {
        if (manualFocusMode && focusTimeRemaining > 0 && !isOnBreak) {
            delay(1000L)
            focusTimeRemaining -= 1
            sharedPreferences.edit().putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, focusTimeRemaining).apply()

            if (focusTimeRemaining <= 0) {
                // Record session on timer auto-stop
                val blockerName = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null) ?: "Unknown"
                recordSession(blockerName, breaksUsedThisSession)
                manualFocusMode = false
                sharedPreferences.edit()
                    .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
                    .putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, 0)
                    .apply()
            }
        }
    }

    // Consolidated effect for focus mode state changes
    LaunchedEffect(manualFocusMode, activeManualBlocker) {
        val editor = sharedPreferences.edit()
            .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, manualFocusMode)
            .putString(Constants.PrefsKeys.ACTIVE_BLOCKER, activeManualBlocker?.name)

        if (!manualFocusMode) {
            breaksUsedThisSession = 0
            isOnBreak = false
            breakTimeRemaining = 0
            focusTimeRemaining = 0
            editor
                .putInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, 0)
                .putBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)
                .putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, 0)
                .putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, 0)
        }

        editor.apply()
        DndController.updateDndState(context)
    }

    // Sync UI with external changes to activeScheduleId
    LaunchedEffect(activeScheduleId) {
         if (activeScheduleId == null) {
              manualFocusMode = sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
         } else {
             manualFocusMode = true
         }
    }

    // Shared logic to re-read all prefs after an external trigger (NFC or QR)
    fun syncFromPrefs() {
        manualFocusMode = sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
        val activeBlockerName = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null)
        activeManualBlocker = blockerLists.find { it.name == activeBlockerName }
        focusDurationMinutes = sharedPreferences.getInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, 0)
        focusTimeRemaining = sharedPreferences.getInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, 0)
        sessionBreaksEnabled = sharedPreferences.getBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, true)
        // Re-read session history so Insights screen stays current
        val sessionsJson = sharedPreferences.getString(Constants.PrefsKeys.FOCUS_SESSIONS, null)
        focusSessions = if (sessionsJson != null) {
            try {
                val type = object : TypeToken<List<FocusSession>>() {}.type
                gson.fromJson(sessionsJson, type)
            } catch (e: Exception) { emptyList() }
        } else emptyList()
        longestStreak = sharedPreferences.getInt(Constants.PrefsKeys.LONGEST_STREAK, 0)

        // Re-read block events
        val blockEventsJson = sharedPreferences.getString(Constants.PrefsKeys.BLOCK_EVENTS, null)
        blockEvents = if (blockEventsJson != null) {
            try {
                val type = object : TypeToken<List<BlockEvent>>() {}.type
                gson.fromJson(blockEventsJson, type)
            } catch (e: Exception) { emptyList() }
        } else emptyList()
    }

    // Sync UI with NFC preset activation
    LaunchedEffect(nfcTriggerCount) {
        if (nfcTriggerCount > 0) { syncFromPrefs() }
    }

    // Sync UI with QR code activation
    LaunchedEffect(qrTriggerCount) {
        if (qrTriggerCount > 0) { syncFromPrefs() }
    }

    // Sync UI when a background service (WiFi/BT trigger) changes focus state
    LaunchedEffect(servicesTriggerCount) {
        if (servicesTriggerCount > 0) { syncFromPrefs() }
    }

    // Onboarding screen
    if (!onboardingCompleted) {
        com.infinicada.focuspocus.ui.screens.OnboardingScreen(
            namedTags = namedTags,
            blockerLists = blockerLists,
            installedApps = installedApps,
            isServiceEnabled = isServiceEnabled,
            onSaveBlocker = onSaveBlocker,
            onSaveTag = onSaveTag,
            onComplete = {
                onboardingCompleted = true
                sharedPreferences.edit()
                    .putBoolean(Constants.PrefsKeys.ONBOARDING_COMPLETED, true)
                    .putInt(Constants.PrefsKeys.ONBOARDING_VERSION, 1)
                    .apply()
            }
        )
        return
    }

    if (!isServiceEnabled) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Grant Magical Sight") },
            text = { Text("Focus Pocus needs Accessibility permission to detect when you open distracting apps and gently guide you back to your focus. Without this enchantment, your spells cannot shield you from temptation.") },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }) {
                    Text("Go to Settings")
                }
            }
        )
    }

    var showBlockerSelectionDialog by remember { mutableStateOf(false) }
    val focusMode = focusTagId != null || manualFocusMode
    var screen by remember { mutableStateOf<Screen>(Screen.BlockerList) }
    var scheduleScreen by remember { mutableStateOf<ScheduleScreenRoute>(ScheduleScreenRoute.ScheduleList) }
    var selectedBlocker by remember { mutableStateOf<Blocker?>(null) }

    val activeSchedule = remember(activeScheduleId, schedules) {
        schedules.find { it.id == activeScheduleId }
    }

    if (showBlockerSelectionDialog) {
        BlockerSelectionDialog(
            blockerLists = blockerLists,
            onBlockerSelected = { blocker ->
                activeManualBlocker = blocker
                showBlockerSelectionDialog = false
            },
            onDismissRequest = {
                showBlockerSelectionDialog = false
            }
        )
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
        ) { innerPadding ->
            val contentModifier = Modifier.padding(innerPadding)
            when (currentDestination) {
                AppDestinations.HOME -> {
                    val currentActiveBlocker = activeManualBlocker

                    val breaksAllowed = if (activeSchedule != null) {
                        activeSchedule.breaksEnabled
                    } else {
                        sessionBreaksEnabled
                    }

                    val effectiveBreakDuration = if (activeSchedule != null) {
                        activeSchedule.breakDurationMinutes
                    } else {
                        breakDurationMinutes
                    }
                    val effectiveMaxBreaks = if (activeSchedule != null) {
                        activeSchedule.maxBreaksPerSession
                    } else {
                        maxBreaksPerSession
                    }

                    val emergencyBreakAvailable = System.currentTimeMillis() >= lastEmergencyBreakMillis + (emergencyBreakCadenceWeeks * 7L * 24 * 60 * 60 * 1000)
                    val emergencyBreakDaysRemaining = if (!emergencyBreakAvailable) {
                        val nextAvailable = lastEmergencyBreakMillis + (emergencyBreakCadenceWeeks * 7L * 24 * 60 * 60 * 1000)
                        ((nextAvailable - System.currentTimeMillis()) / (24 * 60 * 60 * 1000) + 1).toInt()
                    } else 0

                    Greeting(
                        focusMode = focusMode,
                        activeTagId = focusTagId,
                        namedTags = namedTags,
                        activeBlocker = currentActiveBlocker,
                        activeSchedule = activeSchedule,
                        blockerLists = blockerLists,
                        focusPresets = focusPresets,
                        selectedPresetId = selectedPresetId,
                        focusDurationMinutes = focusDurationMinutes,
                        focusTimeRemaining = focusTimeRemaining,
                        isOnBreak = isOnBreak,
                        breakTimeRemaining = breakTimeRemaining,
                        breaksUsedThisSession = breaksUsedThisSession,
                        maxBreaksPerSession = effectiveMaxBreaks,
                        breaksAllowed = breaksAllowed,
                        sessionBreaksEnabled = sessionBreaksEnabled,
                        hideStopButton = hideStopButton,
                        nfcLockMode = nfcLockMode,
                        emergencyBreakAvailable = emergencyBreakAvailable,
                        emergencyBreakDaysRemaining = emergencyBreakDaysRemaining,
                        currentStreak = currentStreak,
                        onPresetSelected = { preset ->
                            selectedPresetId = preset.id
                            activeManualBlocker = blockerLists.find { it.name == preset.blockerName }
                            focusDurationMinutes = preset.durationMinutes
                            sharedPreferences.edit().putInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, preset.durationMinutes).apply()
                            sessionBreaksEnabled = preset.breaksEnabled
                            sharedPreferences.edit().putBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, preset.breaksEnabled).apply()
                        },
                        onBlockerSelected = { blocker ->
                            activeManualBlocker = blocker
                            selectedPresetId = null
                        },
                        onDurationSelected = { duration ->
                            focusDurationMinutes = duration
                            sharedPreferences.edit().putInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, duration).apply()
                            selectedPresetId = null
                        },
                        onSessionBreaksToggled = { enabled ->
                            sessionBreaksEnabled = enabled
                            sharedPreferences.edit().putBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, enabled).apply()
                            selectedPresetId = null
                        },
                        onStartClicked = {
                            if (focusMode) {
                                if (activeSchedule != null) {
                                     if (activeSchedule.unbindingTalismanId == null) {
                                          // Record session before stopping
                                          recordSession(activeSchedule.blockerName, breaksUsedThisSession)
                                          onDispelSchedule()
                                          manualFocusMode = false
                                     }
                                } else {
                                    // Record session before stopping
                                    activeManualBlocker?.let { recordSession(it.name, breaksUsedThisSession) }
                                    manualFocusMode = false
                                }
                            } else {
                                if (activeManualBlocker != null) {
                                    if (focusDurationMinutes > 0) {
                                        focusTimeRemaining = focusDurationMinutes * 60
                                        sharedPreferences.edit().putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, focusTimeRemaining).apply()
                                    }
                                    // Record session start time
                                    sharedPreferences.edit().putLong(Constants.PrefsKeys.SESSION_START_TIME, System.currentTimeMillis()).apply()
                                    manualFocusMode = true
                                } else {
                                    if (blockerLists.isNotEmpty()) {
                                        showBlockerSelectionDialog = true
                                    }
                                }
                            }
                        },
                        onBlockerSelectorClicked = {
                            if (activeSchedule == null) {
                                showBlockerSelectionDialog = true
                            }
                        },
                        onTakeBreak = {
                            if (breaksAllowed && breaksUsedThisSession < effectiveMaxBreaks && !isOnBreak) {
                                isOnBreak = true
                                breakTimeRemaining = effectiveBreakDuration * 60
                                breaksUsedThisSession += 1
                                sharedPreferences.edit()
                                    .putBoolean(Constants.PrefsKeys.IS_ON_BREAK, true)
                                    .putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, breakTimeRemaining)
                                    .putInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, breaksUsedThisSession)
                                    .apply()
                                DndController.updateDndState(context)
                            }
                        },
                        onEndBreak = {
                            isOnBreak = false
                            breakTimeRemaining = 0
                            sharedPreferences.edit()
                                .putBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)
                                .putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, 0)
                                .apply()
                            DndController.updateDndState(context)
                        },
                        onScanQrCode = onScanQrCode,
                        onEmergencyBreak = {
                            isOnBreak = true
                            breakTimeRemaining = effectiveBreakDuration * 60
                            // Don't increment breaksUsedThisSession for emergency breaks
                            lastEmergencyBreakMillis = System.currentTimeMillis()
                            sharedPreferences.edit()
                                .putBoolean(Constants.PrefsKeys.IS_ON_BREAK, true)
                                .putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, breakTimeRemaining)
                                .putLong(Constants.PrefsKeys.LAST_EMERGENCY_BREAK_MILLIS, lastEmergencyBreakMillis)
                                .apply()
                            DndController.updateDndState(context)
                        },
                        modifier = contentModifier
                    )
                }

                AppDestinations.BLOCK -> {
                    if (screen != Screen.BlockerList) {
                        BackHandler {
                            screen = Screen.BlockerList
                        }
                    }

                    val activeBlockerName = if (focusMode) {
                        activeSchedule?.blockerName ?: activeManualBlocker?.name
                    } else null

                    when (screen) {
                        is Screen.BlockerList -> BlockerListScreen(
                            blockerLists = blockerLists,
                            focusPresets = focusPresets,
                            namedTags = namedTags,
                            activeBlockerName = activeBlockerName,
                            onBlockerClick = {
                                selectedBlocker = it
                                screen = Screen.EditBlocker
                            },
                            onCreateClick = {
                                screen = Screen.CreateBlocker
                            },
                            onSaveFocusPreset = onSaveFocusPreset,
                            onDeleteFocusPreset = onDeleteFocusPreset,
                            modifier = contentModifier
                        )
                        is Screen.CreateBlocker -> CreateBlockerScreen(
                            onSaveBlocker = {
                                onSaveBlocker(it)
                                screen = Screen.BlockerList
                            },
                            installedApps = installedApps,
                            modifier = contentModifier
                        )
                        is Screen.EditBlocker -> selectedBlocker?.let {
                            EditBlockerScreen(
                                blocker = it,
                                onSaveBlocker = { blockerToSave ->
                                    onSaveBlocker(blockerToSave)
                                    screen = Screen.BlockerList
                                },
                                onDeleteBlocker = { blockerToDelete ->
                                    onDeleteBlocker(blockerToDelete)
                                    screen = Screen.BlockerList
                                },
                                installedApps = installedApps,
                                modifier = contentModifier
                            )
                        }
                    }
                }

                AppDestinations.SCHEDULE -> {
                    if (scheduleScreen != ScheduleScreenRoute.ScheduleList) {
                        BackHandler {
                            scheduleScreen = ScheduleScreenRoute.ScheduleList
                        }
                    }
                    when(val currentScreen = scheduleScreen) {
                        is ScheduleScreenRoute.ScheduleList -> ScheduleListScreen(
                            schedules = schedules,
                            onScheduleClick = { schedule -> scheduleScreen = ScheduleScreenRoute.EditSchedule(schedule) },
                            onCreateClick = { scheduleScreen = ScheduleScreenRoute.CreateSchedule },
                            onDeleteSchedule = onDeleteSchedule,
                            activeScheduleId = activeScheduleId,
                            autoTriggers = autoTriggers,
                            focusPresets = focusPresets,
                            onSaveAutoTrigger = onSaveAutoTrigger,
                            onDeleteAutoTrigger = onDeleteAutoTrigger,
                            modifier = contentModifier
                        )
                        is ScheduleScreenRoute.CreateSchedule -> ScheduleEditorScreen(
                            scheduleToEdit = null,
                            blockerLists = blockerLists,
                            namedTags = namedTags,
                            onSaveSchedule = {
                                onSaveSchedule(it)
                                scheduleScreen = ScheduleScreenRoute.ScheduleList
                            },
                            onCancel = { scheduleScreen = ScheduleScreenRoute.ScheduleList },
                            modifier = contentModifier
                        )
                        is ScheduleScreenRoute.EditSchedule -> ScheduleEditorScreen(
                            scheduleToEdit = currentScreen.schedule,
                            blockerLists = blockerLists,
                            namedTags = namedTags,
                            onSaveSchedule = {
                                onSaveSchedule(it)
                                scheduleScreen = ScheduleScreenRoute.ScheduleList
                            },
                            onCancel = { scheduleScreen = ScheduleScreenRoute.ScheduleList },
                            modifier = contentModifier
                        )
                    }
                }

                AppDestinations.PROFILE -> {
                    LaunchedEffect(Unit) {
                        isNotificationListenerEnabled = notificationManager.isNotificationPolicyAccessGranted
                    }
                    ProfileScreen(
                        lastScannedTagId = lastScannedTagId,
                        namedTags = namedTags,
                        onSaveTag = onSaveTag,
                        onDeleteTag = onDeleteTag,
                        modifier = contentModifier,
                        breakDurationMinutes = breakDurationMinutes,
                        maxBreaksPerSession = maxBreaksPerSession,
                        onBreakDurationChanged = { newDuration ->
                            breakDurationMinutes = newDuration
                            sharedPreferences.edit().putInt(Constants.PrefsKeys.BREAK_DURATION_MINUTES, newDuration).apply()
                        },
                        onMaxBreaksChanged = { newMax ->
                            maxBreaksPerSession = newMax
                            sharedPreferences.edit().putInt(Constants.PrefsKeys.MAX_BREAKS_PER_SESSION, newMax).apply()
                        },
                        themeMode = themeMode,
                        onThemeModeChanged = onThemeModeChanged,
                        muteNotifications = muteBlockedNotifications,
                        isNotificationListenerEnabled = isNotificationListenerEnabled,
                        onMuteNotificationsChanged = { enabled ->
                            muteBlockedNotifications = enabled
                            sharedPreferences.edit().putBoolean(Constants.PrefsKeys.MUTE_BLOCKED_NOTIFICATIONS, enabled).apply()
                            DndController.updateDndState(context)
                        },
                        onOpenNotificationSettings = {
                            val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            context.startActivity(intent)
                        },
                        focusMode = focusMode,
                        hideStopButton = hideStopButton,
                        onHideStopButtonChanged = { enabled ->
                            hideStopButton = enabled
                            sharedPreferences.edit().putBoolean(Constants.PrefsKeys.HIDE_STOP_BUTTON, enabled).apply()
                        },
                        emergencyBreakCadenceWeeks = emergencyBreakCadenceWeeks,
                        onEmergencyBreakCadenceChanged = { newCadence ->
                            emergencyBreakCadenceWeeks = newCadence
                            sharedPreferences.edit().putInt(Constants.PrefsKeys.EMERGENCY_BREAK_CADENCE_WEEKS, newCadence).apply()
                        },
                        nfcLockMode = nfcLockMode,
                        onNfcLockModeChanged = { enabled ->
                            nfcLockMode = enabled
                            sharedPreferences.edit().putBoolean(Constants.PrefsKeys.NFC_LOCK_MODE, enabled).apply()
                        },
                        installedApps = installedApps,
                        appTimeLimits = appTimeLimits,
                        onSaveAppTimeLimit = onSaveAppTimeLimit,
                        onDeleteAppTimeLimit = onDeleteAppTimeLimit
                    )
                }

                AppDestinations.INSIGHTS -> {
                    UsageStatsScreen(
                        blockerLists = blockerLists,
                        installedApps = installedApps,
                        focusSessions = focusSessions,
                        currentStreak = currentStreak,
                        longestStreak = longestStreak,
                        blockEvents = blockEvents,
                        appTimeLimits = appTimeLimits,
                        modifier = contentModifier
                    )
                }
            }
        }
    }
}

data class AppInfo(
    val name: String,
    val packageName: String
)

data class Schedule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val blockerName: String,
    val days: Set<DayOfWeek>,
    val startTime: String, // "HH:mm"
    val endTime: String, // "HH:mm"
    val unbindingTalismanId: String? = null,
    val breaksEnabled: Boolean = true,
    val breakDurationMinutes: Int = 5,
    val maxBreaksPerSession: Int = 3
)

data class FocusPreset(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val blockerName: String,
    val durationMinutes: Int,
    val breaksEnabled: Boolean,
    val talismanId: String? = null,
    val action: PresetAction? = PresetAction.TOGGLE,
    val tempDurationMinutes: Int? = 30
)

enum class PresetAction { TOGGLE, TEMP_ENABLE, TEMP_DISABLE }

enum class DayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Focus", Icons.Filled.AutoFixHigh),
    BLOCK("Spells", Icons.Filled.Lock),
    SCHEDULE("Rituals", Icons.Filled.DateRange),
    INSIGHTS("Insights", Icons.Filled.Insights),
    PROFILE("Wizard", Icons.Filled.Person),
}

fun formatDuration(minutes: Int): String {
    return when {
        minutes == 0 -> "Unlimited"
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} hour${if (minutes / 60 > 1) "s" else ""}"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}

@Preview(showBackground = true)
@PreviewScreenSizes
@Composable
fun GreetingPreview() {
    FocusPocusTheme {
        FocusPocusApp(null, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), false, null, 0, ThemeMode.SYSTEM, {}, {_ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, 0, 0, emptyList(), {}, {}, emptyMap(), { _, _ -> }, {})
    }
}
