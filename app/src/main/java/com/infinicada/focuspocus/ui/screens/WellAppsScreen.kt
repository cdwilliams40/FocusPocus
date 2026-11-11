package com.infinicada.focuspocus.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.limit.GuardStatus
import com.infinicada.focuspocus.limit.WellApp
import com.infinicada.focuspocus.model.AppInfo
import com.infinicada.focuspocus.ui.components.AppIcon
import com.infinicada.focuspocus.ui.components.AppPickerDialog
import com.infinicada.focuspocus.ui.components.GlassCard
import com.infinicada.focuspocus.ui.components.SingleAppPickerDialog
import com.infinicada.focuspocus.ui.formatDuration

/**
 * The Well's app list. Adding an app is instant; taking one off waits 24
 * hours and can be called off until then. Each app can name a healthier
 * substitute that its pact screen offers first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WellAppsScreen(
    installedApps: List<AppInfo>,
    wellApps: List<WellApp>,
    pendingRemovals: Map<String, Long>,
    nowMillis: Long,
    onAddApps: (List<String>) -> Unit,
    onSetSubstitute: (packageName: String, substitute: String?) -> Unit,
    onRequestRemoval: (String) -> Unit,
    onCancelRemoval: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val names = remember(installedApps) { installedApps.associate { it.packageName to it.name } }
    val wellPackages = remember(wellApps) { wellApps.mapTo(mutableSetOf()) { it.packageName } }
    var showAddPicker by remember { mutableStateOf(false) }
    var substituteFor by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.well_apps_title)) },
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
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.well_apps_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            items(wellApps, key = { it.packageName }) { app ->
                WellAppCard(
                    app = app,
                    appName = names[app.packageName] ?: app.packageName,
                    substituteName = app.substitutePackage?.let { names[it] ?: it },
                    removalAt = pendingRemovals[app.packageName],
                    nowMillis = nowMillis,
                    onPickSubstitute = { substituteFor = app.packageName },
                    onClearSubstitute = { onSetSubstitute(app.packageName, null) },
                    onRequestRemoval = { onRequestRemoval(app.packageName) },
                    onCancelRemoval = { onCancelRemoval(app.packageName) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { showAddPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.well_apps_add))
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showAddPicker) {
        AppPickerDialog(
            installedApps = installedApps.filter { it.packageName !in wellPackages },
            title = stringResource(R.string.well_apps_add),
            initialSelection = emptyList(),
            onConfirm = { picked ->
                showAddPicker = false
                if (picked.isNotEmpty()) onAddApps(picked)
            },
            onDismiss = { showAddPicker = false }
        )
    }

    substituteFor?.let { pkg ->
        SingleAppPickerDialog(
            installedApps = installedApps.filter { it.packageName !in wellPackages },
            title = stringResource(R.string.well_apps_pick_substitute),
            selectedPackage = wellApps.firstOrNull { it.packageName == pkg }?.substitutePackage,
            onPick = { substitute ->
                substituteFor = null
                onSetSubstitute(pkg, substitute.packageName)
            },
            onDismiss = { substituteFor = null }
        )
    }
}

@Composable
private fun WellAppCard(
    app: WellApp,
    appName: String,
    substituteName: String?,
    removalAt: Long?,
    nowMillis: Long,
    onPickSubstitute: () -> Unit,
    onClearSubstitute: () -> Unit,
    onRequestRemoval: () -> Unit,
    onCancelRemoval: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName = app.packageName, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(appName, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (substituteName != null) stringResource(R.string.well_apps_substitute, substituteName)
                    else stringResource(R.string.well_apps_no_substitute),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (removalAt != null) {
                    Text(
                        stringResource(
                            R.string.well_app_removal_pending,
                            formatDuration(GuardStatus.minutesUntil(removalAt, nowMillis).coerceAtLeast(1))
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onPickSubstitute) {
                Text(stringResource(R.string.well_apps_set_substitute))
            }
            if (substituteName != null) {
                TextButton(onClick = onClearSubstitute) {
                    Text(stringResource(R.string.well_apps_clear_substitute))
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (removalAt != null) {
                TextButton(onClick = onCancelRemoval) {
                    Text(stringResource(R.string.well_apps_keep))
                }
            } else {
                TextButton(onClick = onRequestRemoval) {
                    Text(stringResource(R.string.well_apps_remove))
                }
            }
        }
    }
}
