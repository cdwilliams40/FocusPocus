package com.infinicada.focuspocus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.AppTimeLimitManager
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.UsageStatsHelper
import com.infinicada.focuspocus.model.AppInfo
import com.infinicada.focuspocus.model.ConditionalUnlock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionalUnlocksScreen(
    conditionalUnlocks: List<ConditionalUnlock>,
    installedApps: List<AppInfo>,
    blockerLists: List<Blocker>,
    onSave: (ConditionalUnlock) -> Unit,
    onDelete: (ConditionalUnlock) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasUsagePermission = remember { UsageStatsHelper.hasUsageStatsPermission(context) }

    var editingRule by rememberSaveable { mutableStateOf<ConditionalUnlock?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }

    if (showEditor) {
        ConditionalUnlockEditorScreen(
            ruleToEdit = editingRule,
            installedApps = installedApps,
            blockerLists = blockerLists,
            onSave = { rule ->
                onSave(rule)
                showEditor = false
                editingRule = null
            },
            onDelete = if (editingRule != null) {
                { rule ->
                    onDelete(rule)
                    showEditor = false
                    editingRule = null
                }
            } else null,
            onCancel = {
                showEditor = false
                editingRule = null
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.conditional_unlocks_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.conditional_unlocks_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (!hasUsagePermission) {
                item {
                    OutlinedButton(
                        onClick = { UsageStatsHelper.openUsageAccessSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.conditional_unlocks_grant_usage))
                    }
                    Text(
                        stringResource(R.string.conditional_unlocks_usage_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            items(conditionalUnlocks) { rule ->
                val requiredAppName = installedApps.find { it.packageName == rule.requiredAppPackage }?.name
                    ?: rule.requiredAppPackage
                val usedMinutes = remember(rule.requiredAppPackage) {
                    AppTimeLimitManager.getUsedMinutesToday(context, rule.requiredAppPackage)
                }
                val progress = (usedMinutes.toFloat() / rule.requiredMinutes).coerceIn(0f, 1f)
                val conditionMet = usedMinutes >= rule.requiredMinutes

                ElevatedCard(
                    onClick = {
                        editingRule = rule
                        showEditor = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(rule.name, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.conditional_unlocks_rule_summary, rule.requiredMinutes, requiredAppName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.conditional_unlocks_unlocked_enchantments,
                                rule.unlockedBlockerNames.joinToString(", ").ifEmpty { "None" }),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (hasUsagePermission) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.conditional_unlocks_progress, usedMinutes, rule.requiredMinutes),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (conditionMet) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                color = if (conditionMet) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        editingRule = null
                        showEditor = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.conditional_unlocks_add))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionalUnlockEditorScreen(
    ruleToEdit: ConditionalUnlock?,
    installedApps: List<AppInfo>,
    blockerLists: List<Blocker>,
    onSave: (ConditionalUnlock) -> Unit,
    onDelete: ((ConditionalUnlock) -> Unit)?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by rememberSaveable { mutableStateOf(ruleToEdit?.name ?: "") }
    var selectedRequiredApp by rememberSaveable {
        mutableStateOf(installedApps.find { it.packageName == ruleToEdit?.requiredAppPackage })
    }
    var requiredMinutes by rememberSaveable { mutableIntStateOf(ruleToEdit?.requiredMinutes ?: 15) }
    var selectedBlockerNames by rememberSaveable { mutableStateOf(ruleToEdit?.unlockedBlockerNames ?: emptySet()) }

    var requiredAppDropdownExpanded by remember { mutableStateOf(false) }
    var requiredAppSearchQuery by remember { mutableStateOf("") }
    var minutesDropdownExpanded by remember { mutableStateOf(false) }
    var enchantmentsDropdownExpanded by remember { mutableStateOf(false) }

    val minuteOptions = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)

    val filteredRequiredApps = if (requiredAppSearchQuery.isEmpty()) installedApps
        else installedApps.filter { it.name.contains(requiredAppSearchQuery, ignoreCase = true) }

    val isValid = name.isNotBlank() && selectedRequiredApp != null && selectedBlockerNames.isNotEmpty()

    val title = if (ruleToEdit != null) stringResource(R.string.conditional_unlocks_editor_title_edit)
    else stringResource(R.string.conditional_unlocks_editor_title_create)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Name field
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.conditional_unlocks_name_label)) },
                    placeholder = { Text(stringResource(R.string.conditional_unlocks_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Required app picker
            item {
                ExposedDropdownMenuBox(
                    expanded = requiredAppDropdownExpanded,
                    onExpandedChange = { requiredAppDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = if (requiredAppDropdownExpanded) requiredAppSearchQuery
                        else selectedRequiredApp?.name ?: "",
                        onValueChange = {
                            requiredAppSearchQuery = it
                            requiredAppDropdownExpanded = true
                        },
                        label = { Text(stringResource(R.string.conditional_unlocks_required_app_label)) },
                        placeholder = { Text(stringResource(R.string.conditional_unlocks_required_app_placeholder)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = requiredAppDropdownExpanded) },
                        singleLine = true,
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = requiredAppDropdownExpanded,
                        onDismissRequest = {
                            requiredAppDropdownExpanded = false
                            requiredAppSearchQuery = ""
                        }
                    ) {
                        if (filteredRequiredApps.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.time_limits_no_apps_found)) },
                                onClick = {},
                                enabled = false
                            )
                        } else {
                            filteredRequiredApps.forEach { app ->
                                DropdownMenuItem(
                                    text = { Text(app.name) },
                                    onClick = {
                                        selectedRequiredApp = app
                                        requiredAppDropdownExpanded = false
                                        requiredAppSearchQuery = ""
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Required minutes picker
            item {
                ExposedDropdownMenuBox(
                    expanded = minutesDropdownExpanded,
                    onExpandedChange = { minutesDropdownExpanded = !minutesDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = stringResource(R.string.format_duration_minutes, requiredMinutes),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.conditional_unlocks_required_minutes_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = minutesDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = minutesDropdownExpanded,
                        onDismissRequest = { minutesDropdownExpanded = false }
                    ) {
                        minuteOptions.forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.format_duration_minutes, minutes)) },
                                onClick = {
                                    requiredMinutes = minutes
                                    minutesDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Enchantments multi-select
            item {
                ExposedDropdownMenuBox(
                    expanded = enchantmentsDropdownExpanded,
                    onExpandedChange = { enchantmentsDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = if (selectedBlockerNames.isEmpty()) ""
                        else stringResource(R.string.conditional_unlocks_enchantments_count, selectedBlockerNames.size),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.conditional_unlocks_enchantments_label)) },
                        placeholder = { Text(stringResource(R.string.conditional_unlocks_enchantments_placeholder)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = enchantmentsDropdownExpanded) },
                        singleLine = true,
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = enchantmentsDropdownExpanded,
                        onDismissRequest = { enchantmentsDropdownExpanded = false }
                    ) {
                        if (blockerLists.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.time_limits_no_apps_found)) },
                                onClick = {},
                                enabled = false
                            )
                        } else {
                            blockerLists.forEach { blocker ->
                                val checked = blocker.name in selectedBlockerNames
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Checkbox(
                                                checked = checked,
                                                onCheckedChange = null
                                            )
                                            Text(blocker.name)
                                        }
                                    },
                                    onClick = {
                                        selectedBlockerNames = if (checked) {
                                            selectedBlockerNames - blocker.name
                                        } else {
                                            selectedBlockerNames + blocker.name
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Action buttons
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val rule = ConditionalUnlock(
                            id = ruleToEdit?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim(),
                            requiredAppPackage = selectedRequiredApp!!.packageName,
                            requiredMinutes = requiredMinutes,
                            unlockedBlockerNames = selectedBlockerNames
                        )
                        onSave(rule)
                    },
                    enabled = isValid,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_save))
                }
                if (onDelete != null && ruleToEdit != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onDelete(ruleToEdit) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.conditional_unlocks_delete))
                    }
                }
            }
        }
    }
}
