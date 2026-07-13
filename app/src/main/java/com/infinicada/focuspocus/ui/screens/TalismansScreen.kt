package com.infinicada.focuspocus.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.R
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalismansScreen(
    lastScannedTagId: String?,
    namedTags: List<NamedTag>,
    onSaveTag: (String) -> Unit,
    onDeleteTag: (NamedTag) -> Unit,
    onSaveQrTalisman: (NamedTag) -> Unit = {},
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tagName by remember { mutableStateOf("") }
    var showQrTalisman by remember { mutableStateOf<NamedTag?>(null) }
    var showCreateQrDialog by remember { mutableStateOf(false) }
    var qrTagName by remember { mutableStateOf("") }

    val qrTalisman = showQrTalisman
    if (qrTalisman != null) {
        QrCodeDialog(
            content = "focuspocus://talisman/${qrTalisman.id}",
            title = stringResource(R.string.talismans_qr_title, qrTalisman.name),
            onDismiss = { showQrTalisman = null }
        )
    }

    if (showCreateQrDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateQrDialog = false
                qrTagName = ""
            },
            title = { Text(stringResource(R.string.talismans_create_qr_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.talismans_create_qr_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = qrTagName,
                        onValueChange = { if (it.length <= 100) qrTagName = it },
                        label = { Text(stringResource(R.string.talismans_name_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newTag = NamedTag(UUID.randomUUID().toString(), qrTagName.trim())
                        onSaveQrTalisman(newTag)
                        showCreateQrDialog = false
                        qrTagName = ""
                        showQrTalisman = newTag
                    },
                    enabled = qrTagName.isNotBlank()
                ) {
                    Text(stringResource(R.string.talismans_enchant))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showCreateQrDialog = false
                    qrTagName = ""
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.talismans_title)) },
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
            // NFC Scan Section
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.talismans_bind_new), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        lastScannedTagId?.let {
                            Text(stringResource(R.string.talismans_last_scanned, it))
                            Spacer(modifier = Modifier.height(8.dp))
                            TextField(
                                value = tagName,
                                onValueChange = { if (it.length <= 100) tagName = it },
                                label = { Text(stringResource(R.string.talismans_name_label)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    onSaveTag(tagName.trim())
                                    tagName = ""
                                },
                                enabled = tagName.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.talismans_enchant))
                            }
                        } ?: Text(
                            stringResource(R.string.talismans_scan_nfc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Create QR Talisman
            item {
                OutlinedButton(
                    onClick = { showCreateQrDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.talismans_create_qr))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Enchanted Items Header
            if (namedTags.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.talismans_enchanted_items), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            items(namedTags) { tag ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tag.name, style = MaterialTheme.typography.titleMedium)
                        }
                        OutlinedButton(
                            onClick = { showQrTalisman = tag },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(stringResource(R.string.action_qr))
                        }
                        Button(
                            onClick = { onDeleteTag(tag) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.talismans_disenchant))
                        }
                    }
                }
            }

            if (namedTags.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.talismans_none_bound),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
