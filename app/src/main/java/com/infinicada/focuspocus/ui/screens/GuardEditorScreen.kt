package com.infinicada.focuspocus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.BlockerMode
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.limit.GuardStatus
import com.infinicada.focuspocus.limit.PactManager
import com.infinicada.focuspocus.model.AppInfo
import com.infinicada.focuspocus.model.AppTimeLimit
import com.infinicada.focuspocus.model.PactGroup
import com.infinicada.focuspocus.ui.components.AppIcon
import com.infinicada.focuspocus.ui.components.SingleAppPickerDialog

/**
 * The unified guard editor: creates and edits pacts and wards over the single
 * [AppTimeLimit] store, and pact circles over the [PactGroup] store. Replaces
 * the separate Pacts/Time Limits add dialogs; converting an app between the
 * two styles is just flipping the style here — the irrelevant style's fields
 * are zeroed on save, matching what the old dialogs wrote.
 *
 * Exactly one editing mode applies: [editPackageName] set (edit an app's
 * config), [editCircleName] set (edit a circle), or neither (create).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardEditorScreen(
    installedApps: List<AppInfo>,
    blockerLists: List<Blocker>,
    pactGroups: List<PactGroup>,
    appTimeLimitConfigs: Map<String, AppTimeLimit>,
    editPackageName: String?,
    editCircleName: String?,
    usageAccessGranted: Boolean,
    onGrantUsageAccess: () -> Unit,
    onSaveConfig: (AppTimeLimit) -> Unit,
    onDeleteConfig: (String) -> Unit,
    onSaveGroup: (PactGroup) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onOpenEnchantment: (Blocker) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val editingConfig = editPackageName?.let { appTimeLimitConfigs[it] }
    val editingGroup = editCircleName?.let { name -> pactGroups.find { it.blockerName == name } }

    // The edited guard can disappear underneath us (deleted elsewhere, or an
    // enchantment removal cascading its circle) — bail out instead of editing
    // a ghost.
    val editTargetGone = (editPackageName != null && editingConfig == null) ||
        (editCircleName != null && editingGroup == null)
    if (editTargetGone) {
        LaunchedEffect(Unit) { onNavigateBack() }
        return
    }

    val isEditing = editingConfig != null || editingGroup != null

    // ── Target (create mode only) ──
    var targetCircle by remember { mutableStateOf(editingGroup != null) }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }
    var targetEnchantment by remember {
        mutableStateOf(editingGroup?.let { g -> blockerLists.find { it.name == g.blockerName } })
    }
    var enchantmentExpanded by remember { mutableStateOf(false) }

    // An app is guarded by at most one config; blacklists carry at most one circle.
    val availableApps = remember(installedApps, appTimeLimitConfigs) {
        installedApps.filter { it.packageName !in appTimeLimitConfigs }
    }
    val availableBlacklists = remember(blockerLists, pactGroups) {
        blockerLists.filter { blocker ->
            blocker.mode == BlockerMode.BLACKLIST && pactGroups.none { it.blockerName == blocker.name }
        }
    }

    // ── Style ──
    val initial = editingGroup?.toAppTimeLimit(editPackageName ?: "") ?: editingConfig
    var pactStyle by remember { mutableStateOf(initial?.pactModeEnabled ?: true) }
    val isCircle = targetCircle || editingGroup != null

    // ── Pact fields ──
    var pactMaxMinutes by remember {
        mutableIntStateOf(initial?.pactMaxMinutes?.takeIf { it > 0 } ?: PactManager.DEFAULT_MAX_MINUTES)
    }
    var sealMinutes by remember { mutableIntStateOf(initial?.cooldownMinutes ?: 30) }
    // Both styles store the daily cap in dailyLimitMinutes, so both fields
    // seed from it — converting a guard between styles must not drop the cap.
    var backstopMinutes by remember { mutableIntStateOf(initial?.dailyLimitMinutes ?: 0) }
    // Held as a package name, not an AppInfo: installedApps loads
    // asynchronously, and resolving against a not-yet-loaded list here would
    // silently wipe the stored substitute on the next save.
    var alternativePackage by remember { mutableStateOf(initial?.pactAlternativePackage) }
    var showAlternativePicker by remember { mutableStateOf(false) }

    // ── Ward fields ──
    var dailyLimitMinutes by remember {
        mutableIntStateOf(initial?.dailyLimitMinutes?.takeIf { it > 0 } ?: 30)
    }
    var sessionCooldownEnabled by remember {
        mutableStateOf((initial?.sessionLimitMinutes ?: 0) > 0)
    }
    var sessionLimitMinutes by remember {
        mutableIntStateOf(initial?.sessionLimitMinutes?.takeIf { it > 0 } ?: 10)
    }

    // ── Shared: escalation (drives seals for pacts, cooldowns for wards) ──
    var escalationEnabled by remember {
        mutableStateOf(initial?.cooldownEscalationEnabled ?: false)
    }
    var escalationStepMinutes by remember {
        mutableIntStateOf(initial?.cooldownEscalationStepMinutes?.takeIf { it > 0 } ?: 15)
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

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
    val dailyLimitOptions = listOf(
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
    val escalationStepOptions = listOf(
        5 to stringResource(R.string.duration_5_min),
        10 to stringResource(R.string.duration_10_min),
        15 to stringResource(R.string.duration_15_min),
        30 to stringResource(R.string.duration_30_min)
    )

    val canSave = when {
        isEditing -> true
        isCircle -> targetEnchantment != null
        else -> selectedApp != null
    }

    fun save() {
        if (isCircle) {
            val blockerName = editingGroup?.blockerName ?: targetEnchantment?.name ?: return
            onSaveGroup(
                PactGroup(
                    blockerName = blockerName,
                    pactMaxMinutes = pactMaxMinutes,
                    cooldownMinutes = sealMinutes,
                    cooldownEscalationEnabled = escalationEnabled,
                    cooldownEscalationStepMinutes = escalationStepMinutes,
                    pactAlternativePackage = alternativePackage,
                    dailyLimitMinutes = backstopMinutes
                )
            )
        } else {
            val packageName = editPackageName ?: selectedApp?.packageName ?: return
            onSaveConfig(
                if (pactStyle) {
                    AppTimeLimit(
                        packageName = packageName,
                        dailyLimitMinutes = backstopMinutes,
                        sessionLimitMinutes = 0,
                        cooldownMinutes = sealMinutes,
                        cooldownEscalationEnabled = escalationEnabled,
                        cooldownEscalationStepMinutes = escalationStepMinutes,
                        pactModeEnabled = true,
                        pactMaxMinutes = pactMaxMinutes,
                        pactAlternativePackage = alternativePackage
                    )
                } else {
                    AppTimeLimit(
                        packageName = packageName,
                        dailyLimitMinutes = dailyLimitMinutes,
                        sessionLimitMinutes = if (sessionCooldownEnabled) sessionLimitMinutes else 0,
                        cooldownMinutes = sealMinutes,
                        // Without a session cooldown there is nothing to
                        // escalate, and the toggle wasn't even on screen —
                        // don't persist a value chosen for the other style.
                        cooldownEscalationEnabled = sessionCooldownEnabled && escalationEnabled,
                        cooldownEscalationStepMinutes = escalationStepMinutes,
                        pactModeEnabled = false
                    )
                }
            )
        }
    }

    val titleRes = when {
        !isEditing -> R.string.home_guard_make_pact
        editingGroup != null -> R.string.guard_editor_edit_circle
        editingConfig?.pactModeEnabled == true -> R.string.guard_editor_edit_pact
        else -> R.string.guard_editor_edit_ward
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ── Target ──
            if (isEditing) {
                EditedTargetHeader(
                    editingConfig = editingConfig,
                    editingGroup = editingGroup,
                    blockerLists = blockerLists,
                    installedApps = installedApps,
                    appTimeLimitConfigs = appTimeLimitConfigs,
                    onOpenEnchantment = onOpenEnchantment
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = !targetCircle, onClick = { targetCircle = false })
                    Text(
                        stringResource(R.string.pacts_target_app),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { targetCircle = false }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = targetCircle,
                        onClick = {
                            targetCircle = true
                            pactStyle = true
                        }
                    )
                    Text(
                        stringResource(R.string.pacts_target_enchantment),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable {
                            targetCircle = true
                            pactStyle = true
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (targetCircle) {
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
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = enchantmentExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
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
                    Box {
                        OutlinedTextField(
                            value = selectedApp?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.time_limits_app_label)) },
                            placeholder = { Text(stringResource(R.string.time_limits_select_app)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = showAppPicker)
                            },
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
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Style (locked to pact for circles) ──
            if (!isCircle) {
                Text(
                    stringResource(R.string.guard_editor_style_header),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                StyleOptionRow(
                    selected = pactStyle,
                    title = stringResource(R.string.guard_style_pact),
                    subtitle = stringResource(R.string.guard_style_pact_desc),
                    onClick = { pactStyle = true }
                )
                StyleOptionRow(
                    selected = !pactStyle,
                    title = stringResource(R.string.guard_style_ward),
                    subtitle = stringResource(R.string.guard_style_ward_desc),
                    onClick = { pactStyle = false }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Style fields ──
            if (pactStyle) {
                GuardDropdown(
                    label = stringResource(R.string.time_limits_pact_max_label),
                    options = pactMaxOptions,
                    selected = pactMaxMinutes,
                    onSelect = { pactMaxMinutes = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
                GuardDropdown(
                    label = stringResource(R.string.time_limits_pact_seal_label),
                    options = sealOptions,
                    selected = sealMinutes,
                    onSelect = { sealMinutes = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
                GuardDropdown(
                    label = stringResource(R.string.pacts_backstop_label),
                    options = backstopOptions,
                    selected = backstopMinutes,
                    onSelect = { backstopMinutes = it }
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Optional healthier substitute
                Box {
                    OutlinedTextField(
                        value = alternativePackage?.let { pkg ->
                            installedApps.find { it.packageName == pkg }?.name ?: pkg
                        } ?: "",
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
                if (alternativePackage != null) {
                    TextButton(onClick = { alternativePackage = null }) {
                        Text(stringResource(R.string.time_limits_pact_alternative_clear))
                    }
                }
                if (showAlternativePicker) {
                    SingleAppPickerDialog(
                        installedApps = installedApps.filter {
                            it.packageName != (editPackageName ?: selectedApp?.packageName)
                        },
                        title = stringResource(R.string.time_limits_pact_alternative_label),
                        selectedPackage = alternativePackage,
                        onPick = { app ->
                            alternativePackage = app.packageName
                            showAlternativePicker = false
                        },
                        onDismiss = { showAlternativePicker = false }
                    )
                }
            } else {
                GuardDropdown(
                    label = stringResource(R.string.time_limits_daily_limit_label),
                    options = dailyLimitOptions,
                    selected = dailyLimitMinutes,
                    onSelect = { dailyLimitMinutes = it }
                )
                Spacer(modifier = Modifier.height(12.dp))

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
                        checked = sessionCooldownEnabled,
                        onCheckedChange = { sessionCooldownEnabled = it }
                    )
                }
                if (sessionCooldownEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    GuardDropdown(
                        label = stringResource(R.string.time_limits_session_limit_label),
                        options = sessionLimitOptions,
                        selected = sessionLimitMinutes,
                        onSelect = { sessionLimitMinutes = it }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    GuardDropdown(
                        label = stringResource(R.string.time_limits_cooldown_label),
                        options = sealOptions,
                        selected = sealMinutes,
                        onSelect = { sealMinutes = it }
                    )
                }
            }

            // ── Escalation (pact seals always cool down; ward cooldowns only
            //    exist when the session cooldown is on) ──
            if (pactStyle || sessionCooldownEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(
                                if (pactStyle) R.string.guard_escalation_pact
                                else R.string.guard_escalation_ward
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(R.string.guard_escalation_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = escalationEnabled,
                        onCheckedChange = { escalationEnabled = it }
                    )
                }
                if (escalationEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    GuardDropdown(
                        label = stringResource(R.string.guard_escalation_step),
                        options = escalationStepOptions,
                        selected = escalationStepMinutes,
                        onSelect = { escalationStepMinutes = it }
                    )
                }
            }

            // A ward (or a pact's daily backstop) is inert without usage
            // access — the old Time Limits screen gated creation on it, so
            // the unified editor at least has to say so.
            if (!usageAccessGranted && (!pactStyle || backstopMinutes > 0)) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.time_limits_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onGrantUsageAccess) {
                    Text(stringResource(R.string.time_limits_grant_usage))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { save() },
                    enabled = canSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_save))
                }
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }

            if (isEditing) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_remove))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDeleteConfirm) {
        val targetName = editingGroup?.blockerName
            ?: installedApps.find { it.packageName == editPackageName }?.name
            ?: editPackageName.orEmpty()
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.guard_delete_confirm_title, targetName)) },
            text = {
                Text(
                    stringResource(
                        if (editingGroup != null) R.string.guard_delete_circle_message
                        else R.string.guard_delete_app_message
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        if (editingGroup != null) {
                            onDeleteGroup(editingGroup.blockerName)
                        } else {
                            editPackageName?.let(onDeleteConfig)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.action_remove))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/** Locked identity of the guard being edited: app icon + name, or circle + member count. */
@Composable
private fun EditedTargetHeader(
    editingConfig: AppTimeLimit?,
    editingGroup: PactGroup?,
    blockerLists: List<Blocker>,
    installedApps: List<AppInfo>,
    appTimeLimitConfigs: Map<String, AppTimeLimit>,
    onOpenEnchantment: (Blocker) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (editingGroup != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Link,
                    contentDescription = stringResource(R.string.home_guard_circle_icon_desc),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
        } else if (editingConfig != null) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                AppIcon(
                    packageName = editingConfig.packageName,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = editingGroup?.blockerName
                    ?: installedApps.find { it.packageName == editingConfig?.packageName }?.name
                    ?: editingConfig?.packageName.orEmpty(),
                style = MaterialTheme.typography.titleMedium
            )
            if (editingGroup != null) {
                // Same membership arithmetic as the dashboard card: apps with
                // an explicit per-app config aren't gated by this circle.
                Text(
                    stringResource(
                        R.string.pacts_group_summary,
                        GuardStatus.circleMemberPackages(
                            editingGroup, blockerLists, appTimeLimitConfigs
                        ).size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    if (editingGroup != null) {
        // Membership lives on the enchantment — that's the feature — so the
        // editor only links there instead of duplicating an app picker.
        val enchantment = blockerLists.find { it.name == editingGroup.blockerName }
        if (enchantment != null) {
            TextButton(onClick = { onOpenEnchantment(enchantment) }) {
                Text(stringResource(R.string.guard_circle_members_link))
            }
        }
    }
}

@Composable
private fun StyleOptionRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuardDropdown(
    label: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
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
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}
