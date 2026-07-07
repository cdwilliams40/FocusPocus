package com.infinicada.focuspocus.ui.screens

import kotlin.math.roundToInt
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.ui.components.ArcaneBackground
import com.infinicada.focuspocus.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
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
    muteNotifications: Boolean,
    isNotificationListenerEnabled: Boolean,
    onMuteNotificationsChanged: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    nfcLockMode: Boolean,
    onNfcLockModeChanged: (Boolean) -> Unit,
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
