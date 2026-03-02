package com.infinicada.focuspocus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.FocusPreset
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.PresetAction
import com.infinicada.focuspocus.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSpellsListScreen(
    focusPresets: List<FocusPreset>,
    blockerLists: List<Blocker>,
    namedTags: List<NamedTag>,
    onEditPreset: (FocusPreset) -> Unit,
    onCreatePreset: () -> Unit,
    onDeleteFocusPreset: (FocusPreset) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showQrPreset by remember { mutableStateOf<FocusPreset?>(null) }

    if (showQrPreset != null) {
        QrCodeDialog(
            content = "focuspocus://preset/${showQrPreset!!.id}",
            title = "QR Code: ${showQrPreset!!.name}",
            onDismiss = { showQrPreset = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick Spells") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreatePreset) {
                Icon(Icons.Default.Add, contentDescription = "Create quick spell")
            }
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
                    "Preset focus configurations for quick access",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (focusPresets.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "No quick spells yet. Tap + to create your first one!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            items(focusPresets) { preset ->
                val blocker = blockerLists.find { it.name == preset.blockerName }
                val talisman = namedTags.find { it.id == preset.talismanId }
                val durationText = formatDuration(preset.durationMinutes)
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(preset.name, style = MaterialTheme.typography.titleSmall)
                            val actionLabel = when (preset.action ?: PresetAction.TOGGLE) {
                                PresetAction.TEMP_ENABLE -> " - Temp Enable ${preset.tempDurationMinutes ?: 30}m"
                                PresetAction.TEMP_DISABLE -> " - Temp Disable ${preset.tempDurationMinutes ?: 30}m"
                                else -> ""
                            }
                            Text(
                                "${blocker?.name ?: preset.blockerName} - $durationText${if (preset.breaksEnabled) " - Breaks" else ""}$actionLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (blocker == null) {
                                Text(
                                    "Enchantment missing - please edit",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (talisman != null) {
                                Text(
                                    "Bound to: ${talisman.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                        Row {
                            OutlinedButton(
                                onClick = { showQrPreset = preset },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text("QR")
                            }
                            OutlinedButton(
                                onClick = { onEditPreset(preset) },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text("Edit")
                            }
                            Button(
                                onClick = { onDeleteFocusPreset(preset) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}
