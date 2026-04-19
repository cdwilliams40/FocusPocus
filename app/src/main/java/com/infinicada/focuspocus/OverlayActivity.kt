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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.limit.FrictionLevel
import com.infinicada.focuspocus.ui.theme.FocusPocusTheme
import com.infinicada.focuspocus.ui.theme.MysticalPurpleDark
import com.infinicada.focuspocus.ui.theme.ThemeMode
import kotlinx.coroutines.delay

class OverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Disable back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing - back button is disabled
            }
        })

        renderOverlay(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        renderOverlay(intent)
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing && !isChangingConfigurations) {
            finishAndRemoveTask()
        }
    }

    private fun closeAndGoHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finishAndRemoveTask()
    }

    private fun renderOverlay(intent: Intent) {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val themeMode = try {
            ThemeMode.valueOf(prefs.getString(Constants.PrefsKeys.THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }

        val appName = intent.getStringExtra("appName")?.take(200) ?: "App"
        val spellName = intent.getStringExtra("spellName")?.take(200)
        val frictionLevelOrdinal = intent.getIntExtra("frictionLevel", -1)
        val cooldownExpiryMillis = intent.getLongExtra("cooldownExpiryMillis", 0L)

        val frictionLevel: FrictionLevel? = if (frictionLevelOrdinal in FrictionLevel.entries.indices) {
            FrictionLevel.entries[frictionLevelOrdinal]
        } else null

        setContent {
            FocusPocusTheme(themeMode = themeMode) {
                OverlayScreen(
                    appName = appName,
                    spellName = spellName,
                    frictionLevel = frictionLevel,
                    cooldownExpiryMillis = cooldownExpiryMillis,
                    onClose = { closeAndGoHome() }
                )
            }
        }
    }
}

/** The phrase a Level-3 user must type exactly (case-insensitive) to dismiss the overlay. */
private const val REQUIRED_PHRASE = "I will stop scrolling"

@Composable
fun OverlayScreen(
    appName: String,
    spellName: String?,
    frictionLevel: FrictionLevel?,
    cooldownExpiryMillis: Long,
    onClose: () -> Unit
) {
    val isCooldownOverlay = frictionLevel != null

    // Countdown seconds driven by friction level (or legacy 3-second default)
    val delaySeconds = frictionLevel?.countdownSeconds ?: 3

    var remainingSeconds by remember { mutableIntStateOf(delaySeconds) }
    var countdownDone by remember { mutableStateOf(false) }
    var phraseInput by remember { mutableStateOf("") }

    // Live cooldown timer (minutes remaining in the cooldown block)
    var cooldownMinutesLeft by remember {
        mutableIntStateOf(
            if (cooldownExpiryMillis > 0) {
                maxOf(0, ((cooldownExpiryMillis - System.currentTimeMillis()) / 1000 / 60).toInt() + 1)
            } else 0
        )
    }

    // Countdown until the close button becomes active
    LaunchedEffect(delaySeconds) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--
        }
        countdownDone = true
    }

    // Tick the "cooldown remaining" display every 30 seconds
    LaunchedEffect(cooldownExpiryMillis) {
        if (cooldownExpiryMillis <= 0L) return@LaunchedEffect
        while (true) {
            delay(30_000L)
            cooldownMinutesLeft = maxOf(0, ((cooldownExpiryMillis - System.currentTimeMillis()) / 1000 / 60).toInt() + 1)
        }
    }

    val phraseMatches = phraseInput.trim().equals(REQUIRED_PHRASE, ignoreCase = true)
    val isCloseEnabled = countdownDone && (frictionLevel?.requiresPhrase != true || phraseMatches)

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

                // Show cooldown remaining time for cooldown overlays
                if (isCooldownOverlay && cooldownMinutesLeft > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.overlay_cooldown_remaining, cooldownMinutesLeft),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Level 3: phrase input field (shown once countdown finishes)
                if (frictionLevel?.requiresPhrase == true) {
                    if (countdownDone) {
                        Text(
                            text = stringResource(R.string.overlay_level3_prompt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\"$REQUIRED_PHRASE\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = phraseInput,
                            onValueChange = { phraseInput = it },
                            label = { Text(stringResource(R.string.overlay_level3_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (phraseMatches) onClose()
                            })
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                Button(
                    onClick = { if (isCloseEnabled) onClose() },
                    enabled = isCloseEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = if (countdownDone) {
                            stringResource(R.string.overlay_close)
                        } else {
                            stringResource(R.string.overlay_wait, remainingSeconds)
                        }
                    )
                }
            }
        }
    }
}
