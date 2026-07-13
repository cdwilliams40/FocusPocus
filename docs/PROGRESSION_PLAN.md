# Focus Pocus — Progression Layer Implementation Plan

A concrete, codebase-verified plan for the gamification/reward addition ("the carrot
layer"). This supersedes the earlier Tier 1/2 notes: the scope, defaults, and several
mechanisms were decided against that draft. Every integration point below was checked
against the code at `b7b1388`, then the whole plan was adversarially re-verified
against the code a second time; mechanisms that failed that review (usage-history
reconstruction, single-gate break tokens, allowance-only sealed minutes) have been
replaced with ones that hold.

---

## 1. Decision record

| Decision | Choice |
|---|---|
| Features in scope | Mana & Boons; Trials & Sigils; streak milestones + positive tone |
| Features cut | **Streak freezes** (no forgiven days); **Mesmer/doomscroll detection** (no scroll events) |
| Boon model | Honor-system boons **plus** two app perks: extra break token, sealed-app minutes |
| Perks cut | Emergency reprieve (would undermine the emergency-stop cadence) |
| UI placement | Home + Insights; no new tab, no Spellbook section |
| Naming | Arcane names with plain subtitles ("Trials — daily & weekly challenges") |
| Trial payout | Claim button in app (auto-credit on expiry as fallback) |
| Feedback surfaces | Enriched end-of-session dialog; trial-completion notification; daily evening wrap-up. **No** per-session system notification |
| Default state | **On by default** for everyone, with a Settings master toggle to disable; one-time intro dialog for existing users |
| Talisman sessions | NFC-tag-only sessions (`setFocusTagId`, no `SessionManager`) are **not recorded today and earn no mana** — accepted; wiring them into `SessionManager` is out of scope |
| Emergency stop | Earns mana for time focused, stays dialog-free (visible in the ledger only) |
| Retroactive celebration | On first qualifying session, historical sessions/streaks unlock sigils and milestone bonuses in one batch — embraced as a welcome moment, with the dialog capped to the top 3 unlocks ("+N more in Insights") |

Because streak freezes are cut, the three existing streak-computation sites
(`SessionRecorder.record()`, `SessionRepository.getCurrentStreak()`,
`InsightsRepository.getCurrentStreak()`) and `calculateCurrentStreak` in `DateUtils.kt`
are **not touched**. That removes the riskiest part of the original draft.

## 2. Verified codebase grounding

Facts this plan depends on, confirmed in code:

- **Award choke point.** `SessionRecorder.record(prefs, gson)` is `@Synchronized`
  (`SessionRecorder.kt:21`) and its only production caller is
  `SessionManager.stopSession()` (`SessionManager.kt:67`). Every stop path funnels
  there: timer expiry, manual stop, dispel, emergency stop (`SessionViewModel.kt:208,
  280, 289, 335` respectively), NFC (`MainActivity.kt:257`), QR/NFC trigger
  (`TriggerHandler.kt:85`), and the service-side paths (`MyAccessibilityService.kt:467`
  timed expiry, `:689` ritual end). Exception: **talisman-only sessions** start/stop
  via `SessionRepository.setFocusTagId` (`SessionRepository.kt:160-169`,
  `MainActivity.kt:277-284`) and never touch `SessionManager` — they are not recorded
  and earn nothing (see decision record). `SessionRepository.recordSession()`
  (`SessionRepository.kt:34`) is dead code with zero callers — delete it in phase 1
  rather than retyping it.
- **Bonus signals are readable inside `record()`.** `ACTIVE_SCHEDULE_ID` is cleared at
  `SessionManager.kt:73`, *after* the `record()` call at `:67`, so the ritual bonus is
  readable at award time. `HIDE_STOP_BUTTON` exists (`Constants.kt:36`) and its Settings
  switch is disabled during an active session, so the value at record time held for the
  whole session. `newStreak` is already computed inside `record()`.
- **Sub-1-minute sessions are discarded** by an early return in `record()` — awards
  hooked after that point inherit the floor for free.
- **`record()` returns `List<FocusSession>`**, not the new session — callers currently
  can't tell what was recorded. The enriched summary dialog needs a richer return type
  (§3.4).
- **Notifications:** only one channel exists ("Rituals", created in
  `MyAccessibilityService.onServiceConnected()`); `Constants.FOCUS_SESSION_CHANNEL_ID`
  is declared but dead — delete it in phase 1. Channel creation must move to
  `FocusPocusApplication.onCreate` for anything posted outside the service (§3.5).
  Every **new** `notify()` call site must be guarded with
  `NotificationManagerCompat.areNotificationsEnabled()` /
  `checkSelfPermission(POST_NOTIFICATIONS)` — the `MissingPermission` lint error is not
  in the baseline and CI runs `lintDebug`.
- **There is no AlarmManager/WorkManager anywhere.** Rituals fire from an
  `ACTION_TIME_TICK` minute poll in `MyAccessibilityService.onMinuteTick()`
  (`MyAccessibilityService.kt:212-245`). The service's existing daily-reset marker
  (`lastCooldownResetDate`) is in-memory and re-seeded on reconnect — unreliable.
  The reliable pattern is compare-a-persisted-date-on-read (`OpenReflexTracker`
  compares `todayString()` on every read, `OpenReflexTracker.kt:70-76`).
- **Usage history cannot be reconstructed later.** `UsageStatsHelper` only queries
  "since start of today" / "since X until *now*" (`UsageStatsHelper.kt:101-106`), and
  its own KDoc notes detailed events are retained only a few days. Anything judging a
  *past* day from usage stats is unreliable — day-scoped trial evaluation uses
  `BLOCK_EVENTS` instead (§4.4).
- **Break gating is multi-site.** The take-break lambda gate is `FocusPocusApp.kt:477`,
  but button *visibility*, the "x/y breaks" label, and the exhausted-state branch all
  live in `HomeScreen.kt` (`:460`, `:444`, `:470`), driven by the `maxBreaksPerSession`
  param passed into `Greeting` at `FocusPocusApp.kt:438`. Pomodoro auto-breaks gate
  separately in `MyAccessibilityService.kt:492-495`.
- **Pact allowances:** `PactManager.grantAllowance(packageName, minutes, now)` exists
  (`limit/PactManager.kt:33`) but only writes an expiry. Enforcement order in
  `checkTimeLimitAndBlock` checks the daily limit first (`:848-859`) and an active
  cooldown (`:873-887`) **before** the allowance (`:893-894`) — so a bare allowance
  does not readmit a sealed app that is in cooldown (§4.3).
- **UI architecture:** no Jetpack Navigation in use (`navigation-compose` is a declared
  but unused dependency — don't be misled by the gradle file). Top-level tabs are
  `enum AppDestinations` + `NavigationSuiteScaffold`; Settings renders full-screen via
  a `rememberSaveable showSettings` boolean, `BackHandler`, its own
  Scaffold/TopAppBar with back arrow, and an early return — the Boons screen copies
  that whole pattern (§6.3). Screen composables are `Greeting(...)` (HomeScreen.kt:83)
  and `UsageStatsScreen(...)` (InsightsScreen.kt:81). Reusable pieces:
  `GlassCard(modifier, contentPadding, content)`, `SectionHeader(text, modifier)`,
  `StatTile(value, label, modifier, accent)` (ArcaneComponents.kt:74/110/141);
  single-app selection uses `SingleAppPickerDialog` (ui/components/AppPicker.kt:95);
  gold/tertiary is the established reward accent.
- **Data layer:** single SharedPreferences file (`Constants.PREFS_NAME`), Gson via
  `PrefsHelper.load/save`, stateless repos constructed in `AppContainer`.
  `SessionRepository(context, prefs, gson)` is the precedent for a context-holding
  repo. Settings toggles use `SettingsRepository`'s raw-primitive style (no Gson).
  UI refresh paths: `dataVersion` (Spellbook CRUD only), and
  `LaunchedEffect(nfcTriggerCount)`/`(qrTriggerCount)` (`FocusPocusApp.kt:161-172`)
  driven by `MainActivity.onResume` and its prefs listener — the only hooks that fire
  after service-side or trigger-driven session ends.
- **i18n:** `values-fr` exists but is ~60 strings behind, grandfathered in
  `lint-baseline.xml` (61 MissingTranslation, 5 MissingQuantity). Only **new** lint
  errors fail CI — new strings must ship in both locales, count-bearing ones as
  `<plurals>`.
- **Analytics:** Firebase Analytics is wired for consent only; there are zero
  `logEvent` calls today. Optional events (§9) would be new surface, deliberately
  out of scope.

## 3. Shared foundation

### 3.1 Naming glossary (user-facing)

| Concept | Name | Subtitle shown in UI |
|---|---|---|
| Points | **Mana** | "focus energy" |
| Self-defined rewards | **Boons** | "your rewards" |
| In-app perks | **Perks** | "spend mana in-app" |
| Challenges | **Trials** | "daily & weekly challenges" |
| Achievements | **Sigils** | "milestones you've earned" |

### 3.2 New prefs keys, caps, and notification ids

Add to `Constants.PrefsKeys` (constant names SCREAMING_SNAKE, values camelCase,
matching the file's convention):

```kotlin
// Progression
const val PROGRESSION_ENABLED = "progressionEnabled"          // Boolean, default true
const val PROGRESSION_INTRO_SHOWN = "progressionIntroShown"   // Boolean (one-time dialog)
const val MANA_BALANCE = "manaBalance"                        // Long
const val MANA_LEDGER = "manaLedger"                          // List<ManaLedgerEntry> JSON
const val BOONS = "boons"                                     // List<Boon> JSON
const val TRIALS = "trials"                                   // List<Trial> JSON (active + recent)
const val UNLOCKED_SIGILS = "unlockedSigils"                  // Set<String> JSON
const val HIGHEST_STREAK_MILESTONE_PAID = "highestStreakMilestonePaid" // Int
const val EXTRA_BREAK_TOKENS = "extraBreakTokens"             // Int (session-scoped)
const val LAST_SESSION_RECORDED_DATE = "lastSessionRecordedDate" // String yyyyMMdd (§5.3 cheap guard)
const val LAST_WRAPUP_DATE = "lastWrapupDate"                 // String yyyyMMdd
const val WRAPUP_ENABLED = "wrapupEnabled"                    // Boolean, default true
const val TRIAL_ALERTS_ENABLED = "trialAlertsEnabled"         // Boolean, default true
```

Caps beside the other `MAX_*` in `Constants`: `MAX_BOONS = 50`,
`MAX_MANA_LEDGER = 300`. The ledger prunes with `takeLast` (like `FOCUS_SESSIONS`);
boons reject at cap (like `MAX_SCHEDULES`). Trials never accumulate — the list is
replaced on rotation.

New channel id: `PROGRESSION_CHANNEL_ID = "focus_pocus_progression"`. New notification
id constants beside `FOCUS_SESSION_NOTIFICATION_ID = 9001`:
`TRIAL_COMPLETION_NOTIFICATION_ID = 9002`, `DAILY_WRAPUP_NOTIFICATION_ID = 9003`.
Delete the dead `FOCUS_SESSION_CHANNEL_ID` in the same phase-1 commit (do **not**
reuse it for progression — new name, new mutability boundary).

### 3.3 Models (`model/Progression.kt`, `model/Trial.kt`)

Ledger entries are **structured, not display strings** — the app is bilingual, and
the sealed-minutes perk needs to query past redemptions. Text is rendered from
resources at display time.

```kotlin
enum class LedgerKind { SESSION, TRIAL, BOON, PERK, MILESTONE, SIGIL }

data class ManaLedgerEntry(
    val timestampMillis: Long,
    val amount: Long,            // + earned, - spent
    val kind: LedgerKind,
    val minutes: Int = 0,        // SESSION: focused minutes
    val title: String = "",      // BOON: user's boon title (user data, not localizable)
    val refId: String = "",      // TRIAL/SIGIL id, PERK name
    val packageName: String = "",// PERK SEALED_MINUTES: which app
    val dateKey: String = ""     // "yyyyMMdd" of the award day (perk per-day guard)
)

data class Boon(
    val id: String,              // UUID
    val title: String,
    val costMana: Long,
    val note: String = ""
)

enum class TrialType { COMPLETE_SESSIONS, FOCUS_MINUTES, STAY_UNDER_LIMITS, NO_REFLEX_OPENS, COMPLETE_RITUAL }
enum class TrialPeriod { DAILY, WEEKLY }

data class Trial(
    val id: String,
    val type: TrialType,
    val target: Int,
    val progress: Int = 0,
    val period: TrialPeriod,
    val periodKey: String,       // "yyyyMMdd" or "yyyy-Www" (ISO week, Monday start; java.time OK, minSdk 29)
    val rewardMana: Long,
    val claimed: Boolean = false
)

data class Sigil(val id: String, val titleRes: Int, val descriptionRes: Int)
```

Trial titles are derived from `(type, target)` via string resources, not stored —
persisted data stays locale-independent.

### 3.4 Award pipeline and `RecordResult`

**Pure math first.** `ProgressionMath.kt` (top-level functions, same style as
`calculateCurrentStreak`):

```kotlin
fun computeManaAward(
    durationMinutes: Int,
    newStreak: Int,
    hideStopButton: Boolean,
    fromRitual: Boolean
): Long
```

Formula (all constants in one place, tunable):
- Base: `min(durationMinutes, 240)` mana — the 240 cap stops an 8-hour Sleep Mode or
  unlimited overnight session from minting a fortune.
- Skin-in-the-game: ×1.25 when `hideStopButton`.
- Consistency: ×(1 + 0.02 × min(newStreak, 25)) — tops out at +50%.
- Ritual: +20 flat for honoring a scheduled commitment.
- Result rounded down; emergency-stopped sessions earn like any other (the time was
  still focused; prefs are honor-system anyway, so generosity beats anti-cheat).

**Hook and locking.** Inside `SessionRecorder.record()`, after `newStreak` is computed,
call `Progression.awardForSession(prefs, gson, session, newStreak)` — a static `object`
like the recorder. Its first act is `rolloverIfNeeded(...)` (so a session ending after
midnight never credits yesterday's trial slate), then: update `MANA_BALANCE`, append a
ledger entry, advance session-driven trials, evaluate sigils/milestones. It no-ops
(except trial-slate rotation, which must not credit) when `PROGRESSION_ENABLED` is
false, and sits **after** the existing <1-minute early return. It also writes
`LAST_SESSION_RECORDED_DATE = todayString()` (§5.3).

**One monitor for all mana writes.** `@Synchronized` on `record()` locks
`SessionRecorder`; a repository instance would lock itself — two different monitors,
same prefs keys. All progression mutations (`awardForSession`, `claimTrial`,
`redeemBoon`/`redeemPerk`, rollover credit) therefore go through the same
`Progression` object's `@Synchronized` methods; `ProgressionRepository` delegates its
writes there. Everything is main-thread today, but this is load-bearing the moment any
evaluation moves to `Dispatchers.IO`.

**Return type change.** `record()` currently returns `List<FocusSession>`. Change to:

```kotlin
data class RecordResult(
    val sessions: List<FocusSession>,        // pruned list (existing return value)
    val recorded: FocusSession? = null,      // null when discarded (<1 min / no start time)
    val manaEarned: Long = 0,
    val newStreak: Int = 0,
    val completedTrials: List<Trial> = emptyList(),  // newly completed, unclaimed
    val unlockedSigils: List<Sigil> = emptyList()
)
```

Thread it through `SessionManager.stopSession()` (currently returns `Unit` → return
`RecordResult`) and `SessionRepository.stopSession()`. All ignoring call sites compile
unchanged; **delete** the dead `SessionRepository.recordSession()` (its explicit
`List<FocusSession>` return type would otherwise break). `SessionViewModel` uses the
result for the enriched dialog (§5.1); `SessionManager.stopSession()` posts the
trial-completion notification (§5.2) since it has the `Context` that `record()` lacks —
the posting code must tolerate a mock/null `NotificationManager` because
`SessionManagerTest` passes a Mockito `Context`. Update the 7 `record()`-calling cases
in `SessionRecorderTest` (9 total; 2 test `calculateCurrentStreak` and are unaffected).

### 3.5 Notification channels

Create **both** channels in `FocusPocusApplication.onCreate` (idempotent API):
- Rituals (move the existing creation here; keep the service-side call as a safety
  net — `createNotificationChannel` is safe to call twice).
- Progression: `PROGRESSION_CHANNEL_ID`, name "Progression", description "Mana, trials
  and daily wrap-ups", `IMPORTANCE_LOW` — celebrates without buzzing, and users can
  mute gamification without losing ritual alerts.

### 3.6 Repository, DI, and UI refresh wiring

`data/ProgressionRepository.kt`, constructor `(context, prefs, gson)` — the
`SessionRepository` precedent, because trial-template gating needs
`UsageStatsHelper.hasUsageStatsPermission(context)`. Reads via `PrefsHelper.load ?:
empty`; writes delegate to the shared `Progression` monitor (§3.4):

```kotlin
fun redeemBoon(boon: Boon): Boolean            // balance check, deduct, ledger entry
fun redeemPerk(perk: Perk, packageName: String? = null): Boolean  // §4.3
fun claimTrial(trialId: String): Long          // credits rewardMana, marks claimed, ledger entry
fun getTrials(): List<Trial>                   // calls rolloverIfNeeded() first — compare-on-read
fun rolloverIfNeeded()                         // §4.4; idempotent, cheap when not stale
```

Every mutating method is gated on `PROGRESSION_ENABLED` (rotation may still replace a
stale trial list while disabled, but must not credit mana; sigil unlock helpers and
boon/perk redemption all check the flag).

Register `val progression = ProgressionRepository(context, prefs, gson)` in
`AppContainer`. Expose state through a new `ProgressionViewModel` (AndroidViewModel,
same `MutableStateFlow(repo.getX()).asStateFlow()` pattern) with a `refresh()`.

**Refresh wiring (easy to get wrong):** `dataVersion` bumps only on Spellbook CRUD —
it never fires on session ends. Add `progressionVM.refresh()` to **all three** existing
`LaunchedEffect`s in `FocusPocusApp` (`nfcTriggerCount`, `qrTriggerCount` at
`:161-172`, and `dataVersion` at `:175`) — the `nfcTriggerCount` path is what covers
service-side and TriggerHandler stops, via `MainActivity.onResume`'s forced sync and
its prefs listener. Also refresh after every in-app stop/claim/redeem. Because
`getTrials()` itself rolls over on read, an overnight-cached process shows a fresh
slate on next resume.

## 4. Feature specs

### 4.1 Mana economy

Earning: sessions only (per §3.4), plus trial claims and sigil/milestone bonuses. All
earning routes through the single `Progression` monitor — one writer discipline, no
double-award. Spending: boons and perks. Balance can never go negative; redeem methods
return false and the UI disables buttons below cost.

### 4.2 Boons (honor system)

User-authored: title, cost, optional note. CRUD mirrors the Quick Spell editor flow.
Redeeming deducts mana, appends a ledger entry, and shows a small celebratory
confirmation — the app never verifies real-world follow-through (that's the point:
private, honest, no marketplace).

### 4.3 Perks (code-defined catalog, two at launch)

```kotlin
enum class Perk(val costMana: Long) {
    EXTRA_BREAK(50),          // one extra break in the current session
    SEALED_MINUTES(150)       // 10 minutes in one pacted/sealed app
}
```

- **Extra break token.** Redeemable only during an active **`MANUAL_FOCUS_MODE`**
  session (talisman-only sessions have the break UI but no recording — excluded) when
  breaks are exhausted. Redemption increments `EXTRA_BREAK_TOKENS`. Wiring: pass
  `maxBreaksPerSession = effectiveMaxBreaks + extraBreakTokens` into `Greeting` at
  `FocusPocusApp.kt:438` — that one change consistently fixes the lambda gate (`:477`),
  the button visibility (`HomeScreen.kt:460`), the "x/y breaks" label (`:444`), and the
  exhausted-state branch (`:470`). The redemption affordance lives in that
  exhausted-state branch: a "Use 50 mana for an extra break" button on the active-
  session card. The Pomodoro auto-break gate (`MyAccessibilityService.kt:492-495`)
  does **not** consume tokens. Tokens are cleared defensively in **both**
  `SessionManager.startSession()`'s and `stopSession()`'s prefs editors so they can
  never leak across sessions.
- **Sealed-app minutes.** From the Boons screen, pick a pacted app via
  `SingleAppPickerDialog` filtered to pacted apps; redeem then does **two** things:
  `PactManager.grantAllowance(packageName, 10)` **and** clears that package's active
  cooldown (new `SessionCooldownManager.clearCooldown(packageName)` — the class only
  has bulk/expired clears today). Without the second step the enforcement order in
  `checkTimeLimitAndBlock` (cooldown checked at `:873-887`, before the allowance at
  `:893-894`) blocks exactly the user who just paid. **The perk never overrides a
  spent daily limit** (checked first at `:848-859`): the redeem button is disabled for
  apps currently over their daily limit, with a "daily limit spent" hint. Cap: one
  redemption per app per day, checked against ledger entries with
  `kind == PERK && packageName == X && dateKey == today` — structured fields, no
  string parsing.

Both perks live at the bottom of the Boons screen under a "Perks — spend mana in-app"
`SectionHeader`, visually separated from honor boons (plus the in-session extra-break
button above).

### 4.4 Trials

**Active set:** 2 daily + 1 weekly. Rotation happens in `rolloverIfNeeded()`, called
from `getTrials()` (compare-on-read — the `OpenReflexTracker` pattern) and as the first
act of `awardForSession()` (§3.4), so neither a cached overnight process nor a
post-midnight session end can act on a stale slate. It compares each trial's
`periodKey` to the current day/ISO-week key. On rotation: completed-but-unclaimed
trials are auto-credited with a ledger entry (`kind = TRIAL`), then the slate is
redrawn from a code-defined template list, seeded by the periodKey's `hashCode()`
(specification-defined, stable across process restarts; both call paths are
main-thread, so no double-rotation race).

**Templates (launch set):**

| Type | Example targets | Period | Reward |
|---|---|---|---|
| COMPLETE_SESSIONS | 2–3 sessions | daily | 40 |
| FOCUS_MINUTES | 60–120 min | daily | 50 |
| COMPLETE_RITUAL | 1 scheduled ritual completed | daily | 40 |
| STAY_UNDER_LIMITS | no limit breaches all day | daily | 60 |
| FOCUS_MINUTES | 300–600 min | weekly | 200 |
| NO_REFLEX_OPENS | ≤ N reflex opens | weekly | 150 |

**Draw-time exclusion rules** (never show a trial that can't be measured or is
vacuously true):
- `STAY_UNDER_LIMITS`: requires usage-stats permission **and** ≥ 1 configured daily
  limit — otherwise "no breaches" is a free 60 mana.
- `NO_REFLEX_OPENS`: requires ≥ 1 tracked app (time-limit config or pact group) with
  recorded open history — `OpenReflexTracker` only records tracked apps.
- Mid-period permission grants take effect at the next rotation — accepted.

**Evaluation:**
- Session-driven types (`COMPLETE_SESSIONS`, `FOCUS_MINUTES`, `COMPLETE_RITUAL`)
  update inside `Progression.awardForSession()`. A trial crossing
  `progress >= target` lands in `RecordResult.completedTrials`.
- Day-scoped types are judged from **`BLOCK_EVENTS`, not usage reconstruction**:
  usage stats can't be queried for a past day (no bounded-range API; events retained
  only a few days — an idle stretch would falsely score as success).
  "Stayed under limits on day D" ≡ limits were configured **and** no block event with
  `blockerName == "Time Limit"` has a timestamp in D — which matches enforcement
  semantics exactly (conditional unlocks never record a block, so they legitimately
  pass). `NO_REFLEX_OPENS` sums `OpenReflexTracker.getDailyStats()` for the period
  (30-day retention comfortably covers an ISO week). Judged at rollover for the
  previous periodKey; in-period trial cards show best-effort live progress ("no
  breaches so far today" from today's block events / today's reflex count), judged
  authoritatively at rollover.

**Claiming:** Claim button on the trial card credits `rewardMana`. Completion detected
at session end also posts the trial-alert notification (§5.2) when enabled. Rollover
auto-credits are silent (they happen at app-open time, when the user is looking).

### 4.5 Sigils and streak milestones

Code-defined catalog (`SigilCatalog.ALL`), persisted state is only the unlocked-id set.
Launch catalog (~12): first session; first ritual completed; first pact honored a full
day; 10-hour focus week; 100 total sessions; Warden provisioned; streak milestones
7/30/100 ("Seven Nights", "Thirty Nights", "Hundred Nights"); first boon redeemed;
1 000 lifetime mana; hidden-stop-button session completed.

- Evaluation is idempotent (set-add) and runs in `Progression.awardForSession()` for
  session/streak-driven sigils; boon/mana sigils unlock from the redeem/claim methods.
  All unlock paths respect `PROGRESSION_ENABLED`.
- **Warden sigil:** `DeviceOwnerManager` has **no** provisioning callback — setup
  happens via adb. Hook the sigil in `FocusDeviceAdminReceiver.onEnabled`
  (`FocusDeviceAdminReceiver.kt:18-23`, guarded by an `isDeviceOwner()` check since
  `onEnabled` also fires for plain admin activation) **and** in
  `SettingsViewModel.refreshDeviceOwnerState` when it observes the flag flip to true —
  covering both the provisioning moment and the recheck button.
- **Streak milestones double as the celebration moment:** unlocking 7/30/100 grants
  bonus mana (100/500/2000) guarded by `HIGHEST_STREAK_MILESTONE_PAID` so a streak
  that re-crosses 7 after a break pays again only above the historical high-water
  mark.
- **Retroactive first run:** an existing user with a 45-day streak and 300 sessions
  will, on their first recorded session, unlock several sigils and the 7+30-day
  bonuses at once. Decision: embrace it as a welcome celebration — the enriched dialog
  shows at most the top 3 unlock lines plus "+N more in Insights", and the intro
  dialog (§6.6) sets the expectation.

## 5. Tone: feedback surfaces

### 5.1 Enriched end-of-session dialog

The existing summary dialog (`SessionViewModel._showSessionSummary`, rendered at
`FocusPocusApp.kt:687-779`) gains: mana earned (with bonus breakdown on one line),
current streak, trial completions ("Trial complete — claim in Insights"), and capped
sigil/milestone unlock lines (§4.5). Data comes from the `RecordResult`.

There are **two** pre-stop capture sites to rework, not one: `stopSessionWithSummary`
(`SessionViewModel.kt:264-275`, invoked from `FocusPocusApp.kt:454-459` before the
separate stop call) and `handleTimerExpired` (`SessionViewModel.kt:198-212`). Both
switch to populating the summary from the `RecordResult` returned by the stop call —
and only when `RecordResult.recorded != null`, which also makes the emergency path's
double stop (`emergencyStop()` then `dispelSchedule()`, `FocusPocusApp.kt:483-485`)
a natural no-op on the second, empty result. Emergency stop stays dialog-free (per the
decision record — its mana appears in the ledger). Progression rows in the dialog
render only when `PROGRESSION_ENABLED`; no "0 mana" rows when the layer is off. No
system notification on foreground stops (decided: avoid double feedback).

### 5.2 Trial-completion notification

Posted from `SessionManager.stopSession()` when `RecordResult.completedTrials` is
non-empty and `TRIAL_ALERTS_ENABLED` — "Trial complete: Cast 3 spells — claim your 40
mana". Progression channel, `TRIAL_COMPLETION_NOTIFICATION_ID`, auto-cancel, content
intent to MainActivity. Guarded by the POST_NOTIFICATIONS permission check (§2) and
null-safe against mock Contexts in tests.

### 5.3 Daily evening wrap-up (best effort)

In `MyAccessibilityService.onMinuteTick()`: if `WRAPUP_ENABLED` and
`PROGRESSION_ENABLED`, local time ≥ 20:00, `LAST_WRAPUP_DATE != todayString()`, and
today has recorded activity — post "You reclaimed 2h 15m today · +180 mana · 12-day
streak" on the Progression channel (`DAILY_WRAPUP_NOTIFICATION_ID`), then persist
`LAST_WRAPUP_DATE`.

The activity check compares the cheap `LAST_SESSION_RECORDED_DATE` string (written
inside `record()`'s editor) **first**, so inactive evenings cost a string compare per
tick, not a 500-entry JSON parse × 240 ticks. Only on active days does it parse
`FOCUS_SESSIONS` (reclaimed minutes, streak) and the ledger (mana today). The gating
logic is extracted as a pure `shouldSendWrapup(now, lastWrapupDate,
lastSessionRecordedDate, wrapupEnabled, progressionEnabled)` for testing. Persisted-
date guard means a service restart at 21:30 still sends it (next tick); a fully-down
service skips the day silently. Suppressed on inactive days so it never scolds.
Explicitly accepted as best-effort — no WorkManager dependency for this.

## 6. UI integration

### 6.1 Home (`Greeting` in HomeScreen.kt)

- Idle state: a **mana chip** next to the existing `StreakBadge` AssistChip (same
  row, `AutoAwesome` icon, tertiary/gold accent, "420 mana"). Tapping opens the Boons
  screen. Hidden when progression is off.
- Below Session Setup: a compact **"Today's Trials"** `GlassCard` — one row per active
  daily trial (title, thin progress bar, "2/3"), Claim button inline when complete.
  Weekly trial included collapsed.
- Active state: the extra-break redemption button in the breaks-exhausted branch
  (§4.3).

### 6.2 Insights (`UsageStatsScreen` in InsightsScreen.kt)

New sections after "Focus Streaks":
- **"Mana — focus energy"**: `StatTile` row — balance / earned this week / sigils
  unlocked count; a "Manage boons" button opening the Boons screen; last ~5 ledger
  entries in a `GlassCard`.
- **"Trials — daily & weekly challenges"**: full trial cards with progress bars and
  Claim buttons (the canonical claim surface).
- **"Sigils — milestones you've earned"**: 3-per-row grid of `GlassCard` tiles;
  locked ones dimmed with their unlock hint; unlocked show a gold glyph.

`UsageStatsScreen` takes ~7 new params (trials + onClaimTrial, balance, ledger tail,
unlocked sigils, onOpenBoons) — fits the existing hoisted-state shape without a
refactor. The progression sections are **independent of the screen's Today/Week/Month
time-range tabs** (trials have their own periods; sigils are lifetime).

### 6.3 Boons screen

Full-screen overlay copying the Settings pattern completely: a `rememberSaveable
showBoons` flag in `FocusPocusApp` (early-returned alongside — and mutually exclusive
with — `showSettings`), `BackHandler { showBoons = false }`, its own Scaffold with
transparent `TopAppBar`, back arrow (`R.string.nav_back`), `onNavigateBack` callback,
wrapped in `ArcaneBackground`. `onOpenBoons` lambdas thread into both `Greeting` and
`UsageStatsScreen`. The create/edit editor is in-screen sub-state (like the Quick
Spell editor) with its own back-handling step before the screen dismisses. Contents:
balance header, boon list (`GlassCard` per boon: title, cost, Redeem button disabled
below balance), editor, Perks section (§4.3).

### 6.4 Settings

New "Progression" card between Focus Behavior and Notifications:
- Master toggle "Progression — mana, trials & sigils" (default **on**; raw-primitive
  getter `getProgressionEnabled` default true in `SettingsRepository`). Turning it off
  hides all surfaces and stops every award/credit path (§3.6) but **preserves**
  balance/ledger/sigils for re-enable.
- Sub-toggles (enabled only when master is on): "Daily wrap-up notification",
  "Trial completion alerts".

### 6.5 Strings, i18n, accessibility

- Realistic scale: **~100–130 new strings per locale** (glossary + subtitles, 6 trial
  templates, ~12 sigils × 2, Boons CRUD, perks, settings, notifications, dialog lines,
  intro dialog). Count-bearing strings ("Cast %d spells", "%d mana", "%d-day streak")
  ship as `<plurals>` with proper FR quantities. Everything lands in `values/` and
  `values-fr/` — only new lint errors fail CI, but don't add to the baselined FR debt.
- TalkBack: trial rows use merged semantics announcing title + progress ("Cast 3
  spells, 2 of 3 complete"); sigil tiles carry `stateDescription` locked/unlocked
  (dimming alone is invisible to TalkBack); the mana chip and Claim buttons get
  descriptive labels — following the app's existing `contentDescription` conventions.

### 6.6 First-run intro (on-by-default migration)

One-time dialog for existing users, following the analytics-consent pattern
(`ANALYTICS_CONSENT_SHOWN` / `FocusPocusApp.kt:239-255`): gated on
`PROGRESSION_INTRO_SHOWN`, shown once after update — a short "Focusing now earns
mana" intro with a "turn off in Settings" pointer. New users get a progression page
in `OnboardingScreen`. Without this, v-next users meet mana chips, trial cards, and a
new notification channel with zero explanation.

## 7. Testing

Pure-function unit tests (existing FakeSharedPreferences + JUnit4 setup):
- `ProgressionMathTest`: award formula — base, 240 cap, each multiplier, stacking,
  rounding, zero-duration.
- `TrialEngineTest`: template draw determinism per periodKey; progress advancement per
  type; completion detection; rotation with auto-claim of unclaimed completions;
  draw-time exclusions (no permission / zero limits / zero tracked apps); block-event
  day-scoping for STAY_UNDER_LIMITS; ISO-week key edges (year boundary).
- `SigilTest`: idempotent unlock; milestone high-water-mark guard (re-crossing 7 after
  100 pays nothing); retroactive batch on first run.
- `ProgressionRepositoryTest`: redeem below balance fails; ledger prune at 300; boon
  cap rejection; perk once-per-app-per-day guard via structured ledger fields; all
  mutations no-op when `PROGRESSION_ENABLED` is false.
- `WrapupTest`: `shouldSendWrapup` — time gate, date guard, inactive-day suppression,
  both flags.
- Update the 7 `record()`-calling `SessionRecorderTest` cases (9 total); add: award
  written atomically with session; no award when disabled; no award on <1-min discard;
  `LAST_SESSION_RECORDED_DATE` written.
- Update `SessionManagerTest` (mock `Context`) for the new return type and the
  null-safe notification path; add `EXTRA_BREAK_TOKENS` cleared on start **and** stop.
- `SettingsRepositoryTest`: the three new raw-primitive toggles.
- `SessionCooldownManagerTest` addition for the new `clearCooldown(packageName)`.

## 8. Build order

1. **Foundation** — models, prefs keys, `ProgressionMath`, `Progression` monitor +
   `awardForSession` hook, `RecordResult` threading (incl. deleting dead
   `recordSession()`), channels + id constants in Application (incl. deleting dead
   `FOCUS_SESSION_CHANNEL_ID`), Settings master toggle, repository + ViewModel +
   refresh wiring into all three LaunchedEffects, mana chip on Home, Mana section on
   Insights, intro dialog. *Ships alone: earning visibly works.*
2. **Boons** — Boons screen (Settings-pattern overlay), CRUD, honor redemption,
   ledger UI.
3. **Trials** — templates, rotation (compare-on-read + award-path), session-driven
   evaluation, block-event evaluation, Home/Insights cards, claim flow, completion
   notification.
4. **Sigils + milestones** — catalog, evaluation sites (incl.
   `FocusDeviceAdminReceiver.onEnabled` for Warden), Insights grid, capped dialog
   unlock lines, retroactive batch.
5. **Perks** — extra break token (thread through `Greeting`, in-session affordance,
   start/stop clearing), sealed-app minutes (`grantAllowance` + new `clearCooldown`,
   daily-limit precedence, per-day guard).
6. **Wrap-up notification** (`shouldSendWrapup` + minute-tick wiring) + final dialog
   polish + a11y pass.

Each phase is independently shippable and gated by the same master toggle.

## 9. Risks & mitigations

- **Prefs size / main-thread writes.** One prefs file already holds ≤500 sessions +
  ≤1000 block events; every `apply()` rewrites the whole file. Mitigation: ledger
  capped at 300, trials list replaced not accumulated, sigils are a small id set,
  wrap-up guard is a string compare. No per-scroll or per-tick writes anywhere in
  this plan.
- **Double feedback.** Foreground stops get the dialog only; notifications are
  reserved for trial completions and the wrap-up. A ritual ending in the background
  already has its ritual-end notification — we deliberately do not add a second one.
- **POST_NOTIFICATIONS.** Requested via legacy `requestPermissions` with no follow-up;
  if denied, trial alerts and wrap-ups vanish silently — and unguarded `notify()`
  calls fail `MissingPermission` lint (CI-blocking). All new call sites are guarded
  (§2); the core loop (earn → see balance → redeem) works entirely in-app. A Settings
  hint ("notifications disabled at system level") is a cheap phase-6 add.
- **Economy inflation.** All rates live in one constants block; the structured ledger
  makes drift visible. Honor-system stance: tune for feel, not fraud-proofing.
- **`record()` signature ripple.** `RecordResult` touches SessionManager,
  SessionRepository (including deleting `recordSession()`), SessionViewModel and two
  test files — mechanical but must land as one commit (phase 1) to keep the build
  green.
- **BLOCK_EVENTS retention.** Day-scoped trials read block events; at 1000-entry
  pruning a heavy blocker could theoretically age out same-week events — acceptable
  for a daily trial (same-day events are always present), and the weekly templates
  don't use block events.
- **Analytics (optional, deferred).** If earn/redeem/claim events are ever wanted,
  they must respect `ANALYTICS_CONSENT` (default true, opt-out) — there is no
  event-logging helper today; that would be new surface, deliberately out of scope.

## 10. Open tunables (defaults chosen, revisit after use)

- Award constants: 1 mana/min, 240/session cap, ×1.25 hidden-stop, +2%/streak-day
  (≤+50%), +20 ritual flat.
- Perk prices: extra break 50, 10 sealed minutes 150; sealed-minutes limited to one
  redemption per app per day; never overrides a spent daily limit.
- Trial rewards: 40–60 daily, 150–200 weekly; 2 daily + 1 weekly active.
- Milestone bonuses: 100/500/2000 mana at 7/30/100 days.
- Wrap-up trigger time: 20:00 local.
- Dialog unlock-line cap: 3.
