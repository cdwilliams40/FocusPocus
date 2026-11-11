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
import com.infinicada.focuspocus.ManaRules
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.model.AppInfo
import com.infinicada.focuspocus.ui.components.AppIcon
import com.infinicada.focuspocus.ui.components.AppPickerDialog
import com.infinicada.focuspocus.ui.components.GlassCard

/**
 * Apps whose use earns mana. Time is read from usage stats, so without usage
 * access nothing can be counted; the screen says so and links to the grant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductiveAppsScreen(
    installedApps: List<AppInfo>,
    productiveApps: List<String>,
    minutesToday: Int,
    manaToday: Long,
    usageAccessGranted: Boolean,
    onGrantUsageAccess: () -> Unit,
    onSetApps: (List<String>) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val names = remember(installedApps) { installedApps.associate { it.packageName to it.name } }
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.productive_title)) },
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
                    stringResource(
                        R.string.productive_intro,
                        ManaRules.PRODUCTIVE_MINUTES_PER_MANA,
                        ManaRules.PRODUCTIVE_DAILY_CAP
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (!usageAccessGranted) {
                    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                        Text(
                            stringResource(R.string.productive_needs_usage),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onGrantUsageAccess) {
                            Text(stringResource(R.string.productive_grant_usage))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else if (productiveApps.isNotEmpty()) {
                    Text(
                        stringResource(R.string.productive_today, minutesToday, manaToday),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            items(productiveApps, key = { it }) { pkg ->
                GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(packageName = pkg, contentDescription = null, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(names[pkg] ?: pkg, style = MaterialTheme.typography.titleSmall)
                        }
                        TextButton(onClick = { onSetApps(productiveApps - pkg) }) {
                            Text(stringResource(R.string.productive_remove))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.productive_choose))
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            installedApps = installedApps,
            title = stringResource(R.string.productive_choose),
            initialSelection = productiveApps,
            onConfirm = { picked ->
                showPicker = false
                onSetApps(picked)
            },
            onDismiss = { showPicker = false }
        )
    }
}
