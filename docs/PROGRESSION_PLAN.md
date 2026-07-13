# Focus Pocus — Progression Layer Implementation Plan

A concrete, codebase-verified plan for the gamification/reward addition ("the carrot
layer"). This supersedes the earlier Tier 1/2 notes: the scope, defaults, and several
mechanisms were decided against that draft, and every integration point below has been
checked against the code as of `b7b1388`.

---

## 1. Decision record

| Decision | Choice |
|---|---|
| Features in scope | Mana & Boons; Trials & Sigils; streak milestones + positive tone |
| Features cut | **Streak freezes** (no forgiven days); **Mesmer/doomscroll detection** (no scroll events) |
| Boon model | Honor-system boons **plus** two app perks: extra break token, sealed-app minutes |
| Perks cut | Emergency reprieve (would undermine the emergency-stop cadence) |
| UI placement | Home + Insights; no new tab, no Spellbook section |
| Naming | Arcane names with plain subtitles ("Trials — daily challenges") |
| Trial payout | Claim button in app (auto-credit on expiry as fallback) |
| Feedback surfaces | Enriched end-of-session dialog; trial-completion notification; daily evening wrap-up. **No** per-session system notification |
| Default state | **On by default** for everyone, with a Settings master toggle to disable |

Because streak freezes are cut, the three existing streak-computation sites
(`SessionRecorder.record()`, `SessionRepository.getCurrentStreak()`,
`InsightsRepository.getCurrentStreak()`) and `calculateCurrentStreak` in `DateUtils.kt`
are **not touched**. That removes the riskiest part of the original draft.

## 2. Verified codebase grounding

Facts this plan depends on, confirmed in code:

- **Award choke point.** `SessionRecorder.record(prefs, gson)` is `@Synchronized`
  (`SessionRecorder.kt`) and its only production caller is
  `SessionManager.stopSession()` (`SessionManager.kt:67`). Every stop path funnels
  there: manual stop, timer expiry, dispel, emergency stop (`SessionViewModel.kt:208,
  280, 289, 335`), NFC (`MainActivity.kt:257`), QR/NFC trigger (`TriggerHandler.kt:85`),
  and the service-side paths (`MyAccessibilityService.kt:467` timed expiry, `:689`
  ritual end). `SessionRepository.recordSession()` (`SessionRepository.kt:34`) is dead
  code with zero callers — do **not** hook it.
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
  is declared but dead — no channel, no posts. Channel creation must move to
  `FocusPocusApplication.onCreate` for anything posted outside the service (§3.5).
- **There is no AlarmManager/WorkManager anywhere.** Rituals fire from an
  `ACTION_TIME_TICK` minute poll in `MyAccessibilityService.onMinuteTick()`
  (`MyAccessibilityService.kt:212-245`). The service's existing daily-reset marker
  (`lastCooldownResetDate`) is in-memory and re-seeded on reconnect — unreliable.
  The reliable pattern is `OpenReflexTracker`'s: persist state keyed by
  `SessionCooldownManager.todayString()` ("yyyyMMdd") and compare on read.
- **Break gating:** manual breaks are gated in the UI at `FocusPocusApp.kt:477`
  (`breaksUsedThisSession < effectiveMaxBreaks`); Pomodoro auto-breaks are gated
  separately in `MyAccessibilityService.kt:492-495`.
- **Pact allowances:** `PactManager.grantAllowance(packageName, minutes, now)` exists
  (`limit/PactManager.kt:33`) — the sealed-app-minutes perk calls it directly.
- **UI architecture:** no Jetpack Navigation. Top-level tabs are `enum AppDestinations`
  + `NavigationSuiteScaffold`; Settings renders full-screen via a `showSettings`
  boolean with an early return — the Boons screen reuses that pattern (§6.3). Screen
  composables are `Greeting(...)` (HomeScreen.kt) and `UsageStatsScreen(...)`
  (InsightsScreen.kt). Reusable pieces: `GlassCard(modifier, contentPadding, content)`,
  `SectionHeader(text, modifier)`, `StatTile(value, label, modifier, accent)`;
  gold/tertiary is the established reward accent (streak tiles, StreakBadge).
- **Data layer:** single SharedPreferences file (`Constants.PREFS_NAME`), Gson via
  `PrefsHelper.load/save`, stateless repos constructed in `AppContainer`. Settings
  toggles use `SettingsRepository`'s raw-primitive style (no Gson). ViewModels refresh
  through the `dataVersion` counter in `SpellbookViewModel` + `LaunchedEffect` in
  `FocusPocusApp`.
- **i18n:** the app maintains French translations (`values-fr`) — every new string
  ships in both locales or lint fails the baseline.
- **Analytics:** Firebase Analytics is wired for consent only; there are zero
  `logEvent` calls today. Optional events (§9) would be the first.

## 3. Shared foundation

### 3.1 Naming glossary (user-facing)

| Concept | Name | Subtitle shown in UI |
|---|---|---|
| Points | **Mana** | "focus energy" |
| Self-defined rewards | **Boons** | "your rewards" |
| In-app perks | **Perks** | "spend mana in-app" |
| Challenges | **Trials** | "daily & weekly challenges" |
| Achievements | **Sigils** | "milestones you've earned" |

### 3.2 New prefs keys and caps

Add to `Constants.PrefsKeys` (constant names SCREAMING_SNAKE, values camelCase,
matching the file's convention):

```kotlin
// Progression
const val PROGRESSION_ENABLED = "progressionEnabled"          // Boolean, default true
const val MANA_BALANCE = "manaBalance"                        // Long
const val MANA_LEDGER = "manaLedger"                          // List<ManaLedgerEntry> JSON
const val BOONS = "boons"                                     // List<Boon> JSON
const val TRIALS = "trials"                                   // List<Trial> JSON (active + recent)
const val UNLOCKED_SIGILS = "unlockedSigils"                  // Set<String> JSON
const val HIGHEST_STREAK_MILESTONE_PAID = "highestStreakMilestonePaid" // Int
const val EXTRA_BREAK_TOKENS = "extraBreakTokens"             // Int (session-scoped, cleared on stop)
const val LAST_WRAPUP_DATE = "lastWrapupDate"                 // String yyyyMMdd
const val WRAPUP_ENABLED = "wrapupEnabled"                    // Boolean, default true
const val TRIAL_ALERTS_ENABLED = "trialAlertsEnabled"         // Boolean, default true
```

Caps beside the other `MAX_*` in `Constants`: `MAX_BOONS = 50`,
`MAX_MANA_LEDGER = 300`. The ledger prunes with `takeLast` (like `FOCUS_SESSIONS`);
boons reject at cap (like `MAX_SCHEDULES`). Trials never accumulate — the list is
replaced on rotation.

New channel id: `PROGRESSION_CHANNEL_ID = "focus_pocus_progression"`.

### 3.3 Models (`model/Progression.kt`, `model/Trial.kt`)

```kotlin
data class ManaLedgerEntry(
    val timestampMillis: Long,
    val amount: Long,            // + earned, - spent
    val reason: String           // "Focus · 90 min", "Boon: Game night", "Trial: Cast 3 spells"
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
    val periodKey: String,       // "yyyyMMdd" or "yyyy-Www" (ISO week, Monday start)
    val rewardMana: Long,
    val claimed: Boolean = false
)

data class Sigil(val id: String, val titleRes: Int, val descriptionRes: Int)
```

Trial titles are derived from `(type, target)` via string resources, not stored — keeps
persisted data locale-independent.

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

**Hook.** Inside `SessionRecorder.record()`, after `newStreak` is computed and inside
the `@Synchronized` scope, call a static `Progression.awardForSession(prefs, gson,
session, newStreak)` (same `object` style as the recorder). It updates
`MANA_BALANCE`, appends a `ManaLedgerEntry`, advances session-driven trials, evaluates
sigils/milestones, and returns what happened. All of it no-ops when
`PROGRESSION_ENABLED` is false. It must sit **after** the existing <1-minute early
return so discarded sessions never award.

**Return type change.** `record()` currently returns `List<FocusSession>`. Change to:

```kotlin
data class RecordResult(
    val sessions: List<FocusSession>,        // pruned list (existing return value)
    val recorded: FocusSession? = null,      // null when discarded (<1 min / no start time)
    val manaEarned: Long = 0,
    val newStreak: Int = 0,
    val completedTrials: List<Trial> = emptyList()  // newly completed, unclaimed
)
```

Thread it through `SessionManager.stopSession()` (currently returns `Unit` → return
`RecordResult`) and `SessionRepository.stopSession()`. Callers that ignore it (service,
TriggerHandler) need no changes beyond the type; `SessionViewModel` uses it for the
enriched dialog (§5.1), and `SessionManager.stopSession()` posts the trial-completion
notification (§5.2) since it has the `Context` that `record()` lacks. Update the 8
existing `SessionRecorderTest` cases for the new return type.

### 3.5 Notification channels

Create **both** channels in `FocusPocusApplication.onCreate` (idempotent API):
- Rituals (move the existing creation here; keep the service-side call as a safety
  net — `createNotificationChannel` is safe to call twice).
- Progression: `PROGRESSION_CHANNEL_ID`, name "Progression", description "Mana, trials
  and daily wrap-ups", `IMPORTANCE_LOW` — celebrates without buzzing, and users can
  mute gamification without losing ritual alerts.

### 3.6 Repository and DI

`data/ProgressionRepository.kt`, constructor `(prefs, gson)` like `InsightsRepository`:
balance/ledger/boons/trials/sigils reads via `PrefsHelper.load ?: empty`, plus:

```kotlin
fun redeemBoon(boon: Boon): Boolean            // balance check, deduct, ledger entry
fun redeemPerk(perk: Perk, arg: String?): Boolean  // §4.3; arg = packageName for sealed minutes
fun claimTrial(trialId: String): Long          // credits rewardMana, marks claimed, ledger entry
fun rolloverIfNeeded()                         // §4.4 trial rotation, date-key compared on read
```

Register `val progression = ProgressionRepository(prefs, gson)` in `AppContainer`.
Expose state through a new `ProgressionViewModel` (AndroidViewModel, same
`MutableStateFlow(repo.getX()).asStateFlow()` pattern), refreshed from the existing
`dataVersion` LaunchedEffect plus after every stop/claim/redeem.

## 4. Feature specs

### 4.1 Mana economy

Earning: sessions only (per §3.4), plus trial claims and sigil unlock bonuses. All
earning routes through `Progression` inside the `@Synchronized` recorder or the
repository's synchronized claim methods — one writer discipline, no double-award.

Spending: boons and perks. Balance can never go negative; redeem methods return false
and the UI disables buttons below cost (same pattern as cap-rejecting repos).

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

- **Extra break token.** Redeemable only during an active session when
  `breaksUsed >= effectiveMaxBreaks`. Increments `EXTRA_BREAK_TOKENS`; the UI gate at
  `FocusPocusApp.kt:477` becomes
  `breaksUsedThisSession < effectiveMaxBreaks + extraBreakTokens`. The Pomodoro
  auto-break gate (`MyAccessibilityService.kt:492-495`) does **not** consume tokens —
  they're for deliberate, user-initiated breaks. Cleared in
  `SessionManager.stopSession()`'s existing prefs-clearing editor.
- **Sealed-app minutes.** From the Boons screen, pick one app that has a pact
  (reuse `AppPicker` filtered to pacted apps); redeem calls
  `PactManager.grantAllowance(packageName, 10)`. The pact overlay and enforcement
  already honor allowances — zero enforcement-side changes. Cap: one redemption per
  app per day (checked against the ledger) so mana can't quietly dissolve a pact.

Both perks live at the bottom of the Boons screen under a "Perks — spend mana in-app"
`SectionHeader`, visually separated from honor boons.

### 4.4 Trials

**Active set:** 2 daily + 1 weekly. Rotation happens in `rolloverIfNeeded()`, called
from `ProgressionViewModel.init` and on each `dataVersion` refresh — it compares each
trial's `periodKey` to the current day/ISO-week key (persisted-state-compared-on-read,
the `OpenReflexTracker` pattern; **not** the service's in-memory reset marker). On
rotation: completed-but-unclaimed trials are auto-credited with a ledger note
("Trial expired — auto-claimed"), then the slate is redrawn from a code-defined
template list, seeded by the periodKey's hashCode so the same day always draws the same
trials (deterministic, testable, no `Random` in persistence paths).

**Templates (launch set):**

| Type | Example targets | Period | Reward |
|---|---|---|---|
| COMPLETE_SESSIONS | 2–3 sessions | daily | 40 |
| FOCUS_MINUTES | 60–120 min | daily | 50 |
| COMPLETE_RITUAL | 1 scheduled ritual completed | daily | 40 |
| STAY_UNDER_LIMITS | all app limits respected | daily | 60 |
| FOCUS_MINUTES | 300–600 min | weekly | 200 |
| NO_REFLEX_OPENS | ≤ N reflex opens | weekly | 150 |

**Evaluation:**
- Session-driven types update inside `Progression.awardForSession()` (already in the
  synchronized scope). A trial crossing `progress >= target` lands in
  `RecordResult.completedTrials`.
- Usage-driven types (`STAY_UNDER_LIMITS`, `NO_REFLEX_OPENS`) can only be judged when
  the day is over: they're evaluated during rollover for the *previous* periodKey,
  using `AppTimeLimitManager.getAllUsedMinutesToday`-equivalent queries bounded to that
  day and `OpenReflexTracker.getDailyStats()`. If usage-stats permission is missing,
  these templates are excluded from the draw (never show a trial that can't be
  measured).

**Claiming:** Claim button on the trial card credits `rewardMana`. Completion detected
at session end also posts the trial-alert notification (§5.2) when enabled.

### 4.5 Sigils and streak milestones

Code-defined catalog (`SigilCatalog.ALL`), persisted state is only the unlocked-id set.
Launch catalog (~12): first session; first ritual completed; first pact honored a full
day; 10-hour focus week; 100 total sessions; Warden provisioned; streak milestones
7/30/100 ("Seven Nights", "Thirty Nights", "Hundred Nights"); first boon redeemed;
1 000 lifetime mana; hidden-stop-button session completed.

- Evaluation is idempotent (set-add) and runs in `Progression.awardForSession()` for
  session/streak-driven sigils; the Warden sigil unlocks from `DeviceOwnerManager`'s
  provisioning success path; boon/mana sigils from the repository's redeem/claim
  methods.
- **Streak milestones double as the celebration moment:** unlocking 7/30/100 grants
  bonus mana (100/500/2000) guarded by `HIGHEST_STREAK_MILESTONE_PAID` so a streak
  that re-crosses 7 after a break pays again only above the historical high-water mark.
  The milestone also gets a line in the enriched end-dialog ("🎉 30-day streak —
  Thirty Nights unlocked, +500 mana").

## 5. Tone: feedback surfaces

### 5.1 Enriched end-of-session dialog

The existing summary dialog (`SessionViewModel._showSessionSummary`, rendered in
`FocusPocusApp.kt:687-774`) gains: mana earned this session (with bonus breakdown on
one line), current streak, any trial completions ("Trial complete — claim in
Insights"), and any sigil/milestone unlocks. Data comes from the `RecordResult` now
returned through `stopSession()`. Note the current flow captures summary fields
*before* stopping (`stopSessionWithSummary`) — rework so the dialog is populated from
the `RecordResult` *after* the stop call instead. No system notification on foreground
stops (decided: avoid double feedback).

### 5.2 Trial-completion notification

Posted from `SessionManager.stopSession()` when `RecordResult.completedTrials` is
non-empty and `TRIAL_ALERTS_ENABLED` — "Trial complete: Cast 3 spells — claim your 40
mana". Progression channel, auto-cancel, content intent to MainActivity. For
usage-driven trials completed at rollover, the claim just appears in the UI (no
notification — rollover happens at app-open time anyway, when the user is already
looking).

### 5.3 Daily evening wrap-up (best effort)

In `MyAccessibilityService.onMinuteTick()`: if `WRAPUP_ENABLED` and
`PROGRESSION_ENABLED`, local time ≥ 20:00, `LAST_WRAPUP_DATE != todayString()`, and
today has ≥ 1 recorded focus session — post "You reclaimed 2h 15m today · +180 mana ·
12-day streak" on the Progression channel, then persist `LAST_WRAPUP_DATE`.
Persisted-date guard means a service restart at 21:30 still sends it (next tick),
while a fully-down service skips the day silently. Suppressed entirely on inactive
days so it never scolds. Explicitly accepted as best-effort — no WorkManager
dependency is introduced for this.

## 6. UI integration

### 6.1 Home (`Greeting` in HomeScreen.kt)

- Idle state: a **mana chip** next to the existing `StreakBadge` AssistChip (same
  row, `AutoAwesome` icon, tertiary/gold accent, "420 mana"). Tapping opens the Boons
  screen. Hidden when progression is off.
- Below Session Setup: a compact **"Today's Trials"** `GlassCard` — one row per active
  daily trial (title, thin progress bar, "2/3"), Claim button inline when complete.
  Weekly trial included collapsed. Keeps Home motivational without clutter.

### 6.2 Insights (`UsageStatsScreen` in InsightsScreen.kt)

New sections after "Focus Streaks":
- **"Mana — focus energy"**: `StatTile` row — balance / earned this week / sigils
  unlocked count; a "Manage boons" button opening the Boons screen; last ~5 ledger
  entries in a `GlassCard`.
- **"Trials — daily & weekly challenges"**: full trial cards with progress bars and
  Claim buttons (the canonical claim surface).
- **"Sigils — milestones you've earned"**: 3-per-row grid of `GlassCard` tiles;
  locked ones dimmed with their unlock hint. Unlocked show a gold glyph.

### 6.3 Boons screen

Full-screen overlay following the Settings pattern (a `showBoons` boolean in
`FocusPocusApp` with early return, wrapped in `ArcaneBackground`) — honors the
"Home + Insights, no new nav" decision while giving CRUD room. Contents: balance
header, boon list (`GlassCard` per boon: title, cost, Redeem button disabled below
balance), create/edit editor modeled on the Quick Spell editor, Perks section (§4.3).

### 6.4 Settings

New "Progression" card between Focus Behavior and Notifications:
- Master toggle "Progression — mana, trials & sigils" (default **on**; raw-primitive
  getter `getProgressionEnabled` default true in `SettingsRepository`). Turning it off
  hides all surfaces and stops awards but **preserves** balance/ledger/sigils for
  re-enable.
- Sub-toggles (enabled only when master is on): "Daily wrap-up notification",
  "Trial completion alerts".

### 6.5 Strings

All user-facing strings in `values/strings.xml` **and** `values-fr/strings.xml`
(recent history shows FR lint is enforced). Trial titles are parameterized resources
("Cast %d spells today").

## 7. Testing

Pure-function unit tests (existing FakeSharedPreferences + JUnit4 setup):
- `ProgressionMathTest`: award formula — base, 240 cap, each multiplier, stacking,
  rounding, zero-duration.
- `TrialEngineTest`: template draw determinism per periodKey, progress advancement per
  type, completion detection, rotation with auto-claim of unclaimed completions,
  permission-gated template exclusion, ISO-week key edges (year boundary).
- `SigilTest`: idempotent unlock, milestone high-water-mark guard (re-crossing 7 after
  100 pays nothing).
- `ProgressionRepositoryTest`: redeem below balance fails, ledger prune at 300, boon
  cap rejection, perk once-per-app-per-day guard.
- Update `SessionRecorderTest` (8 cases) for `RecordResult`; add cases: award written
  atomically with session, no award when `PROGRESSION_ENABLED=false`, no award on
  <1-min discard.
- Update `SessionManagerTest` for the new return type.

## 8. Build order

1. **Foundation** — models, prefs keys, `ProgressionMath`, `Progression.awardForSession`
   hook, `RecordResult` threading, channels in Application, Settings master toggle,
   `ProgressionRepository` + `ProgressionViewModel`, mana chip on Home, Mana section on
   Insights. *Ships alone: earning visibly works.*
2. **Boons** — Boons screen, CRUD, honor redemption, ledger UI.
3. **Trials** — templates, rotation, session-driven evaluation, Home/Insights cards,
   claim flow, completion notification; usage-driven templates last within this phase.
4. **Sigils + milestones** — catalog, evaluation sites, Insights grid, enriched-dialog
   unlock lines.
5. **Perks** — extra break token (UI gate change), sealed-app minutes
   (`grantAllowance` wiring, per-day guard).
6. **Wrap-up notification** + final dialog polish.

Each phase is independently shippable and gated by the same master toggle.

## 9. Risks & mitigations

- **Prefs size / main-thread writes.** One prefs file already holds ≤500 sessions +
  ≤1000 block events; every `apply()` rewrites the whole file. Mitigation: ledger
  capped at 300, trials list replaced not accumulated, sigils are a small id set. No
  per-scroll or per-tick writes anywhere in this plan.
- **Double feedback.** Foreground stops get the dialog only; notifications are
  reserved for trial completions and the wrap-up. If a ritual ends in the background,
  the existing ritual-end notification already covers it — we deliberately do not add
  a second one.
- **POST_NOTIFICATIONS denial.** Requested via legacy `requestPermissions` with no
  follow-up; if denied, trial alerts and wrap-ups vanish silently. The core loop
  (earn → see balance → redeem) works entirely in-app, so nothing critical is
  notification-dependent. A Settings hint ("notifications disabled at system level")
  is a cheap phase-6 add.
- **Economy inflation.** All rates live in one constants block; the ledger makes
  drift visible. Honor-system stance: tune for feel, not fraud-proofing.
- **`record()` signature ripple.** `RecordResult` touches SessionManager,
  SessionRepository, SessionViewModel and two test files — mechanical but must land
  as one commit (phase 1) to keep the build green.
- **Analytics (optional, deferred).** If earn/redeem/claim events are ever wanted,
  they must respect `ANALYTICS_CONSENT` (default true, opt-out) — note there is no
  event-logging helper today; that would be new surface, deliberately out of scope.

## 10. Open tunables (defaults chosen, revisit after use)

- Award constants: 1 mana/min, 240/session cap, ×1.25 hidden-stop, +2%/streak-day
  (≤+50%), +20 ritual flat.
- Perk prices: extra break 50, 10 sealed minutes 150; sealed-minutes limited to one
  redemption per app per day.
- Trial rewards: 40–60 daily, 150–200 weekly; 2 daily + 1 weekly active.
- Milestone bonuses: 100/500/2000 mana at 7/30/100 days.
- Wrap-up trigger time: 20:00 local.
