package com.infinicada.focuspocus.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.model.AppInfo
import com.infinicada.focuspocus.model.ConditionalUnlock
import com.infinicada.focuspocus.ui.components.SingleAppPickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionalUnlockEditorScreen(
    ruleToEdit: ConditionalUnlock?,
    installedApps: List<AppInfo>,
    blockerLists: List<Blocker>,
    appTimeLimits: Map<String, Int>,
    onSave: (ConditionalUnlock) -> Unit,
    onDelete: ((ConditionalUnlock) -> Unit)?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /** Packages whose "limit" is a pact gate rather than daily minutes (label only). */
    pactPackages: Set<String> = emptySet()
) {
    var name by rememberSaveable { mutableStateOf(ruleToEdit?.name ?: "") }
    var selectedRequiredApp by remember {
        mutableStateOf(installedApps.find { it.packageName == ruleToEdit?.requiredAppPackage })
    }
    var requiredMinutes by rememberSaveable { mutableIntStateOf(ruleToEdit?.requiredMinutes ?: 15) }
    var selectedBlockerNames by remember { mutableStateOf(ruleToEdit?.unlockedBlockerNames ?: emptySet()) }
    var selectedTimeLimitApps by remember { mutableStateOf(ruleToEdit?.unlockedTimeLimitApps ?: emptySet()) }

    var showRequiredAppPicker by remember { mutableStateOf(false) }
    var minutesDropdownExpanded by remember { mutableStateOf(false) }
    var enchantmentsDropdownExpanded by remember { mutableStateOf(false) }
    var timeLimitAppsDropdownExpanded by remember { mutableStateOf(false) }

    val minuteOptions = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)

    val isValid = name.isNotBlank() && selectedRequiredApp != null &&
        (selectedBlockerNames.isNotEmpty() || selectedTimeLimitApps.isNotEmpty())

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

            // Required app picker: read-only field that opens the full-screen picker
            item {
                Box {
                    OutlinedTextField(
                        value = selectedRequiredApp?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.conditional_unlocks_required_app_label)) },
                        placeholder = { Text(stringResource(R.string.conditional_unlocks_required_app_placeholder)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showRequiredAppPicker) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showRequiredAppPicker = true }
                    )
                }
                if (showRequiredAppPicker) {
                    SingleAppPickerDialog(
                        installedApps = installedApps,
                        title = stringResource(R.string.conditional_unlocks_required_app_label),
                        selectedPackage = selectedRequiredApp?.packageName,
                        onPick = { app ->
                            selectedRequiredApp = app
                            showRequiredAppPicker = false
                        },
                        onDismiss = { showRequiredAppPicker = false }
                    )
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

            // Time-limited apps multi-select
            if (appTimeLimits.isNotEmpty()) {
                item {
                    ExposedDropdownMenuBox(
                        expanded = timeLimitAppsDropdownExpanded,
                        onExpandedChange = { timeLimitAppsDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (selectedTimeLimitApps.isEmpty()) ""
                            else stringResource(R.string.conditional_unlocks_time_limit_apps_count, selectedTimeLimitApps.size),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.conditional_unlocks_time_limit_apps_label)) },
                            placeholder = { Text(stringResource(R.string.conditional_unlocks_time_limit_apps_placeholder)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = timeLimitAppsDropdownExpanded) },
                            singleLine = true,
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = timeLimitAppsDropdownExpanded,
                            onDismissRequest = { timeLimitAppsDropdownExpanded = false }
                        ) {
                            appTimeLimits.entries.forEach { (pkg, limitMinutes) ->
                                val appName = installedApps.find { it.packageName == pkg }?.name ?: pkg
                                val checked = pkg in selectedTimeLimitApps
                                val label = if (pkg in pactPackages) {
                                    stringResource(R.string.conditional_unlocks_pact_app_label, appName)
                                } else {
                                    "$appName (${limitMinutes}m)"
                                }
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
                                            Text(label)
                                        }
                                    },
                                    onClick = {
                                        selectedTimeLimitApps = if (checked) {
                                            selectedTimeLimitApps - pkg
                                        } else {
                                            selectedTimeLimitApps + pkg
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
                        val app = selectedRequiredApp ?: return@Button
                        val rule = ConditionalUnlock(
                            id = ruleToEdit?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim(),
                            requiredAppPackage = app.packageName,
                            requiredMinutes = requiredMinutes,
                            unlockedBlockerNames = selectedBlockerNames,
                            unlockedTimeLimitApps = selectedTimeLimitApps
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
