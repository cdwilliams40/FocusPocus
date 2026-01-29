package com.example.focuspocus

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.focuspocus.ui.theme.FocusPocusTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var namedTags by mutableStateOf<List<NamedTag>>(emptyList())
    private var lastScannedTagId by mutableStateOf<String?>(null)
    private var focusTagId by mutableStateOf<String?>(null)
    private var blockerLists by mutableStateOf<List<Blocker>>(emptyList())
    private var schedules by mutableStateOf<List<Schedule>>(emptyList())
    private val gson = Gson()
    private var installedApps by mutableStateOf<List<AppInfo>>(emptyList())
    private var isServiceEnabled by mutableStateOf(false)
    private var activeScheduleId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        sharedPreferences = getSharedPreferences("FocusPocus", Context.MODE_PRIVATE)
        focusTagId = sharedPreferences.getString("focusTagId", null)
        activeScheduleId = sharedPreferences.getString("activeScheduleId", null)
        
        loadNamedTags()
        loadBlockerLists()
        loadInstalledApps()
        loadSchedules()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        setContent {
            FocusPocusTheme {
                FocusPocusApp(
                    focusTagId = focusTagId,
                    lastScannedTagId = lastScannedTagId,
                    namedTags = namedTags,
                    blockerLists = blockerLists,
                    installedApps = installedApps,
                    schedules = schedules,
                    isServiceEnabled = isServiceEnabled,
                    activeScheduleId = activeScheduleId,
                    onSaveTag = { name -> saveNamedTag(name) },
                    onDeleteTag = { tag -> deleteNamedTag(tag) },
                    onSaveBlocker = { newBlocker -> saveBlocker(newBlocker) },
                    onDeleteBlocker = { blockerToDelete -> deleteBlocker(blockerToDelete) },
                    onSaveSchedule = { newSchedule -> saveSchedule(newSchedule) },
                    onDeleteSchedule = { scheduleToDelete -> deleteSchedule(scheduleToDelete) },
                    onDispelSchedule = { dispelSchedule() },
                    modifier = Modifier
                )
            }
        }
    }

    private fun loadSchedules() {
        val json = sharedPreferences.getString("schedules", null)
        if (json != null) {
            val type = object : TypeToken<List<Schedule>>() {}.type
            schedules = gson.fromJson(json, type)
        }
    }

    private fun saveSchedule(newSchedule: Schedule) {
        val updatedSchedules = schedules.filterNot { it.id == newSchedule.id } + newSchedule
        val json = gson.toJson(updatedSchedules)
        sharedPreferences.edit().putString("schedules", json).apply()
        schedules = updatedSchedules
    }

    private fun deleteSchedule(scheduleToDelete: Schedule) {
        val updatedSchedules = schedules.filterNot { it.id == scheduleToDelete.id }
        val json = gson.toJson(updatedSchedules)
        sharedPreferences.edit().putString("schedules", json).apply()
        schedules = updatedSchedules
    }

    private fun dispelSchedule() {
        activeScheduleId = null
        sharedPreferences.edit()
            .remove("activeScheduleId")
            .putBoolean("manualFocusMode", false)
            .apply()
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
                    packageName = it.packageName,
                    icon = it.loadIcon(pm)
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    private fun loadBlockerLists() {
        val json = sharedPreferences.getString("blockerLists", null)
        if (json != null) {
            val type = object : TypeToken<List<Blocker>>() {}.type
            blockerLists = gson.fromJson(json, type)
        } else {
            blockerLists = listOf(Blocker("Default", BlockerMode.BLACKLIST, listOf("com.google.android.youtube")))
        }
    }

    private fun saveBlocker(newBlocker: Blocker) {
        val updatedBlockers = blockerLists.filterNot { it.name == newBlocker.name } + newBlocker
        val json = gson.toJson(updatedBlockers)
        sharedPreferences.edit().putString("blockerLists", json).apply()
        blockerLists = updatedBlockers
    }

    private fun deleteBlocker(blockerToDelete: Blocker) {
        val updatedBlockers = blockerLists.filterNot { it.name == blockerToDelete.name }
        val json = gson.toJson(updatedBlockers)
        sharedPreferences.edit().putString("blockerLists", json).apply()
        blockerLists = updatedBlockers
    }


    private fun loadNamedTags() {
        val json = sharedPreferences.getString("namedTags", null)
        if (json != null) {
            val type = object : TypeToken<List<NamedTag>>() {}.type
            namedTags = gson.fromJson(json, type)
        }
    }

    private fun saveNamedTag(name: String) {
        lastScannedTagId?.let {
            val newTag = NamedTag(it, name)
            val updatedTags = namedTags.filterNot { t -> t.id == newTag.id } + newTag
            val json = gson.toJson(updatedTags)
            sharedPreferences.edit().putString("namedTags", json).apply()
            namedTags = updatedTags
        }
    }

    private fun deleteNamedTag(tagToDelete: NamedTag) {
        val updatedTags = namedTags.filterNot { it.id == tagToDelete.id }
        val json = gson.toJson(updatedTags)
        sharedPreferences.edit().putString("namedTags", json).apply()
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

        // If accessibility service is not running, disable any active spells
        if (!isServiceEnabled) {
            val hadActiveSpell = sharedPreferences.getBoolean("manualFocusMode", false) ||
                    sharedPreferences.getString("focusTagId", null) != null ||
                    sharedPreferences.getString("activeScheduleId", null) != null

            if (hadActiveSpell) {
                sharedPreferences.edit()
                    .putBoolean("manualFocusMode", false)
                    .remove("focusTagId")
                    .remove("activeScheduleId")
                    .remove("activeBlocker")
                    .apply()

                focusTagId = null
                activeScheduleId = null

                Toast.makeText(this, "Spell dispelled - Accessibility service not running", Toast.LENGTH_LONG).show()
            }
        }

        // Refresh activeScheduleId in case it was set by the service while the app was in background
        activeScheduleId = sharedPreferences.getString("activeScheduleId", null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag?) {
        tag?.let {
            val newTagId = it.id.toHexString()
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

                val isNamed = namedTags.any { t -> t.id == newTagId }
                if (isNamed) {
                    if (focusTagId == null) {
                        focusTagId = newTagId
                        sharedPreferences.edit().putString("focusTagId", newTagId).apply()
                    } else {
                        focusTagId = null
                        sharedPreferences.edit().remove("focusTagId").apply()
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
    isServiceEnabled: Boolean,
    activeScheduleId: String?,
    onSaveTag: (String) -> Unit,
    onDeleteTag: (NamedTag) -> Unit,
    onSaveBlocker: (Blocker) -> Unit,
    onDeleteBlocker: (Blocker) -> Unit,
    onSaveSchedule: (Schedule) -> Unit,
    onDeleteSchedule: (Schedule) -> Unit,
    onDispelSchedule: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentDestination by remember { mutableStateOf(AppDestinations.HOME) }
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("FocusPocus", Context.MODE_PRIVATE) }

    var manualFocusMode by remember {
        mutableStateOf(sharedPreferences.getBoolean("manualFocusMode", false))
    }
    var activeManualBlocker by remember {
        val activeBlockerName = sharedPreferences.getString("activeBlocker", null)
        mutableStateOf(blockerLists.find { it.name == activeBlockerName })
    }

    LaunchedEffect(manualFocusMode, activeManualBlocker) {
        sharedPreferences.edit()
            .putBoolean("manualFocusMode", manualFocusMode)
            .putString("activeBlocker", activeManualBlocker?.name)
            .apply()
    }
    
    // Sync UI with external changes to activeScheduleId (e.g. from onTagDiscovered)
    LaunchedEffect(activeScheduleId) {
         if (activeScheduleId == null) {
              manualFocusMode = sharedPreferences.getBoolean("manualFocusMode", false)
         } else {
             manualFocusMode = true
         }
    }

    if (!isServiceEnabled) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Activate Magic") },
            text = { Text("Focus Pocus needs to be enabled in Accessibility Settings to cast its spells.") },
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

    // Determine which blocker is currently active (if any)
    val activeBlockerName = remember(focusMode, activeSchedule, activeManualBlocker) {
        when {
            !focusMode -> null
            activeSchedule != null -> activeSchedule.blockerName
            else -> activeManualBlocker?.name
        }
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

                    Greeting(
                        focusMode = focusMode,
                        activeTagId = focusTagId,
                        namedTags = namedTags,
                        activeBlocker = currentActiveBlocker,
                        activeSchedule = activeSchedule,
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
                            activeBlockerName = activeBlockerName,
                            onBlockerClick = {
                                selectedBlocker = it
                                screen = Screen.EditBlocker
                            },
                            onCreateClick = {
                                screen = Screen.CreateBlocker
                            },
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
                                isActive = it.name == activeBlockerName,
                                onSaveBlocker = { blockerToSave ->
                                    onSaveBlocker(blockerToSave)
                                    screen = Screen.BlockerList
                                },
                                onDeleteBlocker = { blockerToDelete ->
                                    onDeleteBlocker(blockerToDelete)
                                    screen = Screen.BlockerList
                                },
                                onBack = { screen = Screen.BlockerList },
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
                    ProfileScreen(
                        lastScannedTagId = lastScannedTagId,
                        namedTags = namedTags,
                        onSaveTag = onSaveTag,
                        onDeleteTag = onDeleteTag,
                        modifier = contentModifier,
                        blockerLists = blockerLists
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
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable { onScheduleClick(schedule) },
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(schedule.name, style = MaterialTheme.typography.titleMedium)
                                Text("${schedule.startTime} - ${schedule.endTime}")
                                Text(schedule.days.joinToString { it.name.take(3) })
                                if (schedule.unbindingTalismanId != null) {
                                    Text("Bound to Talisman", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Button(onClick = { onDeleteSchedule(schedule) }) {
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
                            Icon(Icons.Default.Star, contentDescription = null)
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
            Text(selectedBlocker?.name ?: "Select Spell to Cast")
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedButton(onClick = { showTalismanDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedTalisman?.name?.let { "Unbind with: $it" } ?: "Optional: Bind to Talisman")
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Text("Days Active:")
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            DayOfWeek.values().forEach { day ->
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
                Text(text = "End: %02d:%02d".format(endTimeState.hour, endTimeState.minute))
            }
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
                            unbindingTalismanId = selectedTalisman?.id
                        )
                    )
                }
            }, enabled = name.isNotBlank() && selectedBlocker != null && selectedDays.isNotEmpty()) {
                Text("Save Ritual")
            }
        }
    }
}



@Composable
fun BlockerListScreen(
    blockerLists: List<Blocker>,
    activeBlockerName: String?,
    onBlockerClick: (Blocker) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateClick) {
                Icon(Icons.Default.Add, contentDescription = "Create new spell")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Spells", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn {
                items(blockerLists) { blocker ->
                    val isActive = blocker.name == activeBlockerName
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .then(
                                if (isActive) Modifier else Modifier.clickable { onBlockerClick(blocker) }
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
                                Text(blocker.name, style = MaterialTheme.typography.titleMedium)
                                Text(if (blocker.mode == BlockerMode.BLACKLIST) "Banish" else "Protect", style = MaterialTheme.typography.bodySmall)
                                if (isActive) {
                                    Text(
                                        "Currently Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Icon(Icons.Default.Lock, contentDescription = null)
                        }
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
        Text("Inscribe New Spell", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        TextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Spell Name") },
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
                            Image(
                                bitmap = appInfo.icon.toBitmap().asImageBitmap(),
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
            Text("Save Spell")
        }
    }
}

@Composable
fun EditBlockerScreen(
    blocker: Blocker,
    isActive: Boolean,
    onSaveBlocker: (Blocker) -> Unit,
    onDeleteBlocker: (Blocker) -> Unit,
    onBack: () -> Unit,
    installedApps: List<AppInfo>,
    modifier: Modifier = Modifier
) {
    var selectedMode by remember { mutableStateOf(blocker.mode) }
    var apps by remember { mutableStateOf(blocker.apps) }
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog && !isActive) {
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
        Text("Editing Spell: ${blocker.name}", style = MaterialTheme.typography.headlineSmall)

        if (isActive) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "This spell is currently active and cannot be edited. Dispel it first to make changes.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selectedMode == BlockerMode.BLACKLIST,
                onClick = { if (!isActive) selectedMode = BlockerMode.BLACKLIST },
                enabled = !isActive
            )
            Text("Banish", color = if (isActive) Color.Gray else Color.Unspecified)
            RadioButton(
                selected = selectedMode == BlockerMode.WHITELIST,
                onClick = { if (!isActive) selectedMode = BlockerMode.WHITELIST },
                enabled = !isActive
            )
            Text("Shield", color = if (isActive) Color.Gray else Color.Unspecified)
        }

        Button(
            onClick = { showDialog = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isActive
        ) {
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
                            Image(
                                bitmap = appInfo.icon.toBitmap().asImageBitmap(),
                                contentDescription = appInfo.name,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(appInfo.name, modifier = Modifier.padding(start = 16.dp))
                        }
                    } else {
                        Text(appPackageName, modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = { apps = apps - appPackageName },
                        enabled = !isActive
                    ) {
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
            if (isActive) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back")
                }
            } else {
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
                        .padding(start = 8.dp)
                ) {
                    Text("Delete")
                }
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
                        Image(
                            bitmap = app.icon.toBitmap().asImageBitmap(),
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
        title = { Text("Select Spell") },
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
                        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
    blockerLists: List<Blocker>,
    modifier: Modifier = Modifier
) {
    var tagName by remember { mutableStateOf("") }
    
    // Removing bind spell dialog logic entirely from here as it's no longer used

    Column(modifier = modifier.padding(16.dp)) {
        Text("Your Wizard Profile", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
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
                    Button(onClick = { onSaveTag(tagName) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Enchant Talisman")
                    }
                } ?: Text("Scan an NFC tag to bind it.")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Enchanted Items:", style = MaterialTheme.typography.titleMedium)
        
        LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
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
                            // Removed "Bound Spell" display text
                        }
                        Button(onClick = { onDeleteTag(tag) }) {
                            Text("Disenchant")
                        }
                    }
                }
            }
        }
    }
}

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable
)

data class Schedule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val blockerName: String,
    val days: Set<DayOfWeek>,
    val startTime: String, // "HH:mm"
    val endTime: String, // "HH:mm"
    val unbindingTalismanId: String? = null
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
    PROFILE("Wizard", Icons.Filled.Person),
}

@Composable
fun Greeting(
    focusMode: Boolean,
    activeTagId: String?,
    namedTags: List<NamedTag>,
    activeBlocker: Blocker?,
    activeSchedule: Schedule?,
    onStartClicked: () -> Unit,
    onBlockerSelectorClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTagName = namedTags.find { it.id == activeTagId }?.name
    val boundTalismanName = if (activeSchedule != null && activeSchedule.unbindingTalismanId != null) {
        namedTags.find { it.id == activeSchedule.unbindingTalismanId }?.name ?: "Unknown Talisman"
    } else null

    val isButtonEnabled = activeSchedule == null || activeSchedule.unbindingTalismanId == null

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Magical Status Text
            Text(
                text = if (focusMode) "Focus Spell Active" else "Ready to Cast",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = if (focusMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Spell Selector Card
            if (activeSchedule == null) {
                ElevatedCard(
                    onClick = onBlockerSelectorClicked,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (activeBlocker != null)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = if (activeBlocker != null)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.size(16.dp))
                            Column {
                                Text(
                                    text = activeBlocker?.name ?: "Select a Spell",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (activeBlocker != null)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (activeBlocker != null) {
                                    Text(
                                        text = if (activeBlocker.mode == BlockerMode.BLACKLIST) "Banish Mode" else "Shield Mode",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                } else {
                                    Text(
                                        text = "Tap to choose",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Change spell",
                            tint = if (activeBlocker != null)
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                // Active Schedule Card (non-clickable)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.size(16.dp))
                            Text(
                                text = activeBlocker?.name ?: "Unknown Spell",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Ritual: ${activeSchedule.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        if (boundTalismanName != null) {
                            Text(
                                text = "Unbind with: $boundTalismanName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Wand Button
            val wandColor = when {
                !isButtonEnabled -> Color.Gray
                focusMode -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.FilledIconButton(
                    onClick = onStartClicked,
                    modifier = Modifier.size(100.dp),
                    enabled = isButtonEnabled,
                    colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                        containerColor = wandColor,
                        contentColor = Color.White,
                        disabledContainerColor = Color.Gray,
                        disabledContentColor = Color.White.copy(alpha = 0.6f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = if (focusMode) "Dispel" else "Cast",
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = when {
                        !isButtonEnabled -> "Bound"
                        focusMode -> "Tap to Dispel"
                        activeBlocker != null -> "Tap to Cast"
                        else -> "Select a spell first"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (focusMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }

            activeTagId?.let {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Triggered by Talisman: ${activeTagName ?: it}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            if (activeSchedule != null && activeSchedule.unbindingTalismanId != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Scan $boundTalismanName to dispel",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@PreviewScreenSizes
@Composable
fun GreetingPreview() {
    FocusPocusTheme {
        FocusPocusApp(null, null, emptyList(), emptyList(), emptyList(), emptyList(), false, null, {_ -> }, {}, {}, {}, {}, {}, {})
    }
}
