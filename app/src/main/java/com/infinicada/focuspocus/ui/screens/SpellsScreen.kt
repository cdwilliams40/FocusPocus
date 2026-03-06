package com.infinicada.focuspocus.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.infinicada.focuspocus.AppInfo
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.BlockerMode
import com.infinicada.focuspocus.R

@Composable
fun BlockerListScreen(
    blockerLists: List<Blocker>,
    activeBlockerName: String?,
    onBlockerClick: (Blocker) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateClick) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.spells_create_content_desc))
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            item {
                Text(stringResource(R.string.spells_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.spells_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (blockerLists.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.spells_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            items(blockerLists) { blocker ->
                val isActive = blocker.name == activeBlockerName
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .then(
                            if (isActive) Modifier else Modifier.clickable { onBlockerClick(blocker) }
                        ),
                    colors = if (isActive) {
                        CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    } else {
                        CardDefaults.elevatedCardColors()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(blocker.name, style = MaterialTheme.typography.titleMedium)
                                if (isActive) {
                                    Text(
                                        stringResource(R.string.label_active),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            val modeLabel = if (blocker.mode == BlockerMode.BLACKLIST) stringResource(R.string.label_banish) else stringResource(R.string.label_shield)
                            val appCount = blocker.apps.size
                            val siteCount = blocker.websites.orEmpty().size
                            val appCountStr = if (appCount > 0) pluralStringResource(R.plurals.spellbook_app_count, appCount, appCount) else ""
                            val siteCountStr = if (siteCount > 0) pluralStringResource(R.plurals.spellbook_site_count, siteCount, siteCount) else ""
                            Text(
                                buildString {
                                    append(modeLabel)
                                    if (appCount > 0 || siteCount > 0) {
                                        append(" - ")
                                        val parts = mutableListOf<String>()
                                        if (appCount > 0) parts.add(appCountStr)
                                        if (siteCount > 0) parts.add(siteCountStr)
                                        append(parts.joinToString(", "))
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (isActive) {
                                Text(
                                    stringResource(R.string.spells_cannot_edit_active),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
}

@Composable
fun CreateBlockerScreen(
    onSaveBlocker: (Blocker) -> Unit,
    installedApps: List<AppInfo>,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(BlockerMode.BLACKLIST) }
    var apps by remember { mutableStateOf(emptyList<String>()) }
    var websites by remember { mutableStateOf(emptyList<String>()) }
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AppSelectionDialog(
            installedApps = installedApps,
            selectedApps = apps,
            onSave = { newApps ->
                apps = newApps
                showDialog = false
            },
            onDismissRequest = { showDialog = false }
        )
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.spells_create_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = name,
            onValueChange = { if (it.length <= 100) name = it },
            label = { Text(stringResource(R.string.spells_enchantment_name_label)) },
            supportingText = {
                Text(
                    text = "${name.length}/100",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selectedMode == BlockerMode.BLACKLIST,
                onClick = { selectedMode = BlockerMode.BLACKLIST }
            )
            Text(stringResource(R.string.label_banish_blacklist))
            RadioButton(
                selected = selectedMode == BlockerMode.WHITELIST,
                onClick = { selectedMode = BlockerMode.WHITELIST }
            )
            Text(stringResource(R.string.label_shield_whitelist))
        }

        Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.spells_select_target_apps))
        }

        AppListColumn(
            apps = apps,
            installedApps = installedApps,
            onRemoveApp = { apps = apps - it },
            modifier = Modifier
                .padding(top = 16.dp)
                .weight(1f)
        )

        WebsiteListSection(
            websites = websites,
            onWebsitesChanged = { websites = it }
        )

        Button(
            onClick = { onSaveBlocker(Blocker(name.trim(), selectedMode, apps.toSet(), websites)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            enabled = name.isNotBlank()
        ) {
            Text(stringResource(R.string.spells_save_enchantment))
        }
    }
}

@Composable
fun EditBlockerScreen(
    blocker: Blocker,
    onSaveBlocker: (Blocker) -> Unit,
    onDeleteBlocker: (Blocker) -> Unit,
    installedApps: List<AppInfo>,
    modifier: Modifier = Modifier
) {
    var selectedMode by remember { mutableStateOf(blocker.mode) }
    var apps by remember { mutableStateOf(blocker.apps.toList()) }
    var websites by remember { mutableStateOf(blocker.websites.orEmpty()) }
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AppSelectionDialog(
            installedApps = installedApps,
            selectedApps = apps,
            onSave = { newApps ->
                apps = newApps
                showDialog = false
            },
            onDismissRequest = { showDialog = false }
        )
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.spells_edit_title, blocker.name), style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selectedMode == BlockerMode.BLACKLIST,
                onClick = { selectedMode = BlockerMode.BLACKLIST }
            )
            Text(stringResource(R.string.label_banish_blacklist))
            RadioButton(
                selected = selectedMode == BlockerMode.WHITELIST,
                onClick = { selectedMode = BlockerMode.WHITELIST }
            )
            Text(stringResource(R.string.label_shield_whitelist))
        }

        Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.spells_select_target_apps))
        }

        AppListColumn(
            apps = apps,
            installedApps = installedApps,
            onRemoveApp = { apps = apps - it },
            modifier = Modifier
                .padding(top = 16.dp)
                .weight(1f)
        )

        WebsiteListSection(
            websites = websites,
            onWebsitesChanged = { websites = it }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Button(
                onClick = { onSaveBlocker(blocker.copy(mode = selectedMode, apps = apps.toSet(), websites = websites)) },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(stringResource(R.string.action_save))
            }
            Button(
                onClick = { onDeleteBlocker(blocker) },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.action_delete))
            }
        }
    }
}

@Composable
fun WebsiteListSection(
    websites: List<String>,
    onWebsitesChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }

    Column(modifier = modifier.padding(top = 16.dp)) {
        Text(stringResource(R.string.spells_blocked_websites), style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 255) input = it },
                label = { Text(stringResource(R.string.spells_website_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val domain = cleanDomain(input)
                    if (domain.isNotEmpty() && isValidDomain(domain) && domain !in websites) {
                        onWebsitesChanged(websites + domain)
                    }
                    input = ""
                },
                enabled = input.isNotBlank(),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(stringResource(R.string.action_add))
            }
        }
        websites.forEach { site ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(site, modifier = Modifier.weight(1f))
                IconButton(onClick = { onWebsitesChanged(websites - site) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.spells_remove_site, site))
                }
            }
        }
    }
}

private fun cleanDomain(input: String): String {
    var domain = input.trim().lowercase()
    if (domain.startsWith("https://")) domain = domain.removePrefix("https://")
    if (domain.startsWith("http://")) domain = domain.removePrefix("http://")
    if (domain.startsWith("www.")) domain = domain.removePrefix("www.")
    domain = domain.split('/')[0]
    domain = domain.split('?')[0]
    domain = domain.split('#')[0]
    domain = domain.split(':')[0] // strip port
    return domain
}

private val domainPattern = Regex("^[a-z0-9]([a-z0-9\\-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9\\-]{0,61}[a-z0-9])?)*$")

private fun isValidDomain(domain: String): Boolean {
    return domain.length <= 255 && domain.contains('.') && domainPattern.matches(domain)
}

/**
 * Shared composable for displaying a list of selected apps with remove buttons.
 * Used by both CreateBlockerScreen and EditBlockerScreen.
 */
@Composable
fun AppListColumn(
    apps: List<String>,
    installedApps: List<AppInfo>,
    onRemoveApp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(apps) { appPackageName ->
            val appInfo = installedApps.find { it.packageName == appPackageName }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (appInfo != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIcon(
                            packageName = appInfo.packageName,
                            contentDescription = appInfo.name,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(appInfo.name, modifier = Modifier.padding(start = 16.dp))
                    }
                } else {
                    Text(appPackageName, modifier = Modifier.weight(1f))
                }
                Button(onClick = { onRemoveApp(appPackageName) }) {
                    Text(stringResource(R.string.action_remove))
                }
            }
        }
    }
}

@Composable
fun AppSelectionDialog(
    installedApps: List<AppInfo>,
    selectedApps: List<String>,
    onSave: (List<String>) -> Unit,
    onDismissRequest: () -> Unit
) {
    var currentSelections by remember { mutableStateOf(selectedApps.toSet()) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.spells_select_apps_title)) },
        text = {
            LazyColumn {
                items(installedApps) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentSelections = if (currentSelections.contains(app.packageName)) {
                                    currentSelections - app.packageName
                                } else {
                                    currentSelections + app.packageName
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            packageName = app.packageName,
                            contentDescription = app.name,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = app.name,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp)
                        )
                        Checkbox(
                            checked = currentSelections.contains(app.packageName),
                            onCheckedChange = { isChecked ->
                                currentSelections = if (isChecked) {
                                    currentSelections + app.packageName
                                } else {
                                    currentSelections - app.packageName
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(currentSelections.toList()) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Button(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun BlockerSelectionDialog(
    blockerLists: List<Blocker>,
    onBlockerSelected: (Blocker) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.spells_select_enchantment_title)) },
        text = {
            LazyColumn {
                items(blockerLists) { blocker ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBlockerSelected(blocker) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Text(
                            text = blocker.name,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onDismissRequest() }) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun AppIcon(packageName: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
    }
    icon?.let {
        Image(
            bitmap = it.toBitmap().asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier
        )
    }
}
