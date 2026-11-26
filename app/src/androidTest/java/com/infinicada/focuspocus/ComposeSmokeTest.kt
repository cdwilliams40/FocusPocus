package com.infinicada.focuspocus

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.infinicada.focuspocus.limit.WellRefillGate
import com.infinicada.focuspocus.limit.WellSnapshot
import com.infinicada.focuspocus.model.PactIntention
import com.infinicada.focuspocus.ui.screens.FocusScreen
import com.infinicada.focuspocus.ui.screens.OnboardingScreen
import com.infinicada.focuspocus.ui.theme.FocusPocusTheme
import com.infinicada.focuspocus.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests for the app's primary Compose surfaces (block overlay, pact offer,
 * dry Well screen, Focus screen, and Onboarding) in Dark and Light themes.
 */
@RunWith(AndroidJUnit4::class)
class ComposeSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun overlayScreen_showsSubstituteWithManaAndUnlocksCloseAfterCountdown() {
        var alternativeClicked = false
        var closeClicked = false

        composeRule.setContent {
            FocusPocusTheme(themeMode = ThemeMode.DARK) {
                OverlayScreen(
                    appName = "TubeStream",
                    spellName = "Deep Work",
                    alternativeName = "Kindle",
                    progressionEnabled = true,
                    onOpenAlternative = { alternativeClicked = true },
                    onClose = { closeClicked = true }
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.overlay_app_blocked, "TubeStream"))
            .assertIsDisplayed()

        // Substitute button is immediately usable and shows the mana reward.
        val subText = context.getString(
            R.string.overlay_pact_alternative_mana,
            "Kindle",
            ManaRules.SUBSTITUTE_WIN_MANA
        )
        composeRule.onNodeWithText(subText).assertIsDisplayed().assertIsEnabled().performClick()
        assertTrue(alternativeClicked)

        // Close button starts disabled during the 3-second pause, then unlocks.
        val waitText = context.getString(R.string.overlay_wait, 3)
        composeRule.onNodeWithText(waitText).assertIsNotEnabled()

        composeRule.mainClock.advanceTimeBy(3_500L)
        composeRule.waitForIdle()

        val closeText = context.getString(R.string.overlay_close)
        composeRule.onNodeWithText(closeText).assertIsEnabled().performClick()
        assertTrue(closeClicked)
    }

    @Test
    fun pactOfferScreen_allowsIntentionDuringPauseAndDeliversChoiceAfterCountdown() {
        var chosenMinutes: Int? = null
        var chosenIntention: PactIntention? = null
        var declined = false

        composeRule.setContent {
            FocusPocusTheme(themeMode = ThemeMode.DARK) {
                PactOfferScreen(
                    appName = "ScrollBook",
                    choicesMinutes = listOf(5, 15),
                    todayOpens = 4,
                    alternativeName = "Books",
                    progressionEnabled = true,
                    wellMinutes = 20,
                    wellCapacityMinutes = 30,
                    onOpenAlternative = {},
                    onPactChosen = { mins, intention ->
                        chosenMinutes = mins
                        chosenIntention = intention
                    },
                    onDecline = { declined = true }
                )
            }
        }

        // Pact buttons stay disabled during the 3-second pause.
        val fiveMinLabel = context.resources.getQuantityString(R.plurals.overlay_pact_choice_minutes, 5, 5)
        composeRule.onNodeWithText(fiveMinLabel).assertIsNotEnabled()

        // Optional intention chip is selectable during the pause.
        val quickReplyLabel = context.getString(R.string.pact_intention_quick_reply)
        composeRule.onNodeWithText(quickReplyLabel).assertIsDisplayed().performClick()

        // Advance past the 3-second pause and pick the 5-minute pact.
        composeRule.mainClock.advanceTimeBy(3_500L)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(fiveMinLabel).assertIsEnabled().performClick()
        assertEquals(5, chosenMinutes)
        assertEquals(PactIntention.QUICK_REPLY, chosenIntention)

        // Decline button works too.
        composeRule.onNodeWithText(context.getString(R.string.overlay_pact_decline)).performClick()
        assertTrue(declined)
    }

    @Test
    fun wellDryScreen_displaysMorningFocusPauseAndInteractiveActions() {
        var alternativeClicked = false
        var pourClicked = false
        var closeClicked = false
        val now = 1_700_000_000_000L
        val dryWell = WellSnapshot(
            enabled = true,
            capacityMinutes = 30,
            refillIntervalMinutes = 20,
            levelMillis = 0L,
            takenAtMillis = now,
            refillGate = WellRefillGate(
                morningFocusRequired = true,
                morningFocusMinutes = 10,
                morningUnlockedDate = null
            )
        )

        composeRule.setContent {
            FocusPocusTheme(themeMode = ThemeMode.LIGHT) {
                WellDryScreen(
                    appName = "ScrollBook",
                    well = dryWell,
                    neededMinutes = 5,
                    nowMillis = now,
                    alternativeName = "ReadWise",
                    progressionEnabled = true,
                    onOpenAlternative = { alternativeClicked = true },
                    pourCost = 75L,
                    onPour = { pourClicked = true },
                    onDecline = { closeClicked = true }
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.well_paused_morning_focus, 10))
            .assertIsDisplayed()

        val subLabel = context.getString(
            R.string.overlay_pact_alternative_mana,
            "ReadWise",
            ManaRules.SUBSTITUTE_WIN_MANA
        )
        composeRule.onNodeWithText(subLabel).performClick()
        assertTrue(alternativeClicked)

        val pourLabel = context.getString(R.string.well_dry_pour, ManaRules.POUR_MINUTES, 75L)
        composeRule.onNodeWithText(pourLabel).performClick()
        assertTrue(pourClicked)

        composeRule.onNodeWithText(context.getString(R.string.overlay_close)).performClick()
        assertTrue(closeClicked)
    }

    @Test
    fun focusScreen_idleAndActiveStatesRenderAndRespond() {
        val blocker = Blocker(name = "Social", mode = BlockerMode.BLACKLIST, apps = setOf("com.social"))
        var started = false

        composeRule.setContent {
            FocusPocusTheme(themeMode = ThemeMode.DARK) {
                FocusScreen(
                    focusMode = false,
                    activeTalismanId = null,
                    talismans = emptyList(),
                    activeBlockers = listOf(blocker),
                    activeSchedule = null,
                    blockerLists = listOf(blocker),
                    focusDurationMinutes = 25,
                    focusTimeRemaining = 0,
                    isOnBreak = false,
                    breakTimeRemaining = 0,
                    breaksUsedThisSession = 0,
                    maxBreaksPerSession = 3,
                    breaksAllowed = true,
                    sessionBreaksEnabled = true,
                    onBlockerToggled = {},
                    onDurationSelected = {},
                    onSessionBreaksToggled = {},
                    onStartClicked = { started = true },
                    onBlockerSelectorClicked = {},
                    onTakeBreak = {},
                    onEndBreak = {}
                )
            }
        }

        composeRule.onNodeWithText("Social").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_button_cast)).performClick()
        assertTrue(started)
    }

    @Test
    fun onboardingScreen_rendersInLightThemeAndAdvancesSteps() {
        composeRule.setContent {
            FocusPocusTheme(themeMode = ThemeMode.LIGHT) {
                OnboardingScreen(
                    talismans = emptyList(),
                    installedApps = emptyList(),
                    isServiceEnabled = false,
                    analyticsConsent = true,
                    onAnalyticsConsentChanged = {},
                    onCreateFirstPacts = {},
                    onSaveTag = {},
                    onComplete = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.onboarding_welcome_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_next))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.onboarding_accessibility_title))
            .assertIsDisplayed()
    }
}
