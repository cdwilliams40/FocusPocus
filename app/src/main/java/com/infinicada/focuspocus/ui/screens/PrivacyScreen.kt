package com.infinicada.focuspocus.ui.screens

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.R
import androidx.core.net.toUri
import com.infinicada.focuspocus.ui.components.ArcaneBackground

/**
 * The in-app privacy statement.
 *
 * Play requires a privacy policy to be linked from the store listing; linking it
 * from inside the app as well is what makes it readable by someone deciding
 * whether to grant accessibility or notification access, which is the moment the
 * question actually occurs to them. The hosted document
 * ([PRIVACY_POLICY_URL], `docs/PRIVACY_POLICY.md`) stays the authoritative text
 * — this screen is its plain-language summary, and must not drift from it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onOpenFullPolicy: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    ArcaneBackground(modifier = modifier) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.privacy_title)) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    stringResource(R.string.privacy_headline),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.privacy_headline_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))

                PrivacySection(
                    title = stringResource(R.string.privacy_on_device),
                    body = stringResource(R.string.privacy_on_device_body)
                )
                PrivacySection(
                    title = stringResource(R.string.privacy_leaves),
                    body = stringResource(R.string.privacy_leaves_body)
                )
                PrivacySection(
                    title = stringResource(R.string.privacy_never),
                    body = stringResource(R.string.privacy_never_body)
                )
                PrivacySection(
                    title = stringResource(R.string.privacy_accessibility),
                    body = stringResource(R.string.privacy_accessibility_body)
                )
                PrivacySection(
                    title = stringResource(R.string.privacy_controls),
                    body = stringResource(R.string.privacy_controls_body)
                )

                OutlinedButton(
                    onClick = onOpenFullPolicy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.privacy_read_full))
                }
                Spacer(modifier = Modifier.height(12.dp))
                // Selectable so the address can be copied rather than retyped.
                SelectionContainer {
                    Text(
                        stringResource(R.string.privacy_contact),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PrivacySection(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

/**
 * Opens [url] in whatever the user browses with. Guarded: a device with no
 * browser at all resolves nothing, and an unhandled ActivityNotFoundException
 * here would take down the settings screen over a link.
 */
fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        Log.e("PrivacyScreen", "No handler for $url", e)
    }
}

/**
 * Where the authoritative policy lives. Kept next to the screen that links it so
 * the two are updated together; `docs/PRIVACY_POLICY.md` is the source of truth
 * and the same URL belongs in the Play listing.
 */
const val PRIVACY_POLICY_URL =
    "https://github.com/cdwilliams40/FocusPocus/blob/master/docs/PRIVACY_POLICY.md"
