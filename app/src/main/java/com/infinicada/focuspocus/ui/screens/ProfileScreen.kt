package com.infinicada.focuspocus.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.AppInfo
import com.infinicada.focuspocus.AppTimeLimitManager
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.UsageStatsHelper
import com.infinicada.focuspocus.ui.theme.ThemeMode

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
    hideStopButton: Boolean = false,
    onHideStopButtonChanged: (Boolean) -> Unit = {},
    emergencyBreakCadenceWeeks: Int = 2,
    onEmergencyBreakCadenceChanged: (Int) -> Unit = {},
    nfcLockMode: Boolean = false,
    onNfcLockModeChanged: (Boolean) -> Unit = {},
    installedApps: List<AppInfo> = emptyList(),
    appTimeLimits: Map<String, Int> = emptyMap(),
    onSaveAppTimeLimit: (String, Int) -> Unit = { _, _ -> },
    onDeleteAppTimeLimit: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var tagName by remember { mutableStateOf("") }
    var showQrTalisman by remember { mutableStateOf<NamedTag?>(null) }

    if (showQrTalisman != null) {
        QrCodeDialog(
            content = "focuspocus://talisman/${showQrTalisman!!.id}",
            title = "QR Code: ${showQrTalisman!!.name}",
            onDismiss = { showQrTalisman = null }
        )
    }

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

                    Spacer(modifier = Modifier.height(16.dp))

                    // Emergency break cadence
                    Text(
                        "Emergency Break Cooldown: $emergencyBreakCadenceWeeks weeks",
                        color = if (focusMode) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "One special break per cooldown period when all regular breaks are used",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = emergencyBreakCadenceWeeks.toFloat(),
                        onValueChange = { onEmergencyBreakCadenceChanged(it.toInt()) },
                        valueRange = 2f..8f,
                        steps = 5,
                        enabled = !focusMode,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Hide stop button toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hide Stop Button")
                            Text(
                                "Hides Dispel button during timed focus sessions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = hideStopButton,
                            onCheckedChange = onHideStopButtonChanged,
                            enabled = !focusMode
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Security Card (NFC Lock Mode)
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Security", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Talisman Lock Mode")
                            Text(
                                "Require NFC talisman or QR code to stop focus sessions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = nfcLockMode,
                            onCheckedChange = onNfcLockModeChanged,
                            enabled = !focusMode && namedTags.isNotEmpty()
                        )
                    }

                    if (namedTags.isEmpty()) {
                        Text(
                            "Add a talisman first to enable lock mode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // App Time Limits Card
        item {
            AppTimeLimitsCard(
                installedApps = installedApps,
                appTimeLimits = appTimeLimits,
                onSaveAppTimeLimit = onSaveAppTimeLimit,
                onDeleteAppTimeLimit = onDeleteAppTimeLimit
            )
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
                            onValueChange = { if (it.length <= 100) tagName = it },
                            label = { Text("Talisman Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                onSaveTag(tagName.trim())
                                tagName = ""
                            },
                            enabled = tagName.isNotBlank(),
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
                    OutlinedButton(
                        onClick = { showQrTalisman = tag },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("QR")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTimeLimitsCard(
    installedApps: List<AppInfo>,
    appTimeLimits: Map<String, Int>,
    onSaveAppTimeLimit: (String, Int) -> Unit,
    onDeleteAppTimeLimit: (String) -> Unit
) {
    val context = LocalContext.current
    val hasUsagePermission = remember { UsageStatsHelper.hasUsageStatsPermission(context) }
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddTimeLimitDialog(
            installedApps = installedApps,
            existingLimits = appTimeLimits,
            onSave = { pkg, limit ->
                onSaveAppTimeLimit(pkg, limit)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("App Time Limits", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Set daily time quotas per app. Apps are blocked when their limit is reached, even outside focus mode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (!hasUsagePermission) {
                OutlinedButton(
                    onClick = { UsageStatsHelper.openUsageAccessSettings(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Usage Access")
                }
                Text(
                    "Required for time limit tracking",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                appTimeLimits.forEach { (pkg, limit) ->
                    val appName = installedApps.find { it.packageName == pkg }?.name ?: pkg
                    val usedMinutes = remember(pkg) {
                        AppTimeLimitManager.getUsedMinutesToday(context, pkg)
                    }
                    val progress = (usedMinutes.toFloat() / limit).coerceIn(0f, 1f)

                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(appName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "$usedMinutes / $limit min today",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (usedMinutes >= limit) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    color = if (usedMinutes >= limit) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.primary,
                                )
                            }
                            Button(
                                onClick = { onDeleteAppTimeLimit(pkg) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Time Limit")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTimeLimitDialog(
    installedApps: List<AppInfo>,
    existingLimits: Map<String, Int>,
    onSave: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var limitMinutes by remember { mutableIntStateOf(30) }
    var appDropdownExpanded by remember { mutableStateOf(false) }

    val availableApps = installedApps.filter { it.packageName !in existingLimits }

    val limitOptions = listOf(
        5 to "5 min", 10 to "10 min", 15 to "15 min", 30 to "30 min",
        60 to "1 hour", 120 to "2 hours", 240 to "4 hours", 480 to "8 hours"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add App Time Limit") },
        text = {
            Column {
                ExposedDropdownMenuBox(
                    expanded = appDropdownExpanded,
                    onExpandedChange = { appDropdownExpanded = !appDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedApp?.name ?: "Select App",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("App") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = appDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = appDropdownExpanded,
                        onDismissRequest = { appDropdownExpanded = false }
                    ) {
                        availableApps.take(50).forEach { app ->
                            DropdownMenuItem(
                                text = { Text(app.name) },
                                onClick = {
                                    selectedApp = app
                                    appDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Daily Limit: ${limitOptions.find { it.first == limitMinutes }?.second ?: "$limitMinutes min"}")
                Slider(
                    value = limitMinutes.toFloat(),
                    onValueChange = { limitMinutes = it.toInt() },
                    valueRange = 5f..480f,
                    steps = 0,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedApp?.let { onSave(it.packageName, limitMinutes) } },
                enabled = selectedApp != null
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
