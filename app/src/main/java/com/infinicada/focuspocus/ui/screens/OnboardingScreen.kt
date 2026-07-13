package com.infinicada.focuspocus.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material3.Switch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import com.infinicada.focuspocus.model.AppInfo
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.BlockerMode
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.MyAccessibilityService
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.UsageStatsHelper
import com.infinicada.focuspocus.ui.components.AppPickerDialog
import com.infinicada.focuspocus.ui.components.ArcaneBackground

@Composable
fun OnboardingScreen(
    namedTags: List<NamedTag>,
    blockerLists: List<Blocker>,
    installedApps: List<AppInfo>,
    isServiceEnabled: Boolean,
    analyticsConsent: Boolean,
    onAnalyticsConsentChanged: (Boolean) -> Unit,
    onSaveBlocker: (Blocker) -> Unit,
    onSaveTag: (String) -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val totalSteps = 7
    // Saveable so rotation or process recreation doesn't restart the wizard.
    var localAnalyticsConsent by rememberSaveable { mutableStateOf(analyticsConsent) }
    var currentStep by rememberSaveable { mutableIntStateOf(0) }

    // Re-check permissions on resume
    var accessibilityEnabled by remember { mutableStateOf(isServiceEnabled) }
    var notificationPolicyGranted by remember {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mutableStateOf(nm.isNotificationPolicyAccessGranted)
    }
    var usageStatsGranted by remember {
        mutableStateOf(UsageStatsHelper.hasUsageStatsPermission(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityEnabled = isAccessibilityEnabled(context)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationPolicyGranted = nm.isNotificationPolicyAccessGranted
                usageStatsGranted = UsageStatsHelper.hasUsageStatsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ArcaneBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // No Scaffold here, so under edge-to-edge the wizard must keep
                // itself out from behind the status and navigation bars.
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // Segmented step progress
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(totalSteps) { step ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (step <= currentStep) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                )
            }
        }

        Text(
            stringResource(R.string.onboarding_step_of, currentStep + 1, totalSteps),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.weight(1f)) {
            when (currentStep) {
                0 -> WelcomeStep()
                1 -> AccessibilityStep(
                    isEnabled = accessibilityEnabled,
                    onEnable = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
                2 -> CreateBlockerStep(
                    blockerLists = blockerLists,
                    installedApps = installedApps,
                    onSaveBlocker = onSaveBlocker
                )
                3 -> NotificationStep(
                    isGranted = notificationPolicyGranted,
                    onGrant = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    }
                )
                4 -> UsageStatsStep(
                    isGranted = usageStatsGranted,
                    onGrant = { UsageStatsHelper.openUsageAccessSettings(context) }
                )
                5 -> AnalyticsConsentStep(
                    isEnabled = localAnalyticsConsent,
                    onToggle = { enabled ->
                        localAnalyticsConsent = enabled
                        onAnalyticsConsentChanged(enabled)
                    }
                )
                6 -> DoneStep()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentStep > 0) {
                OutlinedButton(onClick = { currentStep-- }) {
                    Text(stringResource(R.string.action_back))
                }
            } else {
                Spacer(modifier = Modifier.size(1.dp))
            }

            if (currentStep < totalSteps - 1) {
                val canProceed = when (currentStep) {
                    1 -> true // Accessibility - allow skip but warn
                    2 -> blockerLists.isNotEmpty() // Must create at least one blocker
                    else -> true
                }
                Button(
                    onClick = { currentStep++ },
                    enabled = canProceed
                ) {
                    Text(if (currentStep == 1 && !accessibilityEnabled) stringResource(R.string.action_skip) else stringResource(R.string.action_next))
                }
            } else {
                Button(
                    onClick = onComplete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.onboarding_start_focusing))
                }
            }
        }
        }
    }
}

/** Icon inside a soft tonal circle, used as the visual anchor of each step. */
@Composable
private fun StepHero(
    icon: ImageVector,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = content
        )
    }
}

/** Confirmation card shown once a permission or setup step is complete. */
@Composable
private fun StatusConfirmedCard(text: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(text, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        StepHero(Icons.Default.AutoFixHigh)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_welcome_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AccessibilityStep(
    isEnabled: Boolean,
    onEnable: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        StepHero(Icons.Default.Accessibility)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(R.string.onboarding_accessibility_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.onboarding_accessibility_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (isEnabled) {
            StatusConfirmedCard(stringResource(R.string.onboarding_accessibility_enabled))
        } else {
            Button(onClick = onEnable) {
                Text(stringResource(R.string.onboarding_enable_accessibility))
            }
        }
    }
}

@Composable
private fun CreateBlockerStep(
    blockerLists: List<Blocker>,
    installedApps: List<AppInfo>,
    onSaveBlocker: (Blocker) -> Unit
) {
    var blockerName by remember { mutableStateOf("") }
    var selectedApps by remember { mutableStateOf(emptyList<String>()) }
    var showAppDialog by remember { mutableStateOf(false) }

    if (showAppDialog) {
        AppPickerDialog(
            installedApps = installedApps,
            title = stringResource(R.string.spells_select_apps_title),
            initialSelection = selectedApps,
            onConfirm = { apps ->
                selectedApps = apps
                showAppDialog = false
            },
            onDismiss = { showAppDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                stringResource(R.string.onboarding_create_enchantment_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.onboarding_create_enchantment_desc),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (blockerLists.isNotEmpty()) {
            item {
                StatusConfirmedCard(
                    stringResource(R.string.onboarding_enchantment_created),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        item {
            TextField(
                value = blockerName,
                onValueChange = { if (it.length <= 100) blockerName = it },
                label = { Text(stringResource(R.string.spells_enchantment_name_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { showAppDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_select_apps, selectedApps.size))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    onSaveBlocker(Blocker(blockerName.trim(), BlockerMode.BLACKLIST, selectedApps.toSet()))
                    blockerName = ""
                    selectedApps = emptyList()
                },
                enabled = blockerName.isNotBlank() && selectedApps.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.onboarding_create_enchantment))
            }
        }
    }
}

@Composable
private fun NotificationStep(
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        StepHero(Icons.Default.DoNotDisturbOn)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(R.string.onboarding_notification_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.onboarding_notification_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (isGranted) {
            StatusConfirmedCard(stringResource(R.string.onboarding_notification_granted))
        } else {
            Button(onClick = onGrant) {
                Text(stringResource(R.string.onboarding_grant_notification))
            }
        }
    }
}

@Composable
private fun UsageStatsStep(
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        StepHero(Icons.Default.DataUsage)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(R.string.onboarding_usage_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.onboarding_usage_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (isGranted) {
            StatusConfirmedCard(stringResource(R.string.onboarding_usage_granted))
        } else {
            Button(onClick = onGrant) {
                Text(stringResource(R.string.onboarding_grant_usage))
            }
        }
    }
}

@Composable
private fun AnalyticsConsentStep(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        StepHero(Icons.Default.BarChart)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(R.string.onboarding_analytics_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.onboarding_analytics_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.onboarding_analytics_toggle))
                Text(
                    stringResource(R.string.onboarding_analytics_toggle_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun DoneStep() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        StepHero(
            Icons.Default.Check,
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(R.string.onboarding_done_title),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_done_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val expectedComponentName = ComponentName(context, MyAccessibilityService::class.java)
    val enabledServicesSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServicesSetting)
    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledComponent = ComponentName.unflattenFromString(componentNameString)
        if (enabledComponent != null && enabledComponent == expectedComponentName)
            return true
    }
    return false
}
