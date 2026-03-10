package com.infinicada.focuspocus.ui.screens

import kotlin.math.roundToInt
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
import androidx.compose.material3.Slider
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
import com.infinicada.focuspocus.AppInfo
import com.infinicada.focuspocus.AppTimeLimitManager
import com.infinicada.focuspocus.UsageStatsHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeLimitsScreen(
    installedApps: List<AppInfo>,
    appTimeLimits: Map<String, Int>,
    onSaveAppTimeLimit: (String, Int) -> Unit,
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
            onSave = { pkg, limit ->
                onSaveAppTimeLimit(pkg, limit)
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
                items(appTimeLimits.entries.toList()) { (pkg, limit) ->
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
                                    stringResource(R.string.time_limits_used_today, usedMinutes, limit),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (usedMinutes >= limit) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
    existingLimits: Map<String, Int>,
    onSave: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var limitMinutes by remember { mutableIntStateOf(30) }
    var appDropdownExpanded by remember { mutableStateOf(false) }

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.time_limits_add_dialog_title)) },
        text = {
            Column {
                ExposedDropdownMenuBox(
                    expanded = appDropdownExpanded,
                    onExpandedChange = { appDropdownExpanded = !appDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedApp?.name ?: stringResource(R.string.time_limits_select_app),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.time_limits_app_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = appDropdownExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
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

                Text(stringResource(R.string.time_limits_daily_limit, limitOptions.find { it.first == limitMinutes }?.second ?: stringResource(R.string.format_duration_minutes, limitMinutes)))
                Slider(
                    value = limitMinutes.toFloat(),
                    onValueChange = { limitMinutes = it.roundToInt() },
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
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
