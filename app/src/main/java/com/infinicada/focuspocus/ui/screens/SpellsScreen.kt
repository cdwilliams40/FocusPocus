package com.infinicada.focuspocus.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.infinicada.focuspocus.AppInfo
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.BlockerMode
import com.infinicada.focuspocus.FocusPreset
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.PresetAction
import com.infinicada.focuspocus.formatDuration
import java.util.UUID

@Composable
fun BlockerListScreen(
    blockerLists: List<Blocker>,
    focusPresets: List<FocusPreset>,
    namedTags: List<NamedTag>,
    activeBlockerName: String?,
    onBlockerClick: (Blocker) -> Unit,
    onCreateClick: () -> Unit,
    onSaveFocusPreset: (FocusPreset) -> Unit,
    onDeleteFocusPreset: (FocusPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPresetEditorDialog by remember { mutableStateOf(false) }
    var presetToEdit by remember { mutableStateOf<FocusPreset?>(null) }
    var showQrPreset by remember { mutableStateOf<FocusPreset?>(null) }

    if (showQrPreset != null) {
        QrCodeDialog(
            content = "focuspocus://preset/${showQrPreset!!.id}",
            title = "QR Code: ${showQrPreset!!.name}",
            onDismiss = { showQrPreset = null }
        )
    }

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
                            val actionLabel = when (preset.action ?: PresetAction.TOGGLE) {
                                PresetAction.TEMP_ENABLE -> " • Temp Enable ${preset.tempDurationMinutes ?: 30}m"
                                PresetAction.TEMP_DISABLE -> " • Temp Disable ${preset.tempDurationMinutes ?: 30}m"
                                else -> ""
                            }
                            Text(
                                "${blocker?.name ?: preset.blockerName} • $durationText${if (preset.breaksEnabled) " • Breaks" else ""}$actionLabel",
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
                                onClick = { showQrPreset = preset },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text("QR")
                            }
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(blocker.name, style = MaterialTheme.typography.titleMedium)
                                if (isActive) {
                                    Text(
                                        "Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                buildString {
                                    append(if (blocker.mode == BlockerMode.BLACKLIST) "Banish" else "Shield")
                                    val appCount = blocker.apps.size
                                    val siteCount = blocker.websites.orEmpty().size
                                    if (appCount > 0 || siteCount > 0) {
                                        append(" - ")
                                        val parts = mutableListOf<String>()
                                        if (appCount > 0) parts.add("$appCount app${if (appCount != 1) "s" else ""}")
                                        if (siteCount > 0) parts.add("$siteCount site${if (siteCount != 1) "s" else ""}")
                                        append(parts.joinToString(", "))
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (isActive) {
                                Text(
                                    "Cannot edit while active",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
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
    var websites by remember { mutableStateOf(emptyList<String>()) }
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
            onValueChange = { if (it.length <= 100) name = it },
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

        AppListColumn(
            apps = apps,
            installedApps = installedApps,
            onRemoveApp = { apps = apps - it },
            modifier = Modifier
                .padding(top = 16.dp)
                .weight(1f)
        )

        WebsiteListSection(
            websites = websites,
            onWebsitesChanged = { websites = it }
        )

        Button(
            onClick = { onSaveBlocker(Blocker(name.trim(), selectedMode, apps, websites)) },
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
    var websites by remember { mutableStateOf(blocker.websites.orEmpty()) }
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

        AppListColumn(
            apps = apps,
            installedApps = installedApps,
            onRemoveApp = { apps = apps - it },
            modifier = Modifier
                .padding(top = 16.dp)
                .weight(1f)
        )

        WebsiteListSection(
            websites = websites,
            onWebsitesChanged = { websites = it }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Button(
                onClick = { onSaveBlocker(blocker.copy(mode = selectedMode, apps = apps, websites = websites)) },
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
fun WebsiteListSection(
    websites: List<String>,
    onWebsitesChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }

    Column(modifier = modifier.padding(top = 16.dp)) {
        Text("Blocked Websites", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 255) input = it },
                label = { Text("e.g. youtube.com") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val domain = cleanDomain(input)
                    if (domain.isNotEmpty() && isValidDomain(domain) && domain !in websites) {
                        onWebsitesChanged(websites + domain)
                    }
                    input = ""
                },
                enabled = input.isNotBlank(),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Add")
            }
        }
        websites.forEach { site ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(site, modifier = Modifier.weight(1f))
                IconButton(onClick = { onWebsitesChanged(websites - site) }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove $site")
                }
            }
        }
    }
}

private fun cleanDomain(input: String): String {
    var domain = input.trim().lowercase()
    if (domain.startsWith("https://")) domain = domain.removePrefix("https://")
    if (domain.startsWith("http://")) domain = domain.removePrefix("http://")
    if (domain.startsWith("www.")) domain = domain.removePrefix("www.")
    domain = domain.split('/')[0]
    domain = domain.split('?')[0]
    domain = domain.split('#')[0]
    domain = domain.split(':')[0] // strip port
    return domain
}

private val domainPattern = Regex("^[a-z0-9]([a-z0-9\\-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9\\-]{0,61}[a-z0-9])?)*$")

private fun isValidDomain(domain: String): Boolean {
    return domain.length <= 255 && domain.contains('.') && domainPattern.matches(domain)
}

/**
 * Shared composable for displaying a list of selected apps with remove buttons.
 * Used by both CreateBlockerScreen and EditBlockerScreen.
 */
@Composable
fun AppListColumn(
    apps: List<String>,
    installedApps: List<AppInfo>,
    onRemoveApp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
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
                Button(onClick = { onRemoveApp(appPackageName) }) {
                    Text("Remove")
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
                                currentSelections = if (currentSelections.contains(app.packageName)) {
                                    currentSelections - app.packageName
                                } else {
                                    currentSelections + app.packageName
                                }
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
                                currentSelections = if (isChecked) {
                                    currentSelections + app.packageName
                                } else {
                                    currentSelections - app.packageName
                                }
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
    var selectedAction by remember { mutableStateOf(presetToEdit?.action ?: PresetAction.TOGGLE) }
    var tempDurationMinutes by remember { mutableIntStateOf(presetToEdit?.tempDurationMinutes ?: 30) }
    // Note: action/tempDurationMinutes are nullable due to Gson deserialization of old data
    var actionDropdownExpanded by remember { mutableStateOf(false) }
    var tempDurationDropdownExpanded by remember { mutableStateOf(false) }
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
                    onValueChange = { if (it.length <= 100) name = it },
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

                // Action type dropdown
                val actionLabels = mapOf(
                    PresetAction.TOGGLE to "Toggle",
                    PresetAction.TEMP_ENABLE to "Temporary Enable",
                    PresetAction.TEMP_DISABLE to "Temporary Disable"
                )
                ExposedDropdownMenuBox(
                    expanded = actionDropdownExpanded,
                    onExpandedChange = { actionDropdownExpanded = !actionDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = actionLabels[selectedAction] ?: "Toggle",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Action Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionDropdownExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = actionDropdownExpanded,
                        onDismissRequest = { actionDropdownExpanded = false }
                    ) {
                        PresetAction.entries.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(actionLabels[action] ?: action.name) },
                                onClick = {
                                    selectedAction = action
                                    actionDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Temp duration picker (for TEMP_ENABLE and TEMP_DISABLE)
                if (selectedAction != PresetAction.TOGGLE) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val tempDurations = listOf(
                        5 to "5 min", 10 to "10 min", 15 to "15 min",
                        30 to "30 min", 60 to "1 hour", 120 to "2 hours"
                    )
                    ExposedDropdownMenuBox(
                        expanded = tempDurationDropdownExpanded,
                        onExpandedChange = { tempDurationDropdownExpanded = !tempDurationDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = tempDurations.find { it.first == tempDurationMinutes }?.second ?: "$tempDurationMinutes min",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Temp Duration") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tempDurationDropdownExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = tempDurationDropdownExpanded,
                            onDismissRequest = { tempDurationDropdownExpanded = false }
                        ) {
                            tempDurations.forEach { (minutes, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        tempDurationMinutes = minutes
                                        tempDurationDropdownExpanded = false
                                    }
                                )
                            }
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
                                name = name.trim(),
                                blockerName = blocker.name,
                                durationMinutes = selectedDuration,
                                breaksEnabled = breaksEnabled,
                                talismanId = selectedTalisman?.id,
                                action = selectedAction,
                                tempDurationMinutes = tempDurationMinutes
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
