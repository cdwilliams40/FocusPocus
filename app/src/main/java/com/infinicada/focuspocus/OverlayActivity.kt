package com.infinicada.focuspocus

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import com.google.gson.Gson
import com.infinicada.focuspocus.limit.GuardLiveState
import com.infinicada.focuspocus.limit.GuardStatus
import com.infinicada.focuspocus.limit.ManaWell
import com.infinicada.focuspocus.limit.PactManager
import com.infinicada.focuspocus.limit.SessionCooldownManager
import com.infinicada.focuspocus.limit.WellMath
import com.infinicada.focuspocus.limit.WellSnapshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.infinicada.focuspocus.limit.FrictionLevel
import com.infinicada.focuspocus.ui.components.ArcaneBackground
import com.infinicada.focuspocus.ui.components.GlassCard
import com.infinicada.focuspocus.ui.formatDuration
import com.infinicada.focuspocus.ui.theme.FocusPocusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayActivity : ComponentActivity() {

    companion object {
        /**
         * True while this block screen is the activity in front. The accessibility
         * service reads it to tell a real return to a just-blocked app from the
         * window event that app can still send while it is being closed: with
         * this screen on top, the app cannot be the one in the foreground.
         */
        @Volatile
        var isOnTop: Boolean = false
            private set
    }

    // The system reports the loss before onPause, so this cannot stay set once
    // another activity has come in front.
    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        isOnTop = isTopResumedActivity
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
        // If the user navigates away without pressing Close (e.g. Home button),
        // finish the activity so it doesn't linger in the background. A stale
        // overlay task can be brought back to the foreground later, making it
        // look like the blocked app is being re-blocked.
        // Skip during configuration changes (e.g. rotation) to prevent the
        // overlay from being dismissed, which would let the user bypass the block.
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
        // Each rendered overlay gets a fresh grant budget: the activity is
        // singleTask, so onNewIntent re-renders this same instance for the next
        // blocked app, and a leftover flag would leave its buttons dead.
        granting = false
        val themeMode = (application as FocusPocusApplication).container.settings.getThemeMode()

        val appName = intent.getStringExtra("appName")?.take(200) ?: "App"
        val spellName = intent.getStringExtra("spellName")?.take(200)
        val frictionLevelOrdinal = intent.getIntExtra("frictionLevel", -1)
        val cooldownExpiryMillis = intent.getLongExtra("cooldownExpiryMillis", 0L)

        // Pact Mode: instead of a plain block, offer an allowance of the user's choosing.
        val pactPackageName = intent.getStringExtra("pactPackageName")
        val pactChoices = intent.getIntArrayExtra("pactChoices")
        val pactSealMinutes = intent.getIntExtra("pactSealMinutes", 30)

        // Group Seal mode: opening this app opens every pact-gated app for one
        // shared window instead of just this one.
        val groupSealMode = intent.getBooleanExtra("groupSealMode", false)
        val groupOpenMinutes = intent.getIntExtra("groupOpenMinutes", 0)

        // Mana Well: a Well-bound pact is paid from the shared pool, so what
        // it can offer is limited to what the pool holds right now. Read here
        // rather than passed in, so the overlay and the draw it leads to see
        // the same store. (A few primitive prefs — fine on the main thread.)
        val wellBound = intent.getBooleanExtra("wellBound", false)
        val now = System.currentTimeMillis()
        val well = if (wellBound) {
            ManaWell.snapshot(getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE), now)
        } else null
        val wellMinutes = well?.availableMinutesAt(now)

        if (groupSealMode && pactPackageName != null && groupOpenMinutes > 0) {
            val windowMinutes = wellMinutes?.let { minOf(groupOpenMinutes, it) } ?: groupOpenMinutes
            setContent {
                FocusPocusTheme(themeMode = themeMode) {
                    if (well != null && windowMinutes <= 0) {
                        WellDryScreen(
                            appName = appName,
                            well = well,
                            neededMinutes = 1,
                            nowMillis = now,
                            onDecline = { closeAndGoHome() }
                        )
                    } else {
                        GroupPactOfferScreen(
                            appName = appName,
                            openWindowMinutes = windowMinutes,
                            sealMinutes = pactSealMinutes,
                            wellMinutes = wellMinutes,
                            wellCapacityMinutes = well?.capacityMinutes ?: 0,
                            onGroupOpenChosen = {
                                grantGroupAndLaunch(pactPackageName, windowMinutes, wellBound)
                            },
                            onDecline = { closeAndGoHome() }
                        )
                    }
                }
            }
            return
        }

        if (pactPackageName != null && pactChoices != null && pactChoices.isNotEmpty()) {
            val todayOpens = intent.getIntExtra("pactTodayOpens", 0)
            val todayReflexOpens = intent.getIntExtra("pactTodayReflexOpens", 0)
            val alternativePackage = intent.getStringExtra("pactAlternativePackage")
            val alternativeName = intent.getStringExtra("pactAlternativeName")?.take(200)
            val choices = wellMinutes?.let { WellMath.limitChoices(pactChoices.toList(), it) }
                ?: pactChoices.toList()
            setContent {
                FocusPocusTheme(themeMode = themeMode) {
                    if (well != null && choices.isEmpty()) {
                        WellDryScreen(
                            appName = appName,
                            well = well,
                            neededMinutes = pactChoices.min(),
                            nowMillis = now,
                            alternativeName = if (alternativePackage != null) alternativeName else null,
                            onOpenAlternative = { alternativePackage?.let { launchApp(it) } },
                            onDecline = { closeAndGoHome() }
                        )
                    } else {
                        PactOfferScreen(
                            appName = appName,
                            choicesMinutes = choices,
                            sealMinutes = pactSealMinutes,
                            todayOpens = todayOpens,
                            todayReflexOpens = todayReflexOpens,
                            alternativeName = if (alternativePackage != null) alternativeName else null,
                            wellMinutes = wellMinutes,
                            wellCapacityMinutes = well?.capacityMinutes ?: 0,
                            onOpenAlternative = { alternativePackage?.let { launchApp(it) } },
                            onPactChosen = { minutes ->
                                grantPactAndLaunch(pactPackageName, minutes, wellBound)
                            },
                            onDecline = { closeAndGoHome() }
                        )
                    }
                }
            }
            return
        }

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

    /**
     * One grant per overlay. The work below leaves the main thread, so without
     * this a second tap during the hop could grant twice.
     */
    private var granting = false

    /**
     * Grants the chosen allowance and opens the app. The store writes and the
     * Warden sync run off the main thread: on a provisioned device the sync
     * enumerates launchable packages and queries usage stats, and this is the
     * overlay the user meets on every pact app's open.
     */
    private fun grantPactAndLaunch(packageName: String, minutes: Int, wellBound: Boolean) =
        grantAndLaunch(packageName) { prefs, gson, now ->
            // Draw first: an allowance the Well couldn't pay for is never granted.
            if (wellBound && !ManaWell.tryDraw(prefs, minutes, now)) return@grantAndLaunch false
            PactManager(prefs, gson).grantAllowance(packageName, minutes, now)
            true
        }

    /**
     * Group Seal mode's grant: opens every pact-gated app that isn't currently
     * sealed for the same shared window, then launches only the app that was
     * actually tapped — mirrors SpellbookViewModel.groupOpenPacts' dashboard
     * twin of this action. With the Mana Well on, the shared window is one
     * draw, and the group is the Well-bound apps only.
     */
    private fun grantGroupAndLaunch(packageName: String, minutes: Int, wellBound: Boolean) =
        grantAndLaunch(packageName) { prefs, gson, now ->
            val pactManager = PactManager(prefs, gson)
            val configs = AppTimeLimitManager.getTimeLimitConfigs(prefs, gson)
            val liveStates = SessionCooldownManager(prefs, gson).peekActiveCooldowns(now)
                .mapValues { (_, state) -> GuardLiveState(cooldownExpiryMillis = state.cooldownExpiryMillis) }
            val targets = GuardStatus.groupOpenEligiblePackages(
                configs, pactManager.getGroups(), BlockerRepository.getBlockers(prefs), liveStates, now,
                wellEnabled = wellBound
            )
            // Nothing left to open (the tapped app was sealed meanwhile): spend
            // nothing and fall through to the launch, as before — the gate
            // then answers with the overlay that is actually true.
            if (targets.isEmpty()) return@grantAndLaunch true
            if (wellBound && !ManaWell.tryDraw(prefs, minutes, now)) return@grantAndLaunch false
            // One store write for the shared window, not one per app.
            pactManager.grantAllowances(targets, minutes, now)
            true
        }

    /**
     * Runs [grant] off the main thread, lifts any Warden suspension it just
     * made stale, then opens [packageName] — under Warden greying the app is
     * OS-suspended, and the system refuses to open it until the sync lands.
     *
     * [grant] returns false when it refused — the Mana Well was drained
     * between this overlay appearing and the choice (a second overlay, the
     * dashboard). Nothing was granted, so the app stays shut.
     */
    private fun grantAndLaunch(
        packageName: String,
        grant: (SharedPreferences, Gson, Long) -> Boolean
    ) {
        if (granting) return
        granting = true
        lifecycleScope.launch {
            val granted = withContext(Dispatchers.Default) {
                val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                val granted = grant(prefs, Gson(), System.currentTimeMillis())
                if (granted) DeviceOwnerManager.syncSuspensions(this@OverlayActivity)
                granted
            }
            if (granted) {
                launchApp(packageName)
            } else {
                Toast.makeText(
                    this@OverlayActivity, R.string.well_draw_refused_toast, Toast.LENGTH_LONG
                ).show()
                closeAndGoHome()
            }
        }
    }

    private fun launchApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        }
        finishAndRemoveTask()
    }
}

/**
 * Whole minutes left before [expiryMillis], rounded up; 0 once expired.
 * The old floor-plus-one arithmetic reported "1 min remaining" for up to a
 * minute after the cooldown had already ended.
 */
private fun cooldownMinutesRemaining(expiryMillis: Long): Int {
    if (expiryMillis <= 0L) return 0
    return GuardStatus.minutesUntil(expiryMillis, System.currentTimeMillis())
}

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

    // The overlay deliberately survives rotation (see onStop), so the wait
    // timer and any typed phrase must survive it too.
    var remainingSeconds by rememberSaveable { mutableIntStateOf(delaySeconds) }
    var countdownDone by rememberSaveable { mutableStateOf(false) }
    var phraseInput by rememberSaveable { mutableStateOf("") }

    // Live cooldown timer (minutes remaining in the cooldown block)
    var cooldownMinutesLeft by remember {
        mutableIntStateOf(cooldownMinutesRemaining(cooldownExpiryMillis))
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
            cooldownMinutesLeft = cooldownMinutesRemaining(cooldownExpiryMillis)
        }
    }

    // The phrase a Level-3 user must type exactly (case-insensitive) to
    // dismiss the overlay — localized, so users type it in their own language.
    val requiredPhrase = stringResource(R.string.overlay_level3_phrase)
    val phraseMatches = phraseInput.trim().equals(requiredPhrase, ignoreCase = true)
    val isCloseEnabled = countdownDone && (frictionLevel?.requiresPhrase != true || phraseMatches)

    ArcaneBackground(modifier = Modifier.fillMaxSize(), starCount = 72) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier.padding(28.dp),
                contentPadding = PaddingValues(28.dp)
            ) {
            Column(
                modifier = Modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Glowing sigil at the top of the card
                val sigilColor = MaterialTheme.colorScheme.primary
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(76.dp)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(
                                    sigilColor.copy(alpha = 0.28f),
                                    sigilColor.copy(alpha = 0.06f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isCooldownOverlay) Icons.Filled.HourglassEmpty
                                      else Icons.Filled.AutoFixHigh,
                        contentDescription = null,
                        tint = sigilColor,
                        modifier = Modifier.size(38.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

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
                            text = "\"$requiredPhrase\"",
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
}

/**
 * Pact Mode offer: the app is blocked by default; the user consciously chooses an
 * allowance, or walks away. The choice buttons stay disabled for a few seconds so
 * muscle memory can't grant a pact before the urge has a chance to pass.
 */
@Composable
fun PactOfferScreen(
    appName: String,
    choicesMinutes: List<Int>,
    sealMinutes: Int,
    todayOpens: Int = 0,
    todayReflexOpens: Int = 0,
    alternativeName: String? = null,
    /** Minutes in the Mana Well when this pact draws from it; null when it doesn't. */
    wellMinutes: Int? = null,
    wellCapacityMinutes: Int = 0,
    onOpenAlternative: () -> Unit = {},
    onPactChosen: (Int) -> Unit,
    onDecline: () -> Unit
) {
    val delaySeconds = 3
    var remainingSeconds by rememberSaveable { mutableIntStateOf(delaySeconds) }
    var countdownDone by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--
        }
        countdownDone = true
    }

    ArcaneBackground(modifier = Modifier.fillMaxSize(), starCount = 72) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier.padding(28.dp),
                contentPadding = PaddingValues(28.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val sigilColor = MaterialTheme.colorScheme.primary
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(76.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    listOf(
                                        sigilColor.copy(alpha = 0.28f),
                                        sigilColor.copy(alpha = 0.06f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            tint = sigilColor,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.overlay_pact_prompt, appName),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )

                    // Awareness counter: the shape of today's habit, shown at the
                    // exact moment the urge fires. Hidden on the first open of the
                    // day — there's no pattern to show yet.
                    if (todayOpens > 1) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (todayReflexOpens > 0) {
                                stringResource(
                                    R.string.overlay_pact_stats_reflex,
                                    todayOpens, todayReflexOpens
                                )
                            } else {
                                stringResource(R.string.overlay_pact_stats, todayOpens)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        // A pact paid from the Mana Well ends without a seal
                        // (GuardStatus.lapseSeal), so it mustn't promise one.
                        text = if (wellMinutes != null) {
                            stringResource(R.string.overlay_pact_well_desc, appName)
                        } else {
                            stringResource(R.string.overlay_pact_seal_desc, appName, sealMinutes)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // The healthier substitute is the most prominent choice and the
                    // only one usable immediately — good reflexes shouldn't wait.
                    if (alternativeName != null) {
                        Button(
                            onClick = onOpenAlternative,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.overlay_pact_alternative, alternativeName))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // The price, right where it is paid: these minutes come
                    // out of the pool every pact app shares.
                    if (wellMinutes != null) {
                        WellStatusLine(wellMinutes, wellCapacityMinutes)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (!countdownDone) {
                        Text(
                            text = stringResource(R.string.overlay_pact_wait, remainingSeconds),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    choicesMinutes.forEach { minutes ->
                        OutlinedButton(
                            onClick = { onPactChosen(minutes) },
                            enabled = countdownDone,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.overlay_pact_choice, minutes))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onDecline,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.overlay_pact_decline))
                    }
                }
            }
        }
    }
}

/**
 * Group Seal mode's offer: opening [appName] opens every pact-gated app at
 * once for a shared window, so there's no per-app minute picker — one
 * confirm action, gated by the same anti-reflex pause as [PactOfferScreen].
 */
@Composable
fun GroupPactOfferScreen(
    appName: String,
    openWindowMinutes: Int,
    sealMinutes: Int,
    /** Minutes in the Mana Well when the window draws from it; null when it doesn't. */
    wellMinutes: Int? = null,
    wellCapacityMinutes: Int = 0,
    onGroupOpenChosen: () -> Unit,
    onDecline: () -> Unit
) {
    val delaySeconds = 3
    var remainingSeconds by rememberSaveable { mutableIntStateOf(delaySeconds) }
    var countdownDone by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--
        }
        countdownDone = true
    }

    ArcaneBackground(modifier = Modifier.fillMaxSize(), starCount = 72) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier.padding(28.dp),
                contentPadding = PaddingValues(28.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val sigilColor = MaterialTheme.colorScheme.primary
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(76.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    listOf(
                                        sigilColor.copy(alpha = 0.28f),
                                        sigilColor.copy(alpha = 0.06f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoFixHigh,
                            contentDescription = null,
                            tint = sigilColor,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.overlay_group_pact_prompt, appName, openWindowMinutes),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.overlay_group_pact_seal_desc, sealMinutes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (wellMinutes != null) {
                        WellStatusLine(wellMinutes, wellCapacityMinutes)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (!countdownDone) {
                        Text(
                            text = stringResource(R.string.overlay_pact_wait, remainingSeconds),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    OutlinedButton(
                        onClick = onGroupOpenChosen,
                        enabled = countdownDone,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.overlay_group_pact_choice, openWindowMinutes))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onDecline,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.overlay_pact_decline))
                    }
                }
            }
        }
    }
}

/** "Mana Well: 18 of 30 min" — what a Well-bound pact is about to draw from. */
@Composable
private fun WellStatusLine(wellMinutes: Int, wellCapacityMinutes: Int) {
    Text(
        text = stringResource(R.string.well_level_line, wellMinutes, wellCapacityMinutes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.tertiary,
        textAlign = TextAlign.Center
    )
}

/**
 * Shown instead of a pact offer when the Mana Well can't pay for even the
 * smallest choice. There is nothing to grant, so there is no pause and no
 * friction to sit through: it says when the time will be back, offers the
 * pact's healthier substitute if it has one, and lets the user leave.
 */
@Composable
fun WellDryScreen(
    appName: String,
    well: WellSnapshot,
    /** The smallest allowance this pact would offer, in minutes. */
    neededMinutes: Int,
    nowMillis: Long,
    alternativeName: String? = null,
    onOpenAlternative: () -> Unit = {},
    onDecline: () -> Unit
) {
    val waitMillis = well.millisUntilMinutes(neededMinutes, nowMillis)

    ArcaneBackground(modifier = Modifier.fillMaxSize(), starCount = 72) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier.padding(28.dp),
                contentPadding = PaddingValues(28.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val sigilColor = MaterialTheme.colorScheme.primary
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(76.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    listOf(
                                        sigilColor.copy(alpha = 0.28f),
                                        sigilColor.copy(alpha = 0.06f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.HourglassEmpty,
                            contentDescription = null,
                            tint = sigilColor,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.overlay_well_dry_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.overlay_well_dry_body, appName),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    WellStatusLine(well.availableMinutesAt(nowMillis), well.capacityMinutes)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (waitMillis != null) {
                            stringResource(
                                R.string.overlay_well_dry_eta,
                                neededMinutes,
                                formatDuration(WellMath.wholeMinutesCeil(waitMillis))
                            )
                        } else {
                            stringResource(R.string.overlay_well_dry_no_refill)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (alternativeName != null) {
                        Button(
                            onClick = onOpenAlternative,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.overlay_pact_alternative, alternativeName))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.overlay_close))
                    }
                }
            }
        }
    }
}
