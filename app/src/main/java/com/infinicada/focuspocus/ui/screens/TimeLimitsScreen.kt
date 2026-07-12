package com.infinicada.focuspocus.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.model.AppInfo
import com.infinicada.focuspocus.model.AppTimeLimit
import com.infinicada.focuspocus.AppTimeLimitManager
import com.infinicada.focuspocus.UsageStatsHelper
import com.infinicada.focuspocus.ui.components.SingleAppPickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeLimitsScreen(
    installedApps: List<AppInfo>,
    appTimeLimits: Map<String, AppTimeLimit>,
    onSaveAppTimeLimit: (AppTimeLimit) -> Unit,
    onDeleteAppTimeLimit: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasUsagePermission = remember { UsageStatsHelper.hasUsageStatsPermission(context) }
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddTimeLimitDialog(
            installedApps = installedApps,
            existingLimits = appTimeLimits,
            onSave = { config ->
                onSaveAppTimeLimit(config)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.time_limits_title)) },
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
                    stringResource(R.string.time_limits_description),
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
                        Text(stringResource(R.string.time_limits_grant_usage))
                    }
                    Text(
                        stringResource(R.string.time_limits_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                items(appTimeLimits.entries.toList()) { (pkg, config) ->
                    val appName = installedApps.find { it.packageName == pkg }?.name ?: pkg
                    val limit = config.dailyLimitMinutes
                    val usedMinutes = remember(pkg) {
                        AppTimeLimitManager.getUsedMinutesToday(context, pkg)
                    }
                    val progress = if (limit > 0) (usedMinutes.toFloat() / limit).coerceIn(0f, 1f) else 0f

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
                                    stringResource(R.string.time_limits_used_today, usedMinutes, limit),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (usedMinutes >= limit) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (config.pactModeEnabled) {
                                    Text(
                                        stringResource(
                                            R.string.time_limits_pact_desc,
                                            if (config.pactMaxMinutes > 0) config.pactMaxMinutes else 15,
                                            config.cooldownMinutes
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                } else if (config.sessionLimitMinutes > 0) {
                                    Text(
                                        stringResource(
                                            R.string.time_limits_cooldown_desc,
                                            config.sessionLimitMinutes,
                                            config.cooldownMinutes
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    color = if (usedMinutes >= limit) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary,
                                )
                            }
                            Button(
                                onClick = { onDeleteAppTimeLimit(pkg) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(stringResource(R.string.action_remove))
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.time_limits_add))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTimeLimitDialog(
    installedApps: List<AppInfo>,
    existingLimits: Map<String, AppTimeLimit>,
    onSave: (AppTimeLimit) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var limitMinutes by remember { mutableIntStateOf(30) }
    var showAppPicker by remember { mutableStateOf(false) }
    var timeLimitExpanded by remember { mutableStateOf(false) }

    // Cooldown settings
    var cooldownEnabled by remember { mutableStateOf(false) }
    var sessionLimitMinutes by remember { mutableIntStateOf(10) }
    var cooldownMinutes by remember { mutableIntStateOf(30) }
    var sessionLimitExpanded by remember { mutableStateOf(false) }
    var cooldownDurationExpanded by remember { mutableStateOf(false) }

    // Pact Mode settings (mutually exclusive with the passive session cooldown —
    // in Pact Mode the chosen allowance IS the session limit)
    var pactEnabled by remember { mutableStateOf(false) }
    var pactMaxMinutes by remember { mutableIntStateOf(15) }
    var pactMaxExpanded by remember { mutableStateOf(false) }
    var pactSealExpanded by remember { mutableStateOf(false) }

    val availableApps = installedApps.filter { it.packageName !in existingLimits }

    val limitOptions = listOf(
        5 to stringResource(R.string.duration_5_min),
        10 to stringResource(R.string.duration_10_min),
        15 to stringResource(R.string.duration_15_min),
        30 to stringResource(R.string.duration_30_min),
        60 to stringResource(R.string.duration_1_hour),
        120 to stringResource(R.string.duration_2_hours),
        240 to stringResource(R.string.duration_4_hours),
        480 to stringResource(R.string.duration_8_hours)
    )

    val sessionLimitOptions = listOf(
        5 to stringResource(R.string.duration_5_min),
        10 to stringResource(R.string.duration_10_min),
        15 to stringResource(R.string.duration_15_min),
        20 to stringResource(R.string.time_limits_minutes_value, 20),
        30 to stringResource(R.string.duration_30_min)
    )

    val cooldownDurationOptions = listOf(
        15 to stringResource(R.string.time_limits_minutes_value, 15),
        30 to stringResource(R.string.duration_30_min),
        45 to stringResource(R.string.time_limits_minutes_value, 45),
        60 to stringResource(R.string.duration_1_hour),
        90 to stringResource(R.string.time_limits_minutes_value, 90)
    )

    val pactMaxOptions = listOf(
        5 to stringResource(R.string.duration_5_min),
        10 to stringResource(R.string.duration_10_min),
        15 to stringResource(R.string.duration_15_min),
        30 to stringResource(R.string.duration_30_min)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.time_limits_add_dialog_title)) },
        text = {
            Column {
                // App picker: read-only field that opens the full-screen picker
                Box {
                    OutlinedTextField(
                        value = selectedApp?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.time_limits_app_label)) },
                        placeholder = { Text(stringResource(R.string.time_limits_select_app)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showAppPicker) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showAppPicker = true }
                    )
                }
                if (showAppPicker) {
                    SingleAppPickerDialog(
                        installedApps = availableApps,
                        title = stringResource(R.string.time_limits_select_app),
                        selectedPackage = selectedApp?.packageName,
                        onPick = { app ->
                            selectedApp = app
                            showAppPicker = false
                        },
                        onDismiss = { showAppPicker = false }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Daily limit picker
                ExposedDropdownMenuBox(
                    expanded = timeLimitExpanded,
                    onExpandedChange = { timeLimitExpanded = !timeLimitExpanded }
                ) {
                    OutlinedTextField(
                        value = limitOptions.find { it.first == limitMinutes }?.second
                            ?: stringResource(R.string.format_duration_minutes, limitMinutes),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.time_limits_daily_limit_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = timeLimitExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = timeLimitExpanded,
                        onDismissRequest = { timeLimitExpanded = false }
                    ) {
                        limitOptions.forEach { (minutes, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    limitMinutes = minutes
                                    timeLimitExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Pact Mode toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.time_limits_enable_pact),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(R.string.time_limits_enable_pact_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = pactEnabled,
                        onCheckedChange = {
                            pactEnabled = it
                            if (it) cooldownEnabled = false
                        }
                    )
                }

                if (pactEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Longest pact picker
                    ExposedDropdownMenuBox(
                        expanded = pactMaxExpanded,
                        onExpandedChange = { pactMaxExpanded = !pactMaxExpanded }
                    ) {
                        OutlinedTextField(
                            value = pactMaxOptions.find { it.first == pactMaxMinutes }?.second
                                ?: stringResource(R.string.format_duration_minutes, pactMaxMinutes),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.time_limits_pact_max_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pactMaxExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = pactMaxExpanded,
                            onDismissRequest = { pactMaxExpanded = false }
                        ) {
                            pactMaxOptions.forEach { (minutes, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        pactMaxMinutes = minutes
                                        pactMaxExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Seal duration picker (cooldown after each pact)
                    ExposedDropdownMenuBox(
                        expanded = pactSealExpanded,
                        onExpandedChange = { pactSealExpanded = !pactSealExpanded }
                    ) {
                        OutlinedTextField(
                            value = cooldownDurationOptions.find { it.first == cooldownMinutes }?.second
                                ?: stringResource(R.string.format_duration_minutes, cooldownMinutes),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.time_limits_pact_seal_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pactSealExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = pactSealExpanded,
                            onDismissRequest = { pactSealExpanded = false }
                        ) {
                            cooldownDurationOptions.forEach { (minutes, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        cooldownMinutes = minutes
                                        pactSealExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Session cooldown toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.time_limits_enable_cooldown),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(R.string.time_limits_enable_cooldown_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = cooldownEnabled,
                        onCheckedChange = {
                            cooldownEnabled = it
                            if (it) pactEnabled = false
                        }
                    )
                }

                if (cooldownEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Session limit picker
                    ExposedDropdownMenuBox(
                        expanded = sessionLimitExpanded,
                        onExpandedChange = { sessionLimitExpanded = !sessionLimitExpanded }
                    ) {
                        OutlinedTextField(
                            value = sessionLimitOptions.find { it.first == sessionLimitMinutes }?.second
                                ?: stringResource(R.string.format_duration_minutes, sessionLimitMinutes),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.time_limits_session_limit_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sessionLimitExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = sessionLimitExpanded,
                            onDismissRequest = { sessionLimitExpanded = false }
                        ) {
                            sessionLimitOptions.forEach { (minutes, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        sessionLimitMinutes = minutes
                                        sessionLimitExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Cooldown duration picker
                    ExposedDropdownMenuBox(
                        expanded = cooldownDurationExpanded,
                        onExpandedChange = { cooldownDurationExpanded = !cooldownDurationExpanded }
                    ) {
                        OutlinedTextField(
                            value = cooldownDurationOptions.find { it.first == cooldownMinutes }?.second
                                ?: stringResource(R.string.format_duration_minutes, cooldownMinutes),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.time_limits_cooldown_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cooldownDurationExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = cooldownDurationExpanded,
                            onDismissRequest = { cooldownDurationExpanded = false }
                        ) {
                            cooldownDurationOptions.forEach { (minutes, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        cooldownMinutes = minutes
                                        cooldownDurationExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedApp?.let { app ->
                        onSave(
                            AppTimeLimit(
                                packageName = app.packageName,
                                dailyLimitMinutes = limitMinutes,
                                sessionLimitMinutes = if (cooldownEnabled) sessionLimitMinutes else 0,
                                cooldownMinutes = cooldownMinutes,
                                pactModeEnabled = pactEnabled,
                                pactMaxMinutes = pactMaxMinutes
                            )
                        )
                    }
                },
                enabled = selectedApp != null
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
