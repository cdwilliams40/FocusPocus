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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.infinicada.focuspocus.AppInfo
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.BlockerMode
import com.infinicada.focuspocus.MyAccessibilityService
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.UsageStatsHelper

@Composable
fun OnboardingScreen(
    namedTags: List<NamedTag>,
    blockerLists: List<Blocker>,
    installedApps: List<AppInfo>,
    isServiceEnabled: Boolean,
    onSaveBlocker: (Blocker) -> Unit,
    onSaveTag: (String) -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val totalSteps = 6
    var currentStep by remember { mutableIntStateOf(0) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress bar
        LinearProgressIndicator(
            progress = { (currentStep + 1).toFloat() / totalSteps },
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        )

        Text(
            "Step ${currentStep + 1} of $totalSteps",
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
                5 -> DoneStep()
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
                    Text("Back")
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
                    Text(if (currentStep == 1 && !accessibilityEnabled) "Skip" else "Next")
                }
            } else {
                Button(
                    onClick = onComplete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Start Focusing")
                }
            }
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
        Icon(
            Icons.Default.AutoFixHigh,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Welcome to Focus Pocus",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Your magical focus companion. Let's set up a few things to help you stay focused and productive.",
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
        Text(
            "Grant Magical Sight",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Focus Pocus needs Accessibility permission to detect when you open distracting apps and gently guide you back to your focus.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (isEnabled) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "  Accessibility enabled",
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        } else {
            Button(onClick = onEnable) {
                Text("Enable Accessibility Service")
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
        AppSelectionDialog(
            installedApps = installedApps,
            selectedApps = selectedApps,
            onSave = { apps ->
                selectedApps = apps
                showAppDialog = false
            },
            onDismissRequest = { showAppDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                "Create Your First Enchantment",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Select the apps you want to block during focus sessions.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (blockerLists.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "  Enchantment created! You can create more later.",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        item {
            TextField(
                value = blockerName,
                onValueChange = { if (it.length <= 100) blockerName = it },
                label = { Text("Enchantment Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { showAppDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Select Apps to Block (${selectedApps.size} selected)")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    onSaveBlocker(Blocker(blockerName.trim(), BlockerMode.BLACKLIST, selectedApps))
                    blockerName = ""
                    selectedApps = emptyList()
                },
                enabled = blockerName.isNotBlank() && selectedApps.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create Enchantment")
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
        Text(
            "Notification Access",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Grant notification access to block notifications from distracting apps and enable Do Not Disturb during focus.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (isGranted) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("  Notification access granted", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        } else {
            Button(onClick = onGrant) {
                Text("Grant Notification Access")
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
        Text(
            "Usage Statistics",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Grant usage access to see app usage insights and enable per-app time limits.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (isGranted) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("  Usage access granted", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        } else {
            Button(onClick = onGrant) {
                Text("Grant Usage Access")
            }
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
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "You're All Set!",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Focus Pocus is ready to help you focus. You can always adjust settings, add talismans, and create more enchantments from the Wizard tab.",
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
