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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.RadioButton
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.BlockerMode
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.model.AppInfo
import com.infinicada.focuspocus.model.AppTimeLimit
import com.infinicada.focuspocus.model.PactGroup
import com.infinicada.focuspocus.ui.components.SingleAppPickerDialog

/**
 * Dedicated management screen for Pact Mode. Pacts are stored as [AppTimeLimit]
 * configs with [AppTimeLimit.pactModeEnabled] — this screen is a focused lens over
 * that store, so the enforcement layer needs no awareness of where a config was
 * created.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PactsScreen(
    installedApps: List<AppInfo>,
    appTimeLimitConfigs: Map<String, AppTimeLimit>,
    blockerLists: List<Blocker>,
    pactGroups: List<PactGroup>,
    todayOpenStats: Map<String, AppOpenStats>,
    onSaveConfig: (AppTimeLimit) -> Unit,
    onDeleteConfig: (String) -> Unit,
    onSaveGroup: (PactGroup) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pactConfigs = appTimeLimitConfigs.filterValues { it.pactModeEnabled }
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddPactDialog(
            installedApps = installedApps,
            existingConfigs = appTimeLimitConfigs,
            blockerLists = blockerLists,
            pactGroups = pactGroups,
            onSave = { config ->
                onSaveConfig(config)
                showAddDialog = false
            },
            onSaveGroup = { group ->
                onSaveGroup(group)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.pacts_title)) },
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
                    stringResource(R.string.pacts_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            items(pactGroups) { group ->
                val memberApps = blockerLists.find { it.name == group.blockerName }?.effectiveApps ?: emptySet()
                val groupOpens = memberApps.sumOf { todayOpenStats[it]?.opens ?: 0 }
                val groupReflexes = memberApps.sumOf { todayOpenStats[it]?.reflexOpens ?: 0 }
                val alternativeName = group.pactAlternativePackage?.let { altPkg ->
                    installedApps.find { it.packageName == altPkg }?.name
                }

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
                            Text(group.blockerName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.pacts_group_summary, memberApps.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(
                                    R.string.pacts_config_summary,
                                    if (group.pactMaxMinutes > 0) group.pactMaxMinutes else 15,
                                    group.cooldownMinutes
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (alternativeName != null) {
                                Text(
                                    stringResource(R.string.pacts_alternative_summary, alternativeName),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                stringResource(R.string.pacts_today_stats, groupOpens, groupReflexes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Button(
                            onClick = { onDeleteGroup(group.blockerName) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(stringResource(R.string.action_remove))
                        }
                    }
                }
            }

            items(pactConfigs.entries.toList()) { (pkg, config) ->
                val appName = installedApps.find { it.packageName == pkg }?.name ?: pkg
                val stats = todayOpenStats[pkg] ?: AppOpenStats()
                val alternativeName = config.pactAlternativePackage?.let { altPkg ->
                    installedApps.find { it.packageName == altPkg }?.name
                }

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
                                stringResource(
                                    R.string.pacts_config_summary,
                                    if (config.pactMaxMinutes > 0) config.pactMaxMinutes else 15,
                                    config.cooldownMinutes
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (alternativeName != null) {
                                Text(
                                    stringResource(R.string.pacts_alternative_summary, alternativeName),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (config.dailyLimitMinutes > 0) {
                                Text(
                                    stringResource(R.string.pacts_backstop_summary, config.dailyLimitMinutes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                stringResource(R.string.pacts_today_stats, stats.opens, stats.reflexOpens),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Button(
                            onClick = { onDeleteConfig(pkg) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(stringResource(R.string.action_remove))
                        }
                    }
                }
            }

            if (pactConfigs.isEmpty() && pactGroups.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.pacts_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.pacts_add))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPactDialog(
    installedApps: List<AppInfo>,
    existingConfigs: Map<String, AppTimeLimit>,
    blockerLists: List<Blocker>,
    pactGroups: List<PactGroup>,
    onSave: (AppTimeLimit) -> Unit,
    onSaveGroup: (PactGroup) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }
    var pactMaxMinutes by remember { mutableIntStateOf(15) }
    var pactMaxExpanded by remember { mutableStateOf(false) }
    var sealMinutes by remember { mutableIntStateOf(30) }
    var sealExpanded by remember { mutableStateOf(false) }
    var backstopMinutes by remember { mutableIntStateOf(0) }
    var backstopExpanded by remember { mutableStateOf(false) }
    var alternativeApp by remember { mutableStateOf<AppInfo?>(null) }
    var showAlternativePicker by remember { mutableStateOf(false) }

    // Target: a single app, or every app in a blacklist enchantment (live membership).
    var useEnchantment by remember { mutableStateOf(false) }
    var targetEnchantment by remember { mutableStateOf<Blocker?>(null) }
    var enchantmentExpanded by remember { mutableStateOf(false) }

    // An app is either pact-managed or limit-managed, never both: converting means
    // removing it from the other screen first.
    val availableApps = installedApps.filter { it.packageName !in existingConfigs }
    val availableBlacklists = blockerLists.filter { blocker ->
        blocker.mode == BlockerMode.BLACKLIST && pactGroups.none { it.blockerName == blocker.name }
    }

    val pactMaxOptions = listOf(
        5 to stringResource(R.string.duration_5_min),
        10 to stringResource(R.string.duration_10_min),
        15 to stringResource(R.string.duration_15_min),
        30 to stringResource(R.string.duration_30_min)
    )
    val sealOptions = listOf(
        15 to stringResource(R.string.duration_15_min),
        30 to stringResource(R.string.duration_30_min),
        45 to stringResource(R.string.duration_45_min),
        60 to stringResource(R.string.duration_1_hour),
        90 to stringResource(R.string.time_limits_minutes_value, 90)
    )
    val backstopOptions = listOf(
        0 to stringResource(R.string.pacts_backstop_none),
        30 to stringResource(R.string.duration_30_min),
        60 to stringResource(R.string.duration_1_hour),
        120 to stringResource(R.string.duration_2_hours),
        240 to stringResource(R.string.duration_4_hours)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pacts_add_dialog_title)) },
        text = {
            Column {
                // Target selector: one app, or a whole blacklist enchantment
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = !useEnchantment, onClick = { useEnchantment = false })
                    Text(
                        stringResource(R.string.pacts_target_app),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { useEnchantment = false }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = useEnchantment, onClick = { useEnchantment = true })
                    Text(
                        stringResource(R.string.pacts_target_enchantment),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { useEnchantment = true }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (useEnchantment) {
                    if (availableBlacklists.isEmpty()) {
                        Text(
                            stringResource(R.string.pacts_no_blacklists),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = enchantmentExpanded,
                            onExpandedChange = { enchantmentExpanded = !enchantmentExpanded }
                        ) {
                            OutlinedTextField(
                                value = targetEnchantment?.name ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.pacts_enchantment_label)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = enchantmentExpanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = enchantmentExpanded,
                                onDismissRequest = { enchantmentExpanded = false }
                            ) {
                                availableBlacklists.forEach { blocker ->
                                    DropdownMenuItem(
                                        text = { Text("${blocker.name} (${blocker.effectiveApps.size})") },
                                        onClick = {
                                            targetEnchantment = blocker
                                            enchantmentExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                // App picker
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
                }

                Spacer(modifier = Modifier.height(16.dp))

                PactDropdown(
                    label = stringResource(R.string.time_limits_pact_max_label),
                    options = pactMaxOptions,
                    selected = pactMaxMinutes,
                    expanded = pactMaxExpanded,
                    onExpandedChange = { pactMaxExpanded = it },
                    onSelect = { pactMaxMinutes = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                PactDropdown(
                    label = stringResource(R.string.time_limits_pact_seal_label),
                    options = sealOptions,
                    selected = sealMinutes,
                    expanded = sealExpanded,
                    onExpandedChange = { sealExpanded = it },
                    onSelect = { sealMinutes = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                PactDropdown(
                    label = stringResource(R.string.pacts_backstop_label),
                    options = backstopOptions,
                    selected = backstopMinutes,
                    expanded = backstopExpanded,
                    onExpandedChange = { backstopExpanded = it },
                    onSelect = { backstopMinutes = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Optional healthier substitute
                Box {
                    OutlinedTextField(
                        value = alternativeApp?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.time_limits_pact_alternative_label)) },
                        placeholder = { Text(stringResource(R.string.time_limits_pact_alternative_none)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showAlternativePicker = true }
                    )
                }
                if (alternativeApp != null) {
                    TextButton(onClick = { alternativeApp = null }) {
                        Text(stringResource(R.string.time_limits_pact_alternative_clear))
                    }
                }
                if (showAlternativePicker) {
                    SingleAppPickerDialog(
                        installedApps = installedApps.filter { it.packageName != selectedApp?.packageName },
                        title = stringResource(R.string.time_limits_pact_alternative_label),
                        selectedPackage = alternativeApp?.packageName,
                        onPick = { app ->
                            alternativeApp = app
                            showAlternativePicker = false
                        },
                        onDismiss = { showAlternativePicker = false }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (useEnchantment) {
                        targetEnchantment?.let { blocker ->
                            onSaveGroup(
                                PactGroup(
                                    blockerName = blocker.name,
                                    pactMaxMinutes = pactMaxMinutes,
                                    cooldownMinutes = sealMinutes,
                                    pactAlternativePackage = alternativeApp?.packageName,
                                    dailyLimitMinutes = backstopMinutes
                                )
                            )
                        }
                    } else {
                        selectedApp?.let { app ->
                            onSave(
                                AppTimeLimit(
                                    packageName = app.packageName,
                                    dailyLimitMinutes = backstopMinutes,
                                    sessionLimitMinutes = 0,
                                    cooldownMinutes = sealMinutes,
                                    pactModeEnabled = true,
                                    pactMaxMinutes = pactMaxMinutes,
                                    pactAlternativePackage = alternativeApp?.packageName
                                )
                            )
                        }
                    }
                },
                enabled = if (useEnchantment) targetEnchantment != null else selectedApp != null
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PactDropdown(
    label: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (Int) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { onExpandedChange(!expanded) }
    ) {
        OutlinedTextField(
            value = options.find { it.first == selected }?.second
                ?: stringResource(R.string.format_duration_minutes, selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            options.forEach { (value, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        onSelect(value)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}
