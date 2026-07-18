package com.infinicada.focuspocus.ui.screens

import kotlin.math.roundToInt
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.DeviceOwnerManager
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.ProtectionHealth
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.limit.GuardStatus
import com.infinicada.focuspocus.ui.components.ArcaneBackground
import com.infinicada.focuspocus.ui.formatDuration
import com.infinicada.focuspocus.ui.theme.ThemeMode
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    protectionStatus: ProtectionHealth.Status,
    onFixAccessibility: () -> Unit,
    onFixUsageAccess: () -> Unit,
    onFixNotifications: () -> Unit,
    onFixBattery: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    breakDurationMinutes: Int,
    maxBreaksPerSession: Int,
    onBreakDurationChanged: (Int) -> Unit,
    onMaxBreaksChanged: (Int) -> Unit,
    emergencyBreakCadenceWeeks: Int,
    onEmergencyBreakCadenceChanged: (Int) -> Unit,
    autoBreakEnabled: Boolean,
    onAutoBreakEnabledChanged: (Boolean) -> Unit,
    autoBreakIntervalMinutes: Int,
    onAutoBreakIntervalChanged: (Int) -> Unit,
    hideStopButton: Boolean,
    onHideStopButtonChanged: (Boolean) -> Unit,
    progressionEnabled: Boolean,
    onProgressionEnabledChanged: (Boolean) -> Unit,
    wrapupEnabled: Boolean,
    onWrapupEnabledChanged: (Boolean) -> Unit,
    trialAlertsEnabled: Boolean,
    onTrialAlertsEnabledChanged: (Boolean) -> Unit,
    muteNotifications: Boolean,
    isNotificationListenerEnabled: Boolean,
    onMuteNotificationsChanged: (Boolean) -> Unit,
    sealLiftedAlertsEnabled: Boolean,
    onSealLiftedAlertsChanged: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    nfcLockMode: Boolean,
    onNfcLockModeChanged: (Boolean) -> Unit,
    isDeviceOwner: Boolean,
    deviceOwnerEnforcement: Boolean,
    onDeviceOwnerEnforcementChanged: (Boolean) -> Unit,
    deviceOwnerSuspendPacts: Boolean,
    onDeviceOwnerSuspendPactsChanged: (Boolean) -> Unit,
    wardenRemovalRequestMillis: Long,
    onRequestWardenRemoval: () -> Unit,
    onCancelWardenRemoval: () -> Unit,
    onRefreshDeviceOwner: () -> Unit,
    onRemoveDeviceOwner: () -> Unit,
    analyticsConsent: Boolean,
    onAnalyticsConsentChanged: (Boolean) -> Unit,
    namedTags: List<NamedTag>,
    focusMode: Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    ArcaneBackground(modifier = modifier) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Protection Health Card — the "am I actually protected?" glance
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.settings_protection_health),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (protectionStatus.allHealthy) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.settings_protection_all_good),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    ProtectionHealthRow(
                        healthy = protectionStatus.accessibilityEnabled,
                        title = stringResource(R.string.settings_protection_accessibility),
                        problem = stringResource(R.string.settings_protection_accessibility_off),
                        onFix = onFixAccessibility
                    )
                    ProtectionHealthRow(
                        healthy = protectionStatus.usageAccessGranted,
                        title = stringResource(R.string.settings_protection_usage),
                        problem = stringResource(R.string.settings_protection_usage_off),
                        onFix = onFixUsageAccess
                    )
                    ProtectionHealthRow(
                        healthy = protectionStatus.notificationsEnabled,
                        title = stringResource(R.string.settings_protection_notifications),
                        problem = stringResource(R.string.settings_protection_notifications_off),
                        onFix = onFixNotifications
                    )
                    ProtectionHealthRow(
                        healthy = protectionStatus.batteryUnrestricted,
                        title = stringResource(R.string.settings_protection_battery),
                        problem = stringResource(R.string.settings_protection_battery_off),
                        onFix = onFixBattery
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Appearance Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)
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
                                    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                                    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                                    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                                },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Focus Behavior Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_focus_behavior), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (focusMode) {
                        Text(
                            stringResource(R.string.settings_cannot_change_active),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text(
                        stringResource(R.string.settings_break_duration, breakDurationMinutes),
                        color = if (focusMode) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = breakDurationMinutes.coerceIn(1, 30).toFloat(),
                        onValueChange = { onBreakDurationChanged(it.roundToInt()) },
                        valueRange = 1f..30f,
                        steps = 28,
                        enabled = !focusMode,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        stringResource(R.string.settings_breaks_per_session, maxBreaksPerSession),
                        color = if (focusMode) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = maxBreaksPerSession.coerceIn(1, 10).toFloat(),
                        onValueChange = { onMaxBreaksChanged(it.roundToInt()) },
                        valueRange = 1f..10f,
                        steps = 8,
                        enabled = !focusMode,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_auto_break))
                            Text(
                                stringResource(R.string.settings_auto_break_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoBreakEnabled,
                            onCheckedChange = onAutoBreakEnabledChanged,
                            enabled = !focusMode
                        )
                    }

                    if (autoBreakEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.settings_auto_break_interval, autoBreakIntervalMinutes),
                            color = if (focusMode) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                        )
                        Slider(
                            value = autoBreakIntervalMinutes.coerceIn(5, 60).toFloat(),
                            onValueChange = { onAutoBreakIntervalChanged((it / 5).roundToInt() * 5) },
                            valueRange = 5f..60f,
                            steps = 10,
                            enabled = !focusMode,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        stringResource(R.string.settings_emergency_cooldown, emergencyBreakCadenceWeeks),
                        color = if (focusMode) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.settings_emergency_cooldown_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = emergencyBreakCadenceWeeks.coerceIn(2, 8).toFloat(),
                        onValueChange = { onEmergencyBreakCadenceChanged(it.roundToInt()) },
                        valueRange = 2f..8f,
                        steps = 5,
                        enabled = !focusMode,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_hide_stop_button))
                            Text(
                                stringResource(R.string.settings_hide_stop_button_desc),
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

            // Progression Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_progression_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_progression_toggle))
                            Text(
                                stringResource(R.string.settings_progression_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = progressionEnabled,
                            onCheckedChange = onProgressionEnabledChanged
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_wrapup_toggle))
                            Text(
                                stringResource(R.string.settings_wrapup_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = wrapupEnabled,
                            onCheckedChange = onWrapupEnabledChanged,
                            enabled = progressionEnabled
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_trial_alerts_toggle))
                            Text(
                                stringResource(R.string.settings_trial_alerts_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = trialAlertsEnabled,
                            onCheckedChange = onTrialAlertsEnabledChanged,
                            enabled = progressionEnabled
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Notifications Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_notifications), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_mute_during_focus))
                            Text(
                                stringResource(R.string.settings_mute_desc),
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
                            Text(stringResource(R.string.settings_grant_notification))
                        }
                        Text(
                            stringResource(R.string.settings_notification_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_seal_lifted_toggle))
                            Text(
                                stringResource(R.string.settings_seal_lifted_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = sealLiftedAlertsEnabled,
                            onCheckedChange = onSealLiftedAlertsChanged
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Security Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_security), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_talisman_lock))
                            Text(
                                stringResource(R.string.settings_talisman_lock_desc),
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
                            stringResource(R.string.settings_add_talisman_first),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Warden Mode (device owner) Card
            var showRemoveWardenDialog by remember { mutableStateOf(false) }
            var showRequestRemovalDialog by remember { mutableStateOf(false) }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_device_owner), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isDeviceOwner) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_device_owner_suspend))
                                Text(
                                    stringResource(R.string.settings_device_owner_suspend_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = deviceOwnerEnforcement,
                                onCheckedChange = onDeviceOwnerEnforcementChanged
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_device_owner_suspend_pacts))
                                Text(
                                    stringResource(R.string.settings_device_owner_suspend_pacts_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = deviceOwnerSuspendPacts,
                                onCheckedChange = onDeviceOwnerSuspendPactsChanged,
                                // Greying rides on the master enforcement toggle above.
                                enabled = deviceOwnerEnforcement
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.settings_device_owner_active),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val warnContext = LocalContext.current
                        val isTestOnlyBuild = remember {
                            DeviceOwnerManager.isTestOnlyBuild(warnContext)
                        }
                        if (isTestOnlyBuild) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.settings_device_owner_testonly_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        // Removal is a two-step act: request it, wait out the
                        // 24-hour cooling-off period, then remove for real. A
                        // 30-second tick keeps the countdown (and the unlock
                        // moment) live while the screen stays open.
                        var wardenNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
                        LaunchedEffect(wardenRemovalRequestMillis) {
                            while (wardenRemovalRequestMillis > 0L) {
                                wardenNow = System.currentTimeMillis()
                                delay(30_000L)
                            }
                        }
                        val removalUnlocked =
                            DeviceOwnerManager.isRemovalUnlocked(wardenRemovalRequestMillis, wardenNow)
                        when {
                            wardenRemovalRequestMillis <= 0L -> {
                                OutlinedButton(
                                    onClick = { showRequestRemovalDialog = true },
                                    enabled = !focusMode,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.settings_device_owner_remove_request))
                                }
                            }
                            !removalUnlocked -> {
                                Text(
                                    stringResource(
                                        R.string.settings_device_owner_remove_pending,
                                        formatDuration(
                                            GuardStatus.minutesUntil(
                                                wardenRemovalRequestMillis +
                                                    DeviceOwnerManager.REMOVAL_COOLDOWN_MS,
                                                wardenNow
                                            )
                                        )
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                TextButton(onClick = onCancelWardenRemoval) {
                                    Text(stringResource(R.string.settings_device_owner_remove_cancel_request))
                                }
                            }
                            else -> {
                                OutlinedButton(
                                    onClick = { showRemoveWardenDialog = true },
                                    enabled = !focusMode,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.settings_device_owner_remove))
                                }
                                TextButton(onClick = onCancelWardenRemoval) {
                                    Text(stringResource(R.string.settings_device_owner_remove_cancel_request))
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.settings_device_owner_remove_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        WardenSetupGuide(onRefreshDeviceOwner = onRefreshDeviceOwner)
                    }
                }
            }
            if (showRemoveWardenDialog) {
                AlertDialog(
                    onDismissRequest = { showRemoveWardenDialog = false },
                    title = { Text(stringResource(R.string.settings_device_owner_remove_confirm_title)) },
                    text = { Text(stringResource(R.string.settings_device_owner_remove_confirm_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showRemoveWardenDialog = false
                            onRemoveDeviceOwner()
                        }) {
                            Text(stringResource(R.string.settings_device_owner_remove_confirm_yes))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRemoveWardenDialog = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }
            if (showRequestRemovalDialog) {
                AlertDialog(
                    onDismissRequest = { showRequestRemovalDialog = false },
                    title = { Text(stringResource(R.string.settings_device_owner_request_confirm_title)) },
                    text = { Text(stringResource(R.string.settings_device_owner_request_confirm_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showRequestRemovalDialog = false
                            onRequestWardenRemoval()
                        }) {
                            Text(stringResource(R.string.settings_device_owner_request_confirm_yes))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRequestRemovalDialog = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Privacy Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_analytics))
                            Text(
                                stringResource(R.string.settings_analytics_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = analyticsConsent,
                            onCheckedChange = onAnalyticsConsentChanged
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    }
}

/** One protection check: a status icon and, when unhealthy, the why + a fix link. */
@Composable
private fun ProtectionHealthRow(
    healthy: Boolean,
    title: String,
    problem: String,
    onFix: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (healthy) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
            contentDescription = stringResource(
                if (healthy) R.string.settings_protection_ok_desc
                else R.string.settings_protection_problem_desc
            ),
            tint = if (healthy) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.error
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (!healthy) {
                Text(
                    problem,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (!healthy) {
            TextButton(onClick = onFix) {
                Text(stringResource(R.string.settings_protection_fix))
            }
        }
    }
}

/**
 * Step-by-step provisioning guide shown while FocusPocus is not yet device owner:
 * numbered instructions, tap-to-copy adb commands, a status re-check button, and
 * troubleshooting for the "already some accounts on the device" error.
 */
@Composable
private fun WardenSetupGuide(onRefreshDeviceOwner: () -> Unit) {
    Text(
        stringResource(R.string.settings_device_owner_inactive_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    SetupStep(1, stringResource(R.string.settings_device_owner_step1))
    SetupStep(2, stringResource(R.string.settings_device_owner_step2))
    SetupStep(3, stringResource(R.string.settings_device_owner_step3))
    AdbCommandRow(DeviceOwnerManager.SET_DEVICE_OWNER_COMMAND)
    Spacer(modifier = Modifier.height(8.dp))

    var checkedStatus by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = {
            checkedStatus = true
            onRefreshDeviceOwner()
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.settings_device_owner_check_status))
    }
    // Only visible while still not device owner: on success the whole card
    // switches to the active state instead.
    if (checkedStatus) {
        Text(
            stringResource(R.string.settings_device_owner_not_detected),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
    var showTroubleshooting by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showTroubleshooting = !showTroubleshooting },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.settings_device_owner_troubleshoot_title),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            if (showTroubleshooting) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
    }
    if (showTroubleshooting) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_device_owner_troubleshoot_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AdbCommandRow(DeviceOwnerManager.LIST_ACCOUNTS_COMMAND)
        Text(
            stringResource(R.string.settings_device_owner_troubleshoot_fix),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SetupStep(number: Int, text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            "$number.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 6.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AdbCommandRow(command: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                command,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
        IconButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("adb command", command))
            // Android 13+ shows its own clipboard confirmation overlay.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, R.string.settings_device_owner_copied, Toast.LENGTH_SHORT).show()
            }
        }) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = stringResource(R.string.settings_device_owner_copy_command),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
