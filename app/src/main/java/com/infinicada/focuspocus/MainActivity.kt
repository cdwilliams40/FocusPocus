package com.infinicada.focuspocus

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.infinicada.focuspocus.ui.theme.FocusPocusTheme
import com.infinicada.focuspocus.ui.theme.ThemeMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import java.util.UUID

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        focusTagId = sharedPreferences.getString(Constants.PrefsKeys.FOCUS_TAG_ID, null)
        activeScheduleId = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)
        themeMode = ThemeMode.valueOf(sharedPreferences.getString(Constants.PrefsKeys.THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)

        loadNamedTags()
        loadBlockerLists()
        loadInstalledApps()
        loadSchedules()
        loadFocusPresets()

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
                    modifier = Modifier
                )
            }
        }
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
                // Clear corrupted data and notify user
                sharedPreferences.edit().remove(Constants.PrefsKeys.SCHEDULES).apply()
                Toast.makeText(this, "Ritual data was corrupted and has been reset", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveSchedule(newSchedule: Schedule) {
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

    private fun loadInstalledApps() {
        val pm = packageManager
        installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter {
                // Include apps that have a launch intent (user-facing apps)
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
                // Clear corrupted data and notify user
                sharedPreferences.edit().remove(Constants.PrefsKeys.BLOCKER_LISTS).apply()
                Toast.makeText(this, "Enchantment data was corrupted - restored defaults", Toast.LENGTH_LONG).show()
            }
        } else {
            blockerLists = listOf(Blocker("Default", BlockerMode.BLACKLIST, listOf("com.google.android.youtube")))
        }
    }

    private fun saveBlocker(newBlocker: Blocker) {
        val updatedBlockers = blockerLists.filterNot { it.name == newBlocker.name } + newBlocker
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
                // Clear corrupted data and notify user
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
        // Save defaults
        val defaultJson = gson.toJson(focusPresets)
        sharedPreferences.edit().putString(Constants.PrefsKeys.FOCUS_PRESETS, defaultJson).apply()
    }

    private fun saveFocusPreset(preset: FocusPreset) {
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

    private fun loadNamedTags() {
        val json = sharedPreferences.getString(Constants.PrefsKeys.NAMED_TAGS, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<NamedTag>>() {}.type
                namedTags = gson.fromJson(json, type)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error parsing named tags JSON: ${e.message}", e)
                namedTags = emptyList()
                // Clear corrupted data and notify user
                sharedPreferences.edit().remove(Constants.PrefsKeys.NAMED_TAGS).apply()
                Toast.makeText(this, "Talisman data was corrupted and has been reset", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveNamedTag(name: String) {
        lastScannedTagId?.let {
            val newTag = NamedTag(it, name)
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

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
        isServiceEnabled = isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)
        // Refresh activeScheduleId in case it was set by the service while the app was in background
        activeScheduleId = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
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
                    val isManualFocusActive = sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
                    if (isManualFocusActive) {
                        // Turn off focus mode
                        sharedPreferences.edit()
                            .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
                            .putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, 0)
                            .apply()
                        nfcTriggerCount++
                        Toast.makeText(this, "${boundPreset.name} Dispelled!", Toast.LENGTH_SHORT).show()
                    } else {
                        // Activate the preset
                        val blocker = blockerLists.find { b -> b.name == boundPreset.blockerName }
                        if (blocker != null) {
                            val focusTimeRemaining = if (boundPreset.durationMinutes > 0) boundPreset.durationMinutes * 60 else 0
                            sharedPreferences.edit()
                                .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
                                .putString(Constants.PrefsKeys.ACTIVE_BLOCKER, blocker.name)
                                .putInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, boundPreset.durationMinutes)
                                .putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, focusTimeRemaining)
                                .putBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, boundPreset.breaksEnabled)
                                .apply()
                            nfcTriggerCount++
                            Toast.makeText(this, "${boundPreset.name} Cast!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Enchantment missing for ${boundPreset.name}", Toast.LENGTH_SHORT).show()
                        }
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
        mutableIntStateOf(sharedPreferences.getInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, 0)) // 0 = unlimited
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
            // Update DND state (re-enable muting after break)
            DndController.updateDndState(context)
        }
    }

    // Focus session countdown timer
    LaunchedEffect(manualFocusMode, focusTimeRemaining, isOnBreak) {
        if (manualFocusMode && focusTimeRemaining > 0 && !isOnBreak) {
            delay(1000L)
            focusTimeRemaining -= 1
            sharedPreferences.edit().putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, focusTimeRemaining).apply()

            // Auto-end session when timer reaches 0
            if (focusTimeRemaining <= 0) {
                manualFocusMode = false
                sharedPreferences.edit()
                    .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
                    .putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, 0)
                    .apply()
            }
        }
    }

    // Consolidated effect for focus mode state changes
    // Combines state persistence and cleanup to avoid race conditions
    LaunchedEffect(manualFocusMode, activeManualBlocker) {
        val editor = sharedPreferences.edit()
            .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, manualFocusMode)
            .putString(Constants.PrefsKeys.ACTIVE_BLOCKER, activeManualBlocker?.name)

        // Reset breaks and focus timer when focus mode ends
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
        // Update DND state when focus mode changes
        DndController.updateDndState(context)
    }

    // Sync UI with external changes to activeScheduleId (e.g. from onTagDiscovered)
    LaunchedEffect(activeScheduleId) {
         if (activeScheduleId == null) {
              manualFocusMode = sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
         } else {
             manualFocusMode = true
         }
    }

    // Sync UI with NFC preset activation
    LaunchedEffect(nfcTriggerCount) {
        if (nfcTriggerCount > 0) {
            manualFocusMode = sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
            val activeBlockerName = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null)
            activeManualBlocker = blockerLists.find { it.name == activeBlockerName }
            focusDurationMinutes = sharedPreferences.getInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, 0)
            focusTimeRemaining = sharedPreferences.getInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, 0)
            sessionBreaksEnabled = sharedPreferences.getBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, true)
        }
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
                if (manualFocusMode) {
                     // Stay in focus mode, just switch blocker
                } else {
                    // Just select it
                }
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
            // containerColor handled by Theme now mostly, but we can override for "Focus Mode"
        ) { innerPadding ->
            val contentModifier = Modifier.padding(innerPadding)
            when (currentDestination) {
                AppDestinations.HOME -> {
                    // Always rely on activeManualBlocker (selected on Home) regardless of trigger
                    val currentActiveBlocker = activeManualBlocker

                    // Check if breaks are allowed for current context
                    val breaksAllowed = if (activeSchedule != null) {
                        activeSchedule.breaksEnabled
                    } else {
                        sessionBreaksEnabled // Manual focus mode uses session toggle
                    }

                    // Use schedule-specific break settings when a schedule is active
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
                            selectedPresetId = null // Custom selection
                        },
                        onDurationSelected = { duration ->
                            focusDurationMinutes = duration
                            sharedPreferences.edit().putInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, duration).apply()
                            selectedPresetId = null // Custom selection
                        },
                        onSessionBreaksToggled = { enabled ->
                            sessionBreaksEnabled = enabled
                            sharedPreferences.edit().putBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, enabled).apply()
                            selectedPresetId = null // Custom selection
                        },
                        onStartClicked = {
                            if (focusMode) {
                                // If active schedule, don't allow stopping via this button if bound to talisman
                                if (activeSchedule != null) {
                                     if (activeSchedule.unbindingTalismanId == null) {
                                          onDispelSchedule()
                                          manualFocusMode = false
                                     }
                                } else {
                                    manualFocusMode = false
                                }
                            } else {
                                if (activeManualBlocker != null) {
                                    // Initialize timer when starting focus session
                                    if (focusDurationMinutes > 0) {
                                        focusTimeRemaining = focusDurationMinutes * 60
                                        sharedPreferences.edit().putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, focusTimeRemaining).apply()
                                    }
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
                                // Update DND state (disable muting during break)
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
                            // Update DND state (re-enable muting after break)
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

                    when (screen) {
                        is Screen.BlockerList -> BlockerListScreen(
                            blockerLists = blockerLists,
                            focusPresets = focusPresets,
                            namedTags = namedTags,
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
                    // Refresh notification listener permission status when this tab is shown
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
                        focusMode = focusMode
                    )
                }

                AppDestinations.INSIGHTS -> {
                    UsageStatsScreen(
                        blockerLists = blockerLists,
                        installedApps = installedApps,
                        modifier = contentModifier
                    )
                }
            }
        }
    }
}

@Composable
fun ScheduleListScreen(
    schedules: List<Schedule>,
    onScheduleClick: (Schedule) -> Unit,
    onCreateClick: () -> Unit,
    onDeleteSchedule: (Schedule) -> Unit,
    activeScheduleId: String? = null,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateClick) {
                Icon(Icons.Default.Add, contentDescription = "Create new schedule")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Scheduled Rituals", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn {
                items(schedules) { schedule ->
                    val isActive = schedule.id == activeScheduleId
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .then(
                                if (isActive) Modifier else Modifier.clickable { onScheduleClick(schedule) }
                            ),
                        colors = if (isActive) {
                            CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        } else {
                            CardDefaults.elevatedCardColors()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(schedule.name, style = MaterialTheme.typography.titleMedium)
                                    if (isActive) {
                                        Text(
                                            "Active",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                val isOvernight = run {
                                    val startParts = schedule.startTime.split(":")
                                    val endParts = schedule.endTime.split(":")
                                    if (startParts.size == 2 && endParts.size == 2) {
                                        val startMins = startParts[0].toIntOrNull()?.times(60)?.plus(startParts[1].toIntOrNull() ?: 0) ?: 0
                                        val endMins = endParts[0].toIntOrNull()?.times(60)?.plus(endParts[1].toIntOrNull() ?: 0) ?: 0
                                        endMins <= startMins
                                    } else false
                                }
                                Text(
                                    if (isOvernight) "${schedule.startTime} - ${schedule.endTime} (overnight)"
                                    else "${schedule.startTime} - ${schedule.endTime}"
                                )
                                Text(schedule.days.joinToString { it.name.take(3) })
                                if (schedule.unbindingTalismanId != null) {
                                    Text("Bound to Talisman", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                }
                                if (isActive) {
                                    Text(
                                        "Cannot edit while active",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Button(
                                onClick = { onDeleteSchedule(schedule) },
                                enabled = !isActive,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    scheduleToEdit: Schedule? = null,
    blockerLists: List<Blocker>,
    namedTags: List<NamedTag>,
    onSaveSchedule: (Schedule) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(scheduleToEdit?.name ?: "") }
    var selectedBlocker by remember {
        mutableStateOf(
            if (scheduleToEdit != null) blockerLists.find { it.name == scheduleToEdit.blockerName } else null
        )
    }
    var showBlockerDialog by remember { mutableStateOf(false) }

    var selectedTalisman by remember {
        mutableStateOf(
             if (scheduleToEdit?.unbindingTalismanId != null) namedTags.find { it.id == scheduleToEdit.unbindingTalismanId } else null
        )
    }
    var showTalismanDialog by remember { mutableStateOf(false) }

    var breaksEnabled by remember { mutableStateOf(scheduleToEdit?.breaksEnabled ?: true) }
    var breakDurationMinutes by remember { mutableIntStateOf(scheduleToEdit?.breakDurationMinutes ?: 5) }
    var maxBreaksPerSession by remember { mutableIntStateOf(scheduleToEdit?.maxBreaksPerSession ?: 3) }

    var selectedDays by remember { mutableStateOf(scheduleToEdit?.days ?: emptySet<DayOfWeek>()) }

    // Initial times
    val (startHour, startMinute) = remember(scheduleToEdit) {
         val parts = scheduleToEdit?.startTime?.split(":")
         if (parts != null && parts.size >= 2) {
             (parts[0].toIntOrNull() ?: 9) to (parts[1].toIntOrNull() ?: 0)
         } else {
             9 to 0
         }
    }
    val startTimeState = rememberTimePickerState(initialHour = startHour, initialMinute = startMinute)

    val (endHour, endMinute) = remember(scheduleToEdit) {
         val parts = scheduleToEdit?.endTime?.split(":")
         if (parts != null && parts.size >= 2) {
             (parts[0].toIntOrNull() ?: 17) to (parts[1].toIntOrNull() ?: 0)
         } else {
             17 to 0
         }
    }
    val endTimeState = rememberTimePickerState(initialHour = endHour, initialMinute = endMinute)

    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    // Validate times - allow overnight schedules (end time before start time)
    val startTimeMinutes = startTimeState.hour * 60 + startTimeState.minute
    val endTimeMinutes = endTimeState.hour * 60 + endTimeState.minute
    val isOvernightSchedule = endTimeMinutes <= startTimeMinutes
    val isTimeValid = startTimeMinutes != endTimeMinutes  // Only invalid if times are identical
    val timeValidationError = if (!isTimeValid) "Start and end times cannot be the same" else null

    if (showBlockerDialog) {
        BlockerSelectionDialog(
            blockerLists = blockerLists,
            onBlockerSelected = {
                selectedBlocker = it
                showBlockerDialog = false
             },
            onDismissRequest = { showBlockerDialog = false }
        )
    }

    if (showTalismanDialog) {
        AlertDialog(
            onDismissRequest = { showTalismanDialog = false },
            title = { Text("Select Unbinding Talisman") },
            text = {
                LazyColumn {
                    items(namedTags) { tag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTalisman = tag
                                    showTalismanDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Text(tag.name, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTalisman = null
                                    showTalismanDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("None (Unbind Freely)", modifier = Modifier.padding(start = 40.dp))
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showStartTimePicker) {
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            confirmButton = {
                Button(onClick = { showStartTimePicker = false }) { Text("OK") }
            },
            text = {
                TimePicker(state = startTimeState)
            }
        )
    }

    if (showEndTimePicker) {
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            confirmButton = {
                Button(onClick = { showEndTimePicker = false }) { Text("OK") }
            },
            text = {
                TimePicker(state = endTimeState)
            }
        )
    }

    Column(modifier = modifier.padding(16.dp)) {
        Text(if (scheduleToEdit != null) "Refine Ritual" else "Concoct Ritual", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Ritual Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(onClick = { showBlockerDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedBlocker?.name ?: "Select Enchantment")
        }
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(onClick = { showTalismanDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedTalisman?.name?.let { "Unbind with: $it" } ?: "Optional: Bind to Talisman")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Breaks toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Allow Breaks")
            Switch(
                checked = breaksEnabled,
                onCheckedChange = { breaksEnabled = it }
            )
        }

        // Break settings (only shown when breaks are enabled)
        if (breaksEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Break Duration: $breakDurationMinutes minutes", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = breakDurationMinutes.toFloat(),
                        onValueChange = { breakDurationMinutes = it.toInt() },
                        valueRange = 1f..30f,
                        steps = 28,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Breaks Per Session: $maxBreaksPerSession", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = maxBreaksPerSession.toFloat(),
                        onValueChange = { maxBreaksPerSession = it.toInt() },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Days Active:")
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = selectedDays.contains(day),
                    onClick = {
                        selectedDays = if (selectedDays.contains(day)) {
                            selectedDays - day
                        } else {
                            selectedDays + day
                        }
                    },
                    label = { Text(day.name.take(1)) }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            // Start Time Picker Button
            OutlinedButton(
                onClick = { showStartTimePicker = true },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Start: %02d:%02d".format(startTimeState.hour, startTimeState.minute))
            }

            Spacer(modifier = Modifier.size(8.dp))

            // End Time Picker Button
            OutlinedButton(
                onClick = { showEndTimePicker = true },
                modifier = Modifier.weight(1f)
            ) {
                val endTimeText = if (isOvernightSchedule) {
                    "End: %02d:%02d (next day)".format(endTimeState.hour, endTimeState.minute)
                } else {
                    "End: %02d:%02d".format(endTimeState.hour, endTimeState.minute)
                }
                Text(text = endTimeText)
            }
        }

        // Show time validation error
        if (timeValidationError != null) {
            Text(
                text = timeValidationError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(onClick = onCancel) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.size(8.dp))
            Button(onClick = {
                selectedBlocker?.let {
                    onSaveSchedule(
                        Schedule(
                            id = scheduleToEdit?.id ?: UUID.randomUUID().toString(),
                            name = name,
                            blockerName = it.name,
                            days = selectedDays,
                            startTime = "%02d:%02d".format(startTimeState.hour, startTimeState.minute),
                            endTime = "%02d:%02d".format(endTimeState.hour, endTimeState.minute),
                            unbindingTalismanId = selectedTalisman?.id,
                            breaksEnabled = breaksEnabled,
                            breakDurationMinutes = breakDurationMinutes,
                            maxBreaksPerSession = maxBreaksPerSession
                        )
                    )
                }
            }, enabled = name.isNotBlank() && selectedBlocker != null && selectedDays.isNotEmpty() && isTimeValid) {
                Text("Save Ritual")
            }
        }
    }
}



@Composable
fun BlockerListScreen(
    blockerLists: List<Blocker>,
    focusPresets: List<FocusPreset>,
    namedTags: List<NamedTag>,
    onBlockerClick: (Blocker) -> Unit,
    onCreateClick: () -> Unit,
    onSaveFocusPreset: (FocusPreset) -> Unit,
    onDeleteFocusPreset: (FocusPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPresetEditorDialog by remember { mutableStateOf(false) }
    var presetToEdit by remember { mutableStateOf<FocusPreset?>(null) }

    if (showPresetEditorDialog) {
        PresetEditorDialog(
            presetToEdit = presetToEdit,
            blockerLists = blockerLists,
            namedTags = namedTags,
            onSave = { preset ->
                onSaveFocusPreset(preset)
                showPresetEditorDialog = false
                presetToEdit = null
            },
            onDismiss = {
                showPresetEditorDialog = false
                presetToEdit = null
            }
        )
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateClick) {
                Icon(Icons.Default.Add, contentDescription = "Create new enchantment")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            // Quick Spells Section
            item {
                Text("Quick Spells", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Preset focus configurations for quick access",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (focusPresets.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "No quick spells yet. Create one to quickly start focus with your favorite settings!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            items(focusPresets) { preset ->
                val blocker = blockerLists.find { it.name == preset.blockerName }
                val talisman = namedTags.find { it.id == preset.talismanId }
                val durationText = formatDuration(preset.durationMinutes)
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(preset.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${blocker?.name ?: preset.blockerName} • $durationText${if (preset.breaksEnabled) " • Breaks" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (blocker == null) {
                                Text(
                                    "Enchantment missing - please edit",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (talisman != null) {
                                Text(
                                    "Bound to: ${talisman.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                        Row {
                            OutlinedButton(
                                onClick = {
                                    presetToEdit = preset
                                    showPresetEditorDialog = true
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text("Edit")
                            }
                            Button(
                                onClick = { onDeleteFocusPreset(preset) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        presetToEdit = null
                        showPresetEditorDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Add Quick Spell")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Spells Section
            item {
                Text("Enchantments", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Banish distracting apps or shield only the ones you need. Each enchantment defines which apps are blocked during focus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (blockerLists.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "No enchantments yet. Tap the + button to create your first one!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            items(blockerLists) { blocker ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable { onBlockerClick(blocker) },
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(blocker.name, style = MaterialTheme.typography.titleMedium)
                            Text(if (blocker.mode == BlockerMode.BLACKLIST) "Banish" else "Shield", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
}


@Composable
fun CreateBlockerScreen(
    onSaveBlocker: (Blocker) -> Unit,
    installedApps: List<AppInfo>,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(BlockerMode.BLACKLIST) }
    var apps by remember { mutableStateOf(emptyList<String>()) }
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AppSelectionDialog(
            installedApps = installedApps,
            selectedApps = apps,
            onSave = { newApps ->
                apps = newApps
                showDialog = false
            },
            onDismissRequest = { showDialog = false }
        )
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Create Enchantment", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Enchantment Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selectedMode == BlockerMode.BLACKLIST,
                onClick = { selectedMode = BlockerMode.BLACKLIST }
            )
            Text("Banish (Blacklist)")
            RadioButton(
                selected = selectedMode == BlockerMode.WHITELIST,
                onClick = { selectedMode = BlockerMode.WHITELIST }
            )
            Text("Shield (Whitelist)")
        }

        Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Select Target Apps")
        }

        LazyColumn(
            modifier = Modifier
                .padding(top = 16.dp)
                .weight(1f)
        ) {
            items(apps) { appPackageName ->
                val appInfo = installedApps.find { it.packageName == appPackageName }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (appInfo != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            AppIcon(
                                packageName = appInfo.packageName,
                                contentDescription = appInfo.name,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(appInfo.name, modifier = Modifier.padding(start = 16.dp))
                        }
                    } else {
                        Text(appPackageName, modifier = Modifier.weight(1f))
                    }
                    Button(onClick = { apps = apps - appPackageName }) {
                        Text("Remove")
                    }
                }
            }
        }

        Button(
            onClick = { onSaveBlocker(Blocker(name, selectedMode, apps)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            enabled = name.isNotBlank()
        ) {
            Text("Save Enchantment")
        }
    }
}

@Composable
fun EditBlockerScreen(
    blocker: Blocker,
    onSaveBlocker: (Blocker) -> Unit,
    onDeleteBlocker: (Blocker) -> Unit,
    installedApps: List<AppInfo>,
    modifier: Modifier = Modifier
) {
    var selectedMode by remember { mutableStateOf(blocker.mode) }
    var apps by remember { mutableStateOf(blocker.apps) }
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AppSelectionDialog(
            installedApps = installedApps,
            selectedApps = apps,
            onSave = { newApps ->
                apps = newApps
                showDialog = false
            },
            onDismissRequest = { showDialog = false }
        )
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Edit Enchantment: ${blocker.name}", style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selectedMode == BlockerMode.BLACKLIST,
                onClick = { selectedMode = BlockerMode.BLACKLIST }
            )
            Text("Banish (Blacklist)")
            RadioButton(
                selected = selectedMode == BlockerMode.WHITELIST,
                onClick = { selectedMode = BlockerMode.WHITELIST }
            )
            Text("Shield (Whitelist)")
        }

        Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Select Target Apps")
        }

        LazyColumn(
            modifier = Modifier
                .padding(top = 16.dp)
                .weight(1f)
        ) {
            items(apps) { appPackageName ->
                val appInfo = installedApps.find { it.packageName == appPackageName }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (appInfo != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            AppIcon(
                                packageName = appInfo.packageName,
                                contentDescription = appInfo.name,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(appInfo.name, modifier = Modifier.padding(start = 16.dp))
                        }
                    } else {
                        Text(appPackageName, modifier = Modifier.weight(1f))
                    }
                    Button(onClick = { apps = apps - appPackageName }) {
                        Text("Remove")
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Button(
                onClick = { onSaveBlocker(blocker.copy(mode = selectedMode, apps = apps)) },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text("Save")
            }
            Button(
                onClick = { onDeleteBlocker(blocker) },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        }
    }
}

@Composable
fun AppSelectionDialog(
    installedApps: List<AppInfo>,
    selectedApps: List<String>,
    onSave: (List<String>) -> Unit,
    onDismissRequest: () -> Unit
) {
    var currentSelections by remember { mutableStateOf(selectedApps.toSet()) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Select Apps") },
        text = {
            LazyColumn {
                items(installedApps) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newSelections = currentSelections.toMutableSet()
                                if (currentSelections.contains(app.packageName)) {
                                    newSelections.remove(app.packageName)
                                } else {
                                    newSelections.add(app.packageName)
                                }
                                currentSelections = newSelections
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            packageName = app.packageName,
                            contentDescription = app.name,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = app.name,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp)
                        )
                        Checkbox(
                            checked = currentSelections.contains(app.packageName),
                            onCheckedChange = { isChecked ->
                                val newSelections = currentSelections.toMutableSet()
                                if (isChecked) {
                                    newSelections.add(app.packageName)
                                } else {
                                    newSelections.remove(app.packageName)
                                }
                                currentSelections = newSelections
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(currentSelections.toList()) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BlockerSelectionDialog(
    blockerLists: List<Blocker>,
    onBlockerSelected: (Blocker) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Select Enchantment") },
        text = {
            LazyColumn {
                items(blockerLists) { blocker ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBlockerSelected(blocker) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Text(
                            text = blocker.name,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onDismissRequest() }) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ProfileScreen(
    lastScannedTagId: String?,
    namedTags: List<NamedTag>,
    onSaveTag: (String) -> Unit,
    onDeleteTag: (NamedTag) -> Unit,
    breakDurationMinutes: Int,
    maxBreaksPerSession: Int,
    onBreakDurationChanged: (Int) -> Unit,
    onMaxBreaksChanged: (Int) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    muteNotifications: Boolean,
    isNotificationListenerEnabled: Boolean,
    onMuteNotificationsChanged: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    focusMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    var tagName by remember { mutableStateOf("") }

    LazyColumn(modifier = modifier.padding(16.dp)) {
        item {
            Text("Your Wizard Profile", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Notification Settings Card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Notifications", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mute During Focus")
                            Text(
                                "Silence notifications while focusing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = muteNotifications,
                            onCheckedChange = onMuteNotificationsChanged,
                            enabled = isNotificationListenerEnabled
                        )
                    }

                    if (!isNotificationListenerEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onOpenNotificationSettings,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Notification Access")
                        }
                        Text(
                            "Required to mute notifications during focus sessions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Theme Settings Card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onThemeModeChanged(mode) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = { onThemeModeChanged(mode) }
                            )
                            Text(
                                text = when (mode) {
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                    ThemeMode.SYSTEM -> "Match System"
                                },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Break Settings Card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Break Settings", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (focusMode) {
                        Text(
                            "Cannot change break settings while a spell is active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Break duration slider
                    Text(
                        "Break Duration: $breakDurationMinutes minutes",
                        color = if (focusMode) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = breakDurationMinutes.toFloat(),
                        onValueChange = { onBreakDurationChanged(it.toInt()) },
                        valueRange = 1f..30f,
                        steps = 28,
                        enabled = !focusMode,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Max breaks slider
                    Text(
                        "Breaks Per Session: $maxBreaksPerSession",
                        color = if (focusMode) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = maxBreaksPerSession.toFloat(),
                        onValueChange = { onMaxBreaksChanged(it.toInt()) },
                        valueRange = 1f..10f,
                        steps = 8,
                        enabled = !focusMode,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // NFC Talismans Card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("NFC Talismans", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    lastScannedTagId?.let {
                        Text("Last Scanned Talisman: $it")
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = tagName,
                            onValueChange = { tagName = it },
                            label = { Text("Talisman Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                onSaveTag(tagName)
                                tagName = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enchant Talisman")
                        }
                    } ?: Text("Scan an NFC tag to bind it.")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Text("Enchanted Items:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(namedTags) { tag ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tag.name, style = MaterialTheme.typography.titleMedium)
                    }
                    Button(
                        onClick = { onDeleteTag(tag) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Disenchant")
                    }
                }
            }
        }
    }
}

data class AppInfo(
    val name: String,
    val packageName: String
)

/**
 * Composable that loads an app icon lazily to avoid memory issues
 * when dealing with large lists of installed apps.
 */
@Composable
fun AppIcon(packageName: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
    }
    icon?.let {
        Image(
            bitmap = it.toBitmap().asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageStatsScreen(
    blockerLists: List<Blocker>,
    installedApps: List<AppInfo>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(UsageStatsHelper.hasUsageStatsPermission(context)) }
    var selectedTab by remember { mutableStateOf("Today") }
    var selectedBlockerFilter by remember { mutableStateOf<Blocker?>(null) }
    var filterExpanded by remember { mutableStateOf(false) }

    val usageStats = remember(hasPermission, selectedTab) {
        if (hasPermission) {
            if (selectedTab == "Today") {
                UsageStatsHelper.getTodayUsage(context)
            } else {
                UsageStatsHelper.getWeeklyUsage(context)
            }
        } else {
            emptyList()
        }
    }

    val filteredStats = remember(usageStats, selectedBlockerFilter) {
        if (selectedBlockerFilter == null) {
            usageStats
        } else {
            val blockedPackages = selectedBlockerFilter!!.apps.toSet()
            usageStats.filter { it.packageName in blockedPackages }
        }
    }

    val totalScreenTime = remember(filteredStats) {
        filteredStats.sumOf { it.totalTimeInForeground }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermission = UsageStatsHelper.hasUsageStatsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Usage Insights", style = MaterialTheme.typography.headlineMedium)
        }

        if (!hasPermission) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Usage Access Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "To view your app usage statistics, please grant usage access permission.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = {
                                UsageStatsHelper.openUsageAccessSettings(context)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Grant Usage Access")
                        }
                    }
                }
            }
        } else {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (selectedTab == "Today") "Today's Screen Time" else "Weekly Screen Time",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            UsageStatsHelper.formatDuration(totalScreenTime),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (selectedBlockerFilter != null) {
                            Text(
                                "Filtered by: ${selectedBlockerFilter!!.name}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == "Today",
                        onClick = { selectedTab = "Today" },
                        label = { Text("Today") }
                    )
                    FilterChip(
                        selected = selectedTab == "This Week",
                        onClick = { selectedTab = "This Week" },
                        label = { Text("This Week") }
                    )
                }
            }

            item {
                ExposedDropdownMenuBox(
                    expanded = filterExpanded,
                    onExpandedChange = { filterExpanded = !filterExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedBlockerFilter?.name ?: "All Apps",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Filter by Enchantment") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = filterExpanded,
                        onDismissRequest = { filterExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Apps") },
                            onClick = {
                                selectedBlockerFilter = null
                                filterExpanded = false
                            }
                        )
                        blockerLists.forEach { blocker ->
                            DropdownMenuItem(
                                text = { Text(blocker.name) },
                                onClick = {
                                    selectedBlockerFilter = blocker
                                    filterExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "App Usage",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (filteredStats.isEmpty()) {
                item {
                    Text(
                        "No usage data available for this period.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(filteredStats) { appUsage ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppIcon(
                                packageName = appUsage.packageName,
                                contentDescription = appUsage.appName,
                                modifier = Modifier.size(40.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    appUsage.appName,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    appUsage.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                UsageStatsHelper.formatDuration(appUsage.totalTimeInForeground),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

data class Schedule(
    val id: String = UUID.randomUUID().toString(),
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
    val id: String = UUID.randomUUID().toString(),
    val name: String,              // "Work Mode", "Nighttime", etc.
    val blockerName: String,       // References existing Blocker by name
    val durationMinutes: Int,      // 0 = unlimited, otherwise duration
    val breaksEnabled: Boolean,
    val talismanId: String? = null // Optional: bind to NFC talisman for quick activation
)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpellSelectorDropdown(
    blockerLists: List<Blocker>,
    selectedBlocker: Blocker?,
    enabled: Boolean,
    onBlockerSelected: (Blocker) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedBlocker?.name ?: "Select Enchantment",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            leadingIcon = {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = if (selectedBlocker != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            blockerLists.forEach { blocker ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Column {
                                Text(blocker.name)
                                Text(
                                    text = if (blocker.mode == BlockerMode.BLACKLIST) "Banish" else "Shield",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = {
                        onBlockerSelected(blocker)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationSelectorDropdown(
    selectedDuration: Int,
    enabled: Boolean,
    onDurationSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val durations = listOf(
        15 to "15 minutes",
        25 to "25 minutes",
        45 to "45 minutes",
        60 to "1 hour",
        120 to "2 hours",
        0 to "Unlimited"
    )

    val selectedLabel = durations.find { it.first == selectedDuration }?.second ?: "Select Duration"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Duration") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            durations.forEach { (minutes, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onDurationSelected(minutes)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun PresetChipRow(
    presets: List<FocusPreset>,
    selectedPresetId: String?,
    onPresetSelected: (FocusPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { preset ->
            FilterChip(
                selected = selectedPresetId == preset.id,
                onClick = { onPresetSelected(preset) },
                label = { Text(preset.name) },
                leadingIcon = if (selectedPresetId == preset.id) {
                    { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
        // "Custom" chip shown when no preset matches
        FilterChip(
            selected = selectedPresetId == null,
            onClick = { /* Custom is selected by modifying any setting */ },
            label = { Text("Custom") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetEditorDialog(
    presetToEdit: FocusPreset?,
    blockerLists: List<Blocker>,
    namedTags: List<NamedTag>,
    onSave: (FocusPreset) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(presetToEdit?.name ?: "") }
    var selectedBlocker by remember {
        mutableStateOf(
            if (presetToEdit != null) blockerLists.find { it.name == presetToEdit.blockerName }
            else blockerLists.firstOrNull()
        )
    }
    var selectedDuration by remember { mutableIntStateOf(presetToEdit?.durationMinutes ?: 25) }
    var breaksEnabled by remember { mutableStateOf(presetToEdit?.breaksEnabled ?: true) }
    var selectedTalisman by remember {
        mutableStateOf(
            if (presetToEdit?.talismanId != null) namedTags.find { it.id == presetToEdit.talismanId }
            else null
        )
    }

    var blockerDropdownExpanded by remember { mutableStateOf(false) }
    var durationDropdownExpanded by remember { mutableStateOf(false) }
    var talismanDropdownExpanded by remember { mutableStateOf(false) }

    val durations = listOf(
        15 to "15 minutes",
        25 to "25 minutes",
        45 to "45 minutes",
        60 to "1 hour",
        120 to "2 hours",
        240 to "4 hours",
        480 to "8 hours",
        0 to "Unlimited"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (presetToEdit != null) "Edit Quick Spell" else "Create Quick Spell") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Blocker selection dropdown
                ExposedDropdownMenuBox(
                    expanded = blockerDropdownExpanded,
                    onExpandedChange = { blockerDropdownExpanded = !blockerDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedBlocker?.name ?: "Select Enchantment",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Enchantment") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = blockerDropdownExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = blockerDropdownExpanded,
                        onDismissRequest = { blockerDropdownExpanded = false }
                    ) {
                        blockerLists.forEach { blocker ->
                            DropdownMenuItem(
                                text = { Text(blocker.name) },
                                onClick = {
                                    selectedBlocker = blocker
                                    blockerDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Duration selection dropdown
                ExposedDropdownMenuBox(
                    expanded = durationDropdownExpanded,
                    onExpandedChange = { durationDropdownExpanded = !durationDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = durations.find { it.first == selectedDuration }?.second ?: "Select Duration",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Duration") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = durationDropdownExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = durationDropdownExpanded,
                        onDismissRequest = { durationDropdownExpanded = false }
                    ) {
                        durations.forEach { (minutes, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedDuration = minutes
                                    durationDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Talisman binding dropdown
                ExposedDropdownMenuBox(
                    expanded = talismanDropdownExpanded,
                    onExpandedChange = { talismanDropdownExpanded = !talismanDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedTalisman?.name ?: "None (tap to select)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Bind to Talisman") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = talismanDropdownExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = talismanDropdownExpanded,
                        onDismissRequest = { talismanDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                selectedTalisman = null
                                talismanDropdownExpanded = false
                            }
                        )
                        namedTags.forEach { tag ->
                            DropdownMenuItem(
                                text = { Text(tag.name) },
                                onClick = {
                                    selectedTalisman = tag
                                    talismanDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Breaks toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Allow Breaks")
                    Switch(
                        checked = breaksEnabled,
                        onCheckedChange = { breaksEnabled = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedBlocker?.let { blocker ->
                        onSave(
                            FocusPreset(
                                id = presetToEdit?.id ?: UUID.randomUUID().toString(),
                                name = name,
                                blockerName = blocker.name,
                                durationMinutes = selectedDuration,
                                breaksEnabled = breaksEnabled,
                                talismanId = selectedTalisman?.id
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && selectedBlocker != null
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun Greeting(
    focusMode: Boolean,
    activeTagId: String?,
    namedTags: List<NamedTag>,
    activeBlocker: Blocker?,
    activeSchedule: Schedule?,
    blockerLists: List<Blocker>,
    focusPresets: List<FocusPreset>,
    selectedPresetId: String?,
    focusDurationMinutes: Int,
    focusTimeRemaining: Int,
    isOnBreak: Boolean,
    breakTimeRemaining: Int,
    breaksUsedThisSession: Int,
    maxBreaksPerSession: Int,
    breaksAllowed: Boolean,
    sessionBreaksEnabled: Boolean,
    onPresetSelected: (FocusPreset) -> Unit,
    onBlockerSelected: (Blocker) -> Unit,
    onDurationSelected: (Int) -> Unit,
    onSessionBreaksToggled: (Boolean) -> Unit,
    onStartClicked: () -> Unit,
    onBlockerSelectorClicked: () -> Unit,
    onTakeBreak: () -> Unit,
    onEndBreak: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTagName = namedTags.find { it.id == activeTagId }?.name
    val boundTalismanName = if (activeSchedule != null && activeSchedule.unbindingTalismanId != null) {
        namedTags.find { it.id == activeSchedule.unbindingTalismanId }?.name ?: "Unknown Talisman"
    } else null

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // Magical Status Text
            Text(
                text = when {
                    isOnBreak -> "On Break"
                    focusMode -> "Focus Spell Active"
                    else -> "Ready to Cast"
                },
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = when {
                    isOnBreak -> MaterialTheme.colorScheme.tertiary
                    focusMode -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onBackground
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Preset chip row (only when not in schedule, not in focus mode, and valid presets exist)
            val validPresets = focusPresets.filter { preset ->
                blockerLists.any { it.name == preset.blockerName }
            }
            if (activeSchedule == null && !focusMode && validPresets.isNotEmpty()) {
                Text(
                    text = "Quick Spells",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                PresetChipRow(
                    presets = validPresets,
                    selectedPresetId = selectedPresetId,
                    onPresetSelected = onPresetSelected,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Spell Selector Dropdown (only when not in schedule and not in focus mode)
            if (activeSchedule == null) {
                SpellSelectorDropdown(
                    blockerLists = blockerLists,
                    selectedBlocker = activeBlocker,
                    enabled = !focusMode,
                    onBlockerSelected = onBlockerSelected,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Duration Selector
                DurationSelectorDropdown(
                    selectedDuration = focusDurationMinutes,
                    enabled = !focusMode,
                    onDurationSelected = onDurationSelected,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Breaks Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Allow Breaks",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Switch(
                        checked = sessionBreaksEnabled,
                        onCheckedChange = onSessionBreaksToggled,
                        enabled = !focusMode
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Session Timer Display (when active and timed)
            if (focusMode && focusTimeRemaining > 0 && !isOnBreak) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val minutes = focusTimeRemaining / 60
                        val seconds = focusTimeRemaining % 60
                        Text(
                            text = "%d:%02d".format(minutes, seconds),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "remaining",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Break timer display
            if (isOnBreak) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val minutes = breakTimeRemaining / 60
                        val seconds = breakTimeRemaining % 60
                        Text(
                            text = "%d:%02d".format(minutes, seconds),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "break remaining",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // Active Schedule Info (when controlled by schedule)
            if (activeSchedule != null && activeBlocker != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(text = "Enchantment: ${activeBlocker.name}", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Ritual: ${activeSchedule.name}", style = MaterialTheme.typography.bodyMedium)
                        if (boundTalismanName != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Unbind with: $boundTalismanName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                        if (focusMode && breaksAllowed) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Breaks: $breaksUsedThisSession / $maxBreaksPerSession used",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Break info when in focus mode (manual mode)
            if (focusMode && activeSchedule == null && breaksAllowed) {
                Text(
                    "Breaks: $breaksUsedThisSession / $maxBreaksPerSession used",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Break button when in focus mode
            if (focusMode && breaksAllowed && !isOnBreak && breaksUsedThisSession < maxBreaksPerSession) {
                Button(
                    onClick = onTakeBreak,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text("Take a Break", color = MaterialTheme.colorScheme.onTertiary)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // End break early button
            if (isOnBreak) {
                Button(
                    onClick = onEndBreak,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("End Break Early")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Big Start/Stop Button
            val buttonColor = if (focusMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            val isButtonEnabled = activeSchedule == null || activeSchedule.unbindingTalismanId == null
            val canCast = activeBlocker != null

            Button(
                onClick = onStartClicked,
                modifier = Modifier.size(140.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isButtonEnabled && (focusMode || canCast)) buttonColor else Color.Gray
                ),
                enabled = isButtonEnabled && !isOnBreak && (focusMode || canCast)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoFixHigh,
                        contentDescription = if (focusMode) "Dispel spell" else "Cast spell",
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (focusMode) {
                            if (activeSchedule != null && activeSchedule.unbindingTalismanId != null) "Bound" else "Dispel"
                        } else "Cast",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            activeTagId?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Triggered by Talisman: ${activeTagName ?: it}")
            }

            if (activeSchedule != null && activeSchedule.unbindingTalismanId != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Scan $boundTalismanName to dispel", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Preview(showBackground = true)
@PreviewScreenSizes
@Composable
fun GreetingPreview() {
    FocusPocusTheme {
        FocusPocusApp(null, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), false, null, 0, ThemeMode.SYSTEM, {}, {_ -> }, {}, {}, {}, {}, {}, {}, {}, {})
    }
}
