package com.infinicada.focuspocus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.ui.theme.FocusPocusTheme
import com.infinicada.focuspocus.ui.theme.MysticalPurpleDark
import com.infinicada.focuspocus.ui.theme.ThemeMode
import kotlinx.coroutines.delay

class OverlayActivity : ComponentActivity() {

    private val CLOSE_DELAY_SECONDS = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Disable back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing - back button is disabled
            }
        })

        val appName = intent.getStringExtra("appName") ?: "App"
        val spellName = intent.getStringExtra("spellName")

        renderOverlay(appName, spellName)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun closeAndGoHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finishAndRemoveTask()
    }

    private fun renderOverlay(appName: String, spellName: String?) {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val themeMode = try {
            ThemeMode.valueOf(prefs.getString(Constants.PrefsKeys.THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
        setContent {
            FocusPocusTheme(themeMode = themeMode) {
                OverlayScreen(
                    appName = appName,
                    spellName = spellName,
                    delaySeconds = CLOSE_DELAY_SECONDS,
                    onClose = { closeAndGoHome() }
                )
            }
        }
    }
}

@Composable
fun OverlayScreen(
    appName: String,
    spellName: String?,
    delaySeconds: Int,
    onClose: () -> Unit
) {
    var remainingSeconds by remember { mutableIntStateOf(delaySeconds) }
    var isCloseEnabled by remember { mutableStateOf(false) }

    // Countdown timer
    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--
        }
        isCloseEnabled = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MysticalPurpleDark.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier.padding(32.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (spellName != null) {
                    Text(
                        text = stringResource(R.string.overlay_spell_name, spellName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = stringResource(R.string.overlay_app_blocked, appName),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { if (isCloseEnabled) onClose() },
                    enabled = isCloseEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = if (isCloseEnabled) stringResource(R.string.overlay_close) else stringResource(R.string.overlay_wait, remainingSeconds)
                    )
                }
            }
        }
    }
}
