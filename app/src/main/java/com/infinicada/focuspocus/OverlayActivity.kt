package com.infinicada.focuspocus

import android.content.Intent
import android.content.SharedPreferences
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Toast
import com.infinicada.focuspocus.handler.NfcForegroundHelper
import com.infinicada.focuspocus.handler.NfcResult
import com.infinicada.focuspocus.handler.NfcTapDebouncer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import com.infinicada.focuspocus.limit.ManaWell
import com.infinicada.focuspocus.limit.PactManager
import com.infinicada.focuspocus.limit.RefillPauseReason
import com.infinicada.focuspocus.limit.WellApps
import com.infinicada.focuspocus.limit.WellMath
import com.infinicada.focuspocus.limit.WellSnapshot
import com.infinicada.focuspocus.model.PactIntention
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.infinicada.focuspocus.ui.components.ArcaneBackground
import com.infinicada.focuspocus.ui.components.GlassCard
import com.infinicada.focuspocus.ui.formatDuration
import com.infinicada.focuspocus.ui.theme.FocusPocusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private val nfcDebouncer = NfcTapDebouncer()
    private var isPactOverlay: Boolean = false

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
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // Disable back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing - back button is disabled
            }
        })

        // A rotation re-creates the activity for the same block: not a new one.
        renderOverlay(intent, newBlock = savedInstanceState == null)
    }

    override fun onResume() {
        super.onResume()
        // Foreground-only NFC reader mode so tapping a talisman while looking at
        // a block screen can dispel/toggle the session immediately without leaving the overlay.
        NfcForegroundHelper.enableReaderMode(this, nfcAdapter, this)
    }

    override fun onPause() {
        super.onPause()
        NfcForegroundHelper.disableReaderMode(this, nfcAdapter)
    }

    override fun onTagDiscovered(tag: Tag?) {
        val tagIdBytes = tag?.id ?: return
        if (tagIdBytes.isEmpty()) return
        val tagId = tagIdBytes.joinToString("") { "%02x".format(it) }
        if (!nfcDebouncer.shouldAccept(tagId, System.currentTimeMillis())) return

        runOnUiThread {
            val container = (application as FocusPocusApplication).container
            val result = NfcForegroundHelper.handleTag(
                activity = this,
                container = container,
                tagId = tagId,
                emitForegroundSummary = false
            )
            // If this was a focus-session block and the talisman just ended the session, dismiss the overlay.
            val endedSession = result is NfcResult.DispelSchedule ||
                result is NfcResult.DispelSession ||
                result is NfcResult.StopTalismanSession
            if (endedSession && !isPactOverlay) {
                finishAndRemoveTask()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        blocksShown++
        renderOverlay(intent, newBlock = true)
    }

    /**
     * Blocks this instance has been reused for (the activity is singleTask).
     * Keys the composition, so the next block starts its own pause instead of
     * inheriting a countdown the previous one already finished.
     */
    private var blocksShown = 0

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

    /**
     * [newBlock] is false when the same block is only being drawn again (a
     * rotation, the offer after a pour), so it isn't counted twice.
     */
    private fun renderOverlay(intent: Intent, newBlock: Boolean) {
        // Each rendered overlay gets a fresh grant budget: the activity is
        // singleTask, so onNewIntent re-renders this same instance for the next
        // blocked app, and a leftover flag would leave its buttons dead.
        granting = false
        val themeMode = (application as FocusPocusApplication).container.settings.getThemeMode()

        val appName = intent.getStringExtra("appName")?.take(200) ?: "App"
        val spellName = intent.getStringExtra("spellName")?.take(200)
        val alternativePackage = intent.getStringExtra("pactAlternativePackage")
        val alternativeName = intent.getStringExtra("pactAlternativeName")?.take(200)
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val progressionEnabled = Progression.isEnabled(prefs)
        val substituteMana = Progression.nextSubstituteWinMana(prefs)

        // A Well app: instead of a plain block, offer a pact paid from the
        // Mana Well, limited to what the Well holds right now. Read here
        // rather than passed in, so the overlay and the draw it leads to see
        // the same store. (A few primitive prefs — fine on the main thread.)
        val pactPackageName = intent.getStringExtra("pactPackageName")
        isPactOverlay = pactPackageName != null
        if (pactPackageName != null) {
            val now = System.currentTimeMillis()
            val well = ManaWell.snapshot(prefs, now)
            val wellMinutes = well.availableMinutesAt(now)
            val todayOpens = intent.getIntExtra("pactTodayOpens", 0)
            val choices = WellMath.limitChoices(WellApps.PACT_CHOICES, wellMinutes)
            // Too dry for a pact: note it, and offer a pour when mana can buy
            // one and the Well has room for it.
            val pourCost: Long? = if (choices.isEmpty()) {
                if (newBlock) ManaWell.recordDry(prefs)
                val cost = Progression.wellChannelCost(prefs)
                val hasRoom = well.levelMillisAt(now) + ManaRules.POUR_MINUTES * WellMath.MINUTE_MS <=
                    well.capacityMinutes * WellMath.MINUTE_MS
                cost.takeIf {
                    progressionEnabled && hasRoom &&
                        prefs.getLong(Constants.PrefsKeys.MANA_BALANCE, 0L) >= it
                }
            } else null
            setContent {
                FocusPocusTheme(themeMode = themeMode) { key(blocksShown) {
                    if (choices.isEmpty()) {
                        WellDryScreen(
                            appName = appName,
                            well = well,
                            neededMinutes = WellApps.PACT_CHOICES.min(),
                            nowMillis = now,
                            alternativeName = if (alternativePackage != null) alternativeName else null,
                            substituteMana = substituteMana,
                            onOpenAlternative = {
                                alternativePackage?.let { openSubstituteWithReward(it) }
                            },
                            pourCost = pourCost,
                            onPour = {
                                if (Progression.redeemPerk(prefs, com.infinicada.focuspocus.model.Perk.WELL_CHANNEL)) {
                                    // Show the pact offer with the minutes just poured.
                                    renderOverlay(intent, newBlock = false)
                                } else {
                                    Toast.makeText(this, R.string.well_channel_failed, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDecline = { closeAndGoHome() }
                        )
                    } else {
                        PactOfferScreen(
                            appName = appName,
                            choicesMinutes = choices,
                            todayOpens = todayOpens,
                            alternativeName = if (alternativePackage != null) alternativeName else null,
                            substituteMana = substituteMana,
                            wellMinutes = wellMinutes,
                            wellCapacityMinutes = well.capacityMinutes,
                            onOpenAlternative = {
                                alternativePackage?.let { openSubstituteWithReward(it) }
                            },
                            onPactChosen = { minutes, intention ->
                                grantPactAndLaunch(pactPackageName, minutes, intention)
                            },
                            onDecline = { closeAndGoHome() }
                        )
                    }
                } }
            }
            return
        }

        setContent {
            FocusPocusTheme(themeMode = themeMode) {
                key(blocksShown) {
                    OverlayScreen(
                        appName = appName,
                        spellName = spellName,
                        alternativeName = if (alternativePackage != null) alternativeName else null,
                        substituteMana = substituteMana,
                        onOpenAlternative = {
                            alternativePackage?.let { openSubstituteWithReward(it) }
                        },
                        onClose = { closeAndGoHome() }
                    )
                }
            }
        }
    }

    /**
     * One grant per overlay. The work below leaves the main thread, so without
     * this a second tap during the hop could grant twice.
     */
    private var granting = false

    private fun openSubstituteWithReward(substitutePackage: String) {
        if (granting) return
        granting = true
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val award = Progression.awardSubstituteWin(prefs, substitutePackage)
        ProgressionNotifier.postTrialCompletions(this, prefs, award.completedTrials)
        val message = if (award.manaEarned > 0L) {
            getString(R.string.substitute_win_toast_mana, award.manaEarned)
        } else {
            getString(R.string.substitute_win_toast)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        launchApp(substitutePackage)
    }

    private fun grantPactAndLaunch(
        packageName: String,
        minutes: Int,
        intention: PactIntention? = null
    ) =
        grantAndLaunch(packageName) { prefs, now ->
            if (!ManaWell.tryDraw(prefs, minutes, now, packageName = packageName, intention = intention)) {
                return@grantAndLaunch false
            }
            PactManager(prefs).grantAllowance(packageName, minutes, now)
            true
        }

    private fun grantAndLaunch(
        packageName: String,
        grant: (SharedPreferences, Long) -> Boolean
    ) {
        if (granting) return
        granting = true
        lifecycleScope.launch {
            val granted = withContext(Dispatchers.Default) {
                val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                val granted = grant(prefs, System.currentTimeMillis())
                if (granted) {
                    DeviceOwnerManager.syncSuspensions(this@OverlayActivity)
                    PactNotifier.update(this@OverlayActivity)
                    RitualAlarmScheduler.scheduleNext(this@OverlayActivity)
                }
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

@Composable
fun OverlayScreen(
    appName: String,
    spellName: String?,
    onClose: () -> Unit,
    alternativeName: String? = null,
    /** What picking the substitute pays now; 0 hides the mana from its label. */
    substituteMana: Long = 0L,
    onOpenAlternative: () -> Unit = {}
) {
    val delaySeconds = 3

    // The overlay deliberately survives rotation (see onStop), so the wait
    // timer must survive it too.
    var remainingSeconds by rememberSaveable { mutableIntStateOf(delaySeconds) }
    var countdownDone by rememberSaveable { mutableStateOf(false) }

    // Countdown until the close button becomes active
    LaunchedEffect(delaySeconds) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--
        }
        countdownDone = true
    }

    val isCloseEnabled = countdownDone

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

                if (alternativeName != null) {
                    Button(
                        onClick = onOpenAlternative,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (substituteMana > 0L) {
                                stringResource(
                                    R.string.overlay_pact_alternative_mana,
                                    alternativeName,
                                    substituteMana
                                )
                            } else {
                                stringResource(R.string.overlay_pact_alternative, alternativeName)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Button(
                    onClick = { if (isCloseEnabled) onClose() },
                    enabled = isCloseEnabled
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
 * Pact offer: a Well app is blocked by default; the user consciously chooses how
 * long to open it, paid from the Mana Well, or walks away. The choice buttons stay disabled for a few seconds so
 * muscle memory can't grant a pact before the urge has a chance to pass.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PactOfferScreen(
    appName: String,
    choicesMinutes: List<Int>,
    todayOpens: Int = 0,
    alternativeName: String? = null,
    /** What picking the substitute pays now; 0 hides the mana from its label. */
    substituteMana: Long = 0L,
    /** Minutes in the Mana Well right now. */
    wellMinutes: Int,
    wellCapacityMinutes: Int,
    onOpenAlternative: () -> Unit = {},
    onPactChosen: (Int, PactIntention?) -> Unit,
    onDecline: () -> Unit
) {
    val delaySeconds = 3
    var remainingSeconds by rememberSaveable { mutableIntStateOf(delaySeconds) }
    var countdownDone by rememberSaveable { mutableStateOf(false) }
    var selectedIntention by rememberSaveable { mutableStateOf<PactIntention?>(null) }

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

                    // Awareness counter: how many times this app has been opened
                    // today. Hidden on the first open — there's no pattern yet.
                    if (todayOpens > 1) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.overlay_pact_stats, todayOpens),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.overlay_pact_well_desc, appName),
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
                            Text(
                                if (substituteMana > 0L) {
                                    stringResource(
                                        R.string.overlay_pact_alternative_mana,
                                        alternativeName,
                                        substituteMana
                                    )
                                } else {
                                    stringResource(R.string.overlay_pact_alternative, alternativeName)
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // The price, right where it is paid: these minutes come
                    // out of the pool every Well app shares.
                    WellStatusLine(wellMinutes, wellCapacityMinutes)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Optional 1-tap intention chips usable right during the 3s pause.
                    Text(
                        text = stringResource(R.string.overlay_pact_intention_prompt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PactIntention.entries.forEach { intention ->
                            FilterChip(
                                selected = selectedIntention == intention,
                                onClick = {
                                    selectedIntention = if (selectedIntention == intention) null else intention
                                },
                                label = {
                                    Text(
                                        text = when (intention) {
                                            PactIntention.QUICK_REPLY -> stringResource(R.string.pact_intention_quick_reply)
                                            PactIntention.SPECIFIC_TASK -> stringResource(R.string.pact_intention_specific_task)
                                            PactIntention.UNWINDING -> stringResource(R.string.pact_intention_unwinding)
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

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
                            onClick = { onPactChosen(minutes, selectedIntention) },
                            enabled = countdownDone,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                androidx.compose.ui.res.pluralStringResource(
                                    R.plurals.overlay_pact_choice_minutes, minutes, minutes
                                )
                            )
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

/** "Mana Well: 18 of 30 min" — what a pact is about to draw from. */
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
    /** What picking the substitute pays now; 0 hides the mana from its label. */
    substituteMana: Long = 0L,
    onOpenAlternative: () -> Unit = {},
    /** Today's pour price when a pour is possible and affordable; null otherwise. */
    pourCost: Long? = null,
    onPour: () -> Unit = {},
    onDecline: () -> Unit
) {
    val waitMillis = well.millisUntilMinutes(neededMinutes, nowMillis)
    val pauseReason = well.refillPauseReasonAt(nowMillis)

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
                        text = when {
                            pauseReason == RefillPauseReason.MORNING_FOCUS ->
                                stringResource(R.string.well_paused_morning_focus, well.refillGate.morningFocusMinutes)
                            pauseReason == RefillPauseReason.QUIET_HOURS ->
                                stringResource(R.string.well_paused_quiet_hours)
                            waitMillis != null -> stringResource(
                                R.string.overlay_well_dry_eta,
                                neededMinutes,
                                formatDuration(WellMath.wholeMinutesCeil(waitMillis))
                            )
                            else -> stringResource(R.string.overlay_well_dry_no_refill)
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
                            Text(
                                if (substituteMana > 0L) {
                                    stringResource(
                                        R.string.overlay_pact_alternative_mana,
                                        alternativeName,
                                        substituteMana
                                    )
                                } else {
                                    stringResource(R.string.overlay_pact_alternative, alternativeName)
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (pourCost != null) {
                        OutlinedButton(
                            onClick = onPour,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.well_dry_pour, ManaRules.POUR_MINUTES, pourCost))
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
