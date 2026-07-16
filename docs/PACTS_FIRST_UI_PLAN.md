# Focus Pocus — Pacts-First UI Restructure Plan

A codebase-verified plan for making Pacts the default way Focus Pocus is set up and
used. Today the app's front door is the session caster ("Ready to Cast" + the big
orb), and the always-on protection — Pacts — lives two levels deep in the Spellbook.
This plan inverts that: the first screen becomes a standing-protection dashboard,
sessions move to their own tab, and onboarding builds a first pact instead of a
first blocklist.

Every integration point below was checked against the code at `271d8a9`. Four
shaping decisions were made with the project owner before writing (see §1, rows
marked ✔).

---

## 1. Decision record

| Decision | Choice |
|---|---|
| ✔ Default screen | **Guard dashboard**: pact'd apps with live seal/allowance state and today's open/reflex counts, plus a compact banner when a focus session or ritual is active. Casting is not on the default screen. |
| ✔ Cast flow | Focus sessions keep their **own tab** ("Focus"), nearly identical to today's Home screen. 4 tabs total: Pacts · Focus · Spellbook · Insights. |
| ✔ Onboarding | **Pact-first**: the "create an enchantment" step is replaced by a "seal your first apps" step. Sessions remain discoverable on the Focus tab. |
| ✔ Time Limits | **Unified with Pacts** into one guard surface. One list, one editor; each guarded app is either a *Pact* (sealed by default) or a *Ward* (time limit). The two old screens and their add dialogs are superseded. |
| Guard editor form | Full-screen editor (like `ScheduleEditorScreen`), not another overloaded `AlertDialog`. Fixes today's remove-and-re-add-only management (§2, gap G1). |
| Naming | Tab label **"Pacts"** (the loved brand is the front door). Time limits are renamed **"Wards"** with a plain subtitle everywhere ("a daily time limit"), matching the repo convention of arcane names + plain subtitles. Fallback if Ward reads as noise in practice: keep "Time limit" as the style label; nothing else in this plan changes. |
| Pacts tab icon | `Icons.Filled.Shield` (always-on protection). `AutoFixHigh` (the wand) moves to the Focus tab, which is where casting lives now. |
| Existing users | No data migration (§7). One-time "the front door moved" dialog, same pattern as the progression intro dialog (`showProgressionIntroDialog`, FocusPocusApp.kt:294). |
| Mana / streak chips | Move to the new Home header (the dashboard is the wallet's home; Boons already sell pact-related perks). The Focus tab keeps the Trials card — trials are claimed where casting happens. |
| Onboarding strictness | The first-pact step is **required** (≥1 app sealed), same strictness as today's blocker step (OnboardingScreen.kt:209). A pact can be deleted in two taps afterwards; the wizard is not the place to opt out of the product's core. |
| Insights | Optional phase only: promote the existing App Opens section when guards exist. No new Insights features in this plan. |
| Group wards | **Not supported.** `PactGroup` stays pact-only (as in the data model today). A ward needs per-app usage arithmetic; "one daily cap shared by a live enchantment" is ambiguous and out of scope. The editor greys the Ward style out for enchantment targets. |

## 2. Verified codebase grounding

Facts this plan depends on, confirmed in code:

- **Tabs & default.** `AppDestinations` has exactly three entries — HOME ("Focus",
  `AutoFixHigh`), SPELLBOOK, INSIGHTS (AppDestinations.kt:10-17) — rendered by
  `NavigationSuiteScaffold` (FocusPocusApp.kt:475). The default tab is
  `rememberSaveable { mutableStateOf(AppDestinations.HOME) }` (FocusPocusApp.kt:328).
  Adding a fourth enum entry is safe for state restoration (enums are saved via
  Bundle serialization by name, and the saveable holds the value, not an ordinal).
- **Home is the session caster.** The HOME branch renders `Greeting`
  (FocusPocusApp.kt:526), which is idle-vs-active session UI end to end
  (HomeScreen.kt:144-197): "Ready to Cast" headline, mana/streak chips, `CastSpellButton`,
  Quick Spell chips, session setup card, Trials card, QR button — or timers, breaks,
  emergency stop, dispel when active.
- **Pacts and Time Limits are two lenses over one store.** Both screens read
  `appTimeLimitConfigs`; `PactsScreen` filters `pactModeEnabled` (PactsScreen.kt:76),
  `TimeLimitsScreen` filters the complement (TimeLimitsScreen.kt:70). Group pacts
  (`PactGroup`) bind pact settings to a blacklist enchantment by name and are stored
  separately (`PACT_GROUPS`, PactManager.kt:64-81). Precedence: an explicit per-app
  config always wins over group membership — enforced in
  `resolvePactConfig` (MyAccessibilityService.kt:1471-1473) and mirrored in the
  Boons wiring (FocusPocusApp.kt:430-439). The unified dashboard must reproduce
  exactly this precedence when deduping group members.
- **Gap G1 — no editing.** Neither screen can edit a guard: rows have only a Remove
  button (PactsScreen.kt:174-180, TimeLimitsScreen.kt:175-181); changing a setting
  means delete + re-add. The per-app pact dialog also cannot set seal escalation at
  all (AddPactDialog, PactsScreen.kt:270-534 — no escalation field), even though the
  model and enforcement support it (`AppTimeLimit.cooldownEscalationEnabled`,
  SessionCooldownManager.kt:94-96).
- **Live state for a dashboard is all queryable, UI-side, today:**
  - Active pact allowance: `PactManager.getAllowanceExpiry(pkg)` (PactManager.kt:41);
    UI-side construction of `PactManager` is established practice
    (SpellbookViewModel.kt:44, OverlayActivity.kt:158).
  - Seal / cooldown: `SessionCooldownManager.getCooldownState(pkg)`
    (SessionCooldownManager.kt:73) plus `minutesRemaining` (:190). **Caveat:** this
    accessor lazily *writes* prefs to prune expired entries (:76-80). The dashboard
    polls; it must not write. Add a read-only `peekCooldownState` (§4.1) instead of
    reusing the pruning accessor from the UI process.
  - Today's opens/reflexes: `OpenReflexTracker.getAllStats()` (OpenReflexTracker.kt:73),
    already exposed as `SpellbookViewModel.getTodayOpenStats()` (SpellbookViewModel.kt:47).
  - Ward usage: `AppTimeLimitManager.getUsedMinutesToday(context, pkg)` — already
    called from composition for Boons gating (FocusPocusApp.kt:452-453) and per-row in
    TimeLimitsScreen (TimeLimitsScreen.kt:131-133).
- **Session state for the Home banner** is already hoisted in `FocusPocusApp`:
  `focusMode` (:325), `activeBlockerNames`, `activeSchedule` (:336),
  `focusTimeRemaining`, `isOnBreak`, `breakTimeRemaining` — no new plumbing, just new
  consumers.
- **Spellbook sub-navigation pattern to copy:** routes are a sealed class
  (SpellbookRoute.kt) driven by `SpellbookViewModel.navigateTo`/`handleBack`
  (SpellbookViewModel.kt:142-160), with a `BackHandler` armed in `FocusPocusApp`
  when the tab is SPELLBOOK and the route isn't Overview (FocusPocusApp.kt:467-469).
  The Pacts/TimeLimits entries there (SpellbookRoute.kt:22-24) are the ones this plan
  removes.
- **Spellbook overview** already groups Pacts + Time Limits as "Everyday guards"
  (SpellbookScreen.kt:86-129) — the mental model this plan promotes to the front
  door. Its Pacts card computes the same group-membership rollup the dashboard needs
  (SpellbookScreen.kt:242-247).
- **Onboarding** is a 7-step wizard (OnboardingScreen.kt:88, steps at :157-188).
  Step 2 (`CreateBlockerStep`) hard-gates on `blockerLists.isNotEmpty()` (:209).
  Deleting an enchantment already cleans up its pact group
  (SpellbookViewModel.kt:174-183), so onboarding-created pacts have no orphan risk.
- **Caps and cleanup:** `MAX_APP_TIME_LIMITS = 100`, `MAX_BLOCKERS = 50`
  (Constants.kt:137,143); `saveAppTimeLimitConfig` already toasts on overflow
  (SpellbookViewModel.kt:254-259). Cross-VM refresh after any mutation is wired via
  `dataVersion` (FocusPocusApp.kt:202-208).
- **i18n:** a near-complete French locale exists (`values-fr/strings.xml`, 559/565
  strings). Every new string in this plan ships in both files.
- **CI gates:** `testDebugUnitTest`, `lintDebug`, `assembleDebug`
  (.github/workflows/ci.yml). No Compose UI tests exist; the testing story is unit
  tests for extracted logic + a manual checklist (§9).
- **Known adjacent wrinkle (not caused by this plan):** the Conditional Unlocks
  screen receives `pactPackages` from explicit configs only
  (FocusPocusApp.kt:775) — pact-group members are missing. §8 Phase 3 fixes this in
  passing since the wiring is being touched anyway.

## 3. Information architecture

### 3.1 Before → after

```
BEFORE (3 tabs)                          AFTER (4 tabs)
┌────────────────────────────┐          ┌────────────────────────────┐
│ Focus (default)            │          │ Pacts (default)   [Shield] │
│   idle: Ready to Cast, orb │          │   guard dashboard: seals,  │
│   active: timers, breaks   │          │   allowances, opens/reflex │
│                            │          │   + active-session banner  │
│ Spellbook                  │          │   + guard editor sub-route │
│   Everyday guards:         │          │                            │
│     Pacts ──▶ PactsScreen  │          │ Focus              [Wand]  │
│     Time Limits ──▶ screen │          │   today's Greeting, intact │
│   Focus sessions:          │          │                            │
│     Enchantments, Rituals  │          │ Spellbook          [Book]  │
│   More magic:              │          │   Focus sessions:          │
│     Quick Spells,          │          │     Enchantments, Rituals  │
│     Talismans, Cond.Unlocks│          │   More magic:              │
│                            │          │     Quick Spells,          │
│ Insights                   │          │     Talismans, Cond.Unlocks│
└────────────────────────────┘          │                            │
                                        │ Insights                   │
Settings / Boons stay full-screen       └────────────────────────────┘
overlays over everything (unchanged).
```

### 3.2 Naming glossary (user-facing)

| Term | Plain subtitle (shown in UI) | Backing data |
|---|---|---|
| Pact | "Sealed by default — opening asks how long you need" | `AppTimeLimit(pactModeEnabled = true)` or `PactGroup` |
| Ward | "A daily time limit — open freely until it runs out" | `AppTimeLimit(pactModeEnabled = false)` |
| Seal | The cooldown after a lapsed pact or exceeded ward | `CooldownState` |
| Pact circle | A pact bound to a whole enchantment (live membership) | `PactGroup` |

"Guard" remains an internal/code umbrella term (`GuardStyle`, `GuardStatus`); it
appears in UI copy only sparingly ("Everyday guards" is retired along with the
Spellbook section that used it).

## 4. Screen specs

### 4.1 New Home: the Pacts dashboard (`ui/screens/PactsHomeScreen.kt`)

```
┌──────────────────────────────────────┐
│ Pacts                            ⚙   │  ← top bar (existing scaffold)
│                                      │
│ 3 apps sealed · 1 pact active        │  ← status headline (dynamic)
│ Today: 14 opens · 6 reflexes caught  │  ← rollup, tertiary color
│ [✦ 120 mana] [🔥 4-day streak]       │  ← chips, as on today's Home
│                                      │
│ ╭──────────────────────────────────╮ │
│ │ ● Deep Work active — 42:10 left  │ │  ← session banner, only when
│ ╰──────────────────────────────────╯ │    focusMode; tap → Focus tab
│                                      │
│ ╭──────────────────────────────────╮ │
│ │ [icon] Instagram          SEALED │ │  ← guard card (pact, sealed)
│ │ Pact · 15 m max · seals 30 m     │ │
│ │ Seal lifts in 22 m               │ │
│ ╰──────────────────────────────────╯ │
│ ╭──────────────────────────────────╮ │
│ │ [icon] YouTube       PACT ACTIVE │ │  ← pact with live allowance
│ │ Pact · 7 m left of 10 m          │ │
│ │ 9 opens · 4 reflexes today       │ │
│ ╰──────────────────────────────────╯ │
│ ╭──────────────────────────────────╮ │
│ │ [⛓] Doomscroll (6 apps)    QUIET │ │  ← pact circle (group)
│ │ Pact circle · 15 m max · 30 m    │ │
│ │ 2 of 6 sealed · 11 opens today   │ │
│ ╰──────────────────────────────────╯ │
│ ╭──────────────────────────────────╮ │
│ │ [icon] TikTok           38/45 m  │ │  ← ward, near limit
│ │ Ward · daily limit · ▓▓▓▓▓▓▓░░   │ │
│ ╰──────────────────────────────────╯ │
│                                      │
│ [        + Make a pact         ]     │  ← primary CTA → editor
│                                      │
│ (Pacts)  (Focus)  (Spellbook) (Ins.) │  ← 4-item nav bar
└──────────────────────────────────────┘
```

**Status headline** picks the most urgent truth: *n apps sealed* > *n pacts
active* > *All quiet* (no guard currently sealed or on an allowance). With zero
guards it's the empty state below. The rollup line reuses the Spellbook card's
arithmetic (SpellbookScreen.kt:242-247): opens/reflexes summed over explicit pact
packages ∪ live group members (ward-only apps excluded from the reflex rollup —
they aren't reflex-tracked).

**Session banner** renders when `focusMode` is true: ritual name or joined blocker
names, countdown (`focusTimeRemaining` formatted with the existing `formatClock`),
break state when `isOnBreak`. Tapping sets `currentDestination = FOCUS`. It is
deliberately status + link only — dispel/break actions stay on the Focus tab to keep
one source of truth for session controls.

**Guard cards**, one per explicit config plus one per pact circle. Group member
dedup follows enforcement precedence: an app with an explicit config renders as its
own card and is excluded from its group's card counts. Card anatomy: app icon
(existing `AppIcon` component) or a group glyph with member count; name; style line
with plain subtitle values; live state line; trailing state chip. State resolution
per app, in priority order:

1. `SEALED` — `peekCooldownState(pkg) != null` → "Seal lifts in Xm"
   (`minutesRemaining`).
2. `PACT ACTIVE` — `getAllowanceExpiry(pkg) != null` → "Xm left of Ym".
3. Ward over limit → `OVER LIMIT` (error color), else used/limit + progress bar
   (visuals lifted from TimeLimitsScreen.kt:136-183).
4. `QUIET` — today's "n opens · m reflexes" (or nothing yet today).

Group card state summarizes members: "k of n sealed", else "pact active on k", else
quiet + aggregate opens. Card order: SEALED first, then PACT ACTIVE, then
OVER LIMIT, then quiet by today's opens descending, name as tiebreaker — the app
you're wrestling with today floats up. Tap → editor prefilled (§4.2).

**Empty state** (no configs, no groups): shield sigil hero (reuse the
`StepHero`/sigil treatment from onboarding/overlay), copy in the voice of
`pacts_empty` ("Seal your most distracting app and watch the reflex count drop"),
one primary button "Make your first pact" → editor. A quiet secondary line points
at the Focus tab for session-based blocking so existing mental models aren't lost.

**Data & refresh.** A small `GuardStatusViewModel` is *not* introduced;
`SpellbookViewModel` already owns every input (configs, groups, blockers, installed
apps, open stats) plus `dataVersion` refresh wiring. Add to it:

- `fun getGuardLiveState(): Map<String, GuardLiveState>` — snapshot combining
  allowance expiry, cooldown state, and used-minutes; same call-on-demand style as
  `getTodayOpenStats()` (SpellbookViewModel.kt:47).
- The composable recomputes via `remember(dataVersion, tick)` where `tick` is a
  60-second `LaunchedEffect` counter active only while HOME is the current
  destination, plus a `LifecycleResumeEffect` bump on foreground return. Countdown
  labels are minute-granular, matching the pact overlay's 30 s tick precedent
  (OverlayActivity.kt:217-223); a per-second dashboard clock is deliberately avoided.
- New `SessionCooldownManager.peekCooldownState(pkg)` / `peekAll()` : read-only
  variants that never prune, so the UI process never races the service's
  authoritative writes (§2 caveat). Enforcement keeps using the pruning accessor.

`GuardLiveState` and the pure resolution/ordering function live in a new
`limit/GuardStatus.kt` so they're unit-testable without Android (§9).

### 4.2 Unified guard editor (`ui/screens/GuardEditorScreen.kt`)

Full-screen editor replacing both `AddPactDialog` (PactsScreen.kt:270) and
`AddTimeLimitDialog` (TimeLimitsScreen.kt:203), reached from the dashboard CTA
(create) or a guard card (edit). Layout follows `ScheduleEditorScreen` conventions.

```
Target        ( • One app         ○ Whole enchantment )
              [ app picker / blacklist dropdown ]        ← locked when editing
Style         ( • Pact — sealed by default
                ○ Ward — a daily time limit )            ← Ward disabled for
                                                           enchantment targets
── Pact fields ──────────────────────────────────────
Longest pact        [ 5 / 10 / 15 / 30 min ]
Seal duration       [ 15 / 30 / 45 / 60 / 90 min ]
Escalating seals    [toggle]  step [ +15 min ]           ← NEW in UI for pacts
Healthier substitute[ app picker · optional ]
Daily backstop      [ none / 30 / 60 / 120 / 240 min ]
── Ward fields ──────────────────────────────────────
Daily limit         [ existing daily options ]
Session cooldown    [toggle] session [10…] cooldown [30…]
Escalating cooldowns[toggle]  step [ +15 min ]
─────────────────────────────────────────────────────
[ Save ]   [ Cancel ]   ( Delete — edit mode, confirm dialog )
```

Semantics:

- Save writes exactly what today's two dialogs write: an `AppTimeLimit` (with
  `pactModeEnabled` per style) via `saveAppTimeLimitConfig`, or a `PactGroup` via
  `savePactGroup`. No new fields, no model changes.
- **Style conversion** (edit mode, app targets): flipping Pact↔Ward rewrites the
  same config with the other flag and zeroes the fields the new style doesn't use
  (`sessionLimitMinutes` for pacts — as AddPactDialog already does,
  PactsScreen.kt:516; `pactMaxMinutes`/`pactAlternativePackage` for wards). This
  finally makes conversion a one-screen operation instead of
  delete-here-recreate-there. Any live cooldown is left untouched — a seal is a
  seal under either style, and the service's next decision uses the new config.
- Editing a **pact circle** edits the `PactGroup`; the member list itself is still
  managed on the enchantment (live membership is the feature). A caption links to
  the enchantment editor in the Spellbook.
- Availability rules carried over: apps already guarded are excluded from the
  create picker (PactsScreen.kt:297); blacklists already carrying a group are
  excluded from the enchantment dropdown (:298-300); whitelist enchantments never
  appear.
- Escalation exposed for both styles closes gap G1's second half — pact seals could
  escalate in enforcement but no UI could switch it on for per-app pacts.

### 4.3 Focus tab (today's `Greeting`, demoted not diminished)

- New `AppDestinations.FOCUS` renders `Greeting` with the exact prop wiring
  currently in the HOME branch (FocusPocusApp.kt:512-606). Timers, breaks,
  emergency stop, NFC lock, hide-stop-button, QR scanning: untouched.
- Header slimmed: the mana chip and streak badge move to the Pacts dashboard
  (HomeScreen.kt:237-251 block); "Ready to Cast" and the Trials card stay.
- **New empty-state affordance:** when `blockerLists.isEmpty()`, the hint under the
  disabled cast orb (HomeScreen.kt:261-269) becomes a button — "Create an
  enchantment" — that jumps to Spellbook → create (new `onCreateEnchantment`
  callback: sets tab to SPELLBOOK + `navigateTo(SpellbookRoute.CreateEnchantment)`).
  Today that hint points at a dropdown that can be empty — after pact-first
  onboarding it *usually* will be, so this stops being cosmetic.

### 4.4 Spellbook

- The "Everyday guards" group — header, `PactsSectionCard`, Time Limits card
  (SpellbookScreen.kt:86-129) — is removed; the overview becomes "Focus sessions"
  (Enchantments, Rituals) + "More magic" (Quick Spells, Talismans, Conditional
  Unlocks). `SpellbookRoute.Pacts` / `.TimeLimits` and their `FocusPocusApp`
  branches (:747-768) are deleted; `PactsScreen.kt` and `TimeLimitsScreen.kt` are
  deleted once the editor and dashboard cover them (Phase 3).
- The enchantment editor gains one passive caption when a pact circle is bound to
  it ("This enchantment carries a pact circle — every app here is sealed"), so the
  coupling `deleteBlocker` already handles (SpellbookViewModel.kt:174-183) is
  visible before deletion, and the existing silent group cleanup stops being a
  surprise.

### 4.5 Insights (optional, Phase 5)

When any guard exists, move the existing App Opens section (opens + reflex
breakdown, day/week/month) directly below Streaks. No new charts. Everything else
unchanged.

### 4.6 Onboarding (pact-first)

Seven steps stay seven; only step 2 changes shape and the copy retunes:

| # | Step | Change |
|---|---|---|
| 0 | Welcome | Copy: lead with sealed-by-default ("Your most distracting apps, sealed until *you* choose to open them"), sessions second. |
| 1 | Accessibility | Mechanics unchanged; copy mentions sealing rather than only focus sessions. |
| 2 | **First pact** (was: create enchantment) | Multi-select app picker (existing `AppPickerDialog`), suggested defaults surfaced as read-only chips ("15 m max pact · 30 m seal — tune later on the Pacts tab"). Saving creates one `AppTimeLimit(pactModeEnabled = true, pactMaxMinutes = 15, cooldownMinutes = 30)` per picked app via a new `onCreateFirstPacts(packages: List<String>)` callback → `saveAppTimeLimitConfig` per package. Gate: ≥1 app picked (parity with :209). Selection capped defensively at `MAX_APP_TIME_LIMITS`. |
| 3–5 | DND, Usage stats, Analytics | Mechanics unchanged; usage-stats copy now also names the pact daily backstop as a beneficiary. |
| 6 | Done | Copy: "Your pacts are sealed." Button `onboarding_start_focusing` → "Begin" (nav lands on the Pacts dashboard, which now shows the freshly created pacts — the payoff frame). |

`OnboardingScreen`'s signature: `onSaveBlocker` is replaced by `onCreateFirstPacts`
(the blocker-creation UI inside the wizard goes away; `CreateBlockerStep` is deleted
with it). `blockerLists` stays a parameter only if step copy references it —
otherwise drop it.

**Existing users** (`onboardingCompleted == true`) never see the wizard; they get
the one-time "front door moved" dialog on first launch after update: *"Pacts are
now your home screen. Casting lives on the Focus tab, one tap away."* — new pref
`PACTS_HOME_INTRO_SHOWN`, shown-once logic copied from
`showProgressionIntroDialog` (FocusPocusApp.kt:294-305, SettingsViewModel).

## 5. Navigation & state plumbing

- `AppDestinations` becomes `HOME(R.string.nav_pacts, Icons.Filled.Shield)`,
  `FOCUS(R.string.nav_focus, Icons.Filled.AutoFixHigh)`, `SPELLBOOK`, `INSIGHTS` —
  order defines the bar. HOME stays the enum default so nothing else changes at the
  `rememberSaveable` site (FocusPocusApp.kt:328).
- Home sub-navigation copies the Spellbook pattern: `sealed class PactsRoute
  { Overview; CreateGuard; EditGuard(pkg); EditCircle(blockerName) }` held in
  `SpellbookViewModel` beside `spellbookRoute` (single owner of config-editing state;
  it already exposes every datum the editor needs). `BackHandler` armed when
  `currentDestination == HOME && pactsRoute !is Overview`, mirroring
  FocusPocusApp.kt:467-469. Route state is VM-held → survives rotation, resets on
  process death: exact parity with the Spellbook routes.
- Cross-tab jumps (session banner → FOCUS; Focus empty state → SPELLBOOK create)
  are plain lambdas from `FocusPocusApp`, which owns `currentDestination`; no
  navigation library is introduced.
- Deep links, NFC, QR (`TriggerHandler`, `MainActivity`) are tab-independent and
  untouched; the QR button stays on the Focus tab.

## 6. Strings & i18n

New keys (all shipped in `values/` **and** `values-fr/`):

- `nav_pacts`; dashboard: `home_guard_headline_sealed` (plural), `_pact_active`
  (plural), `home_guard_all_quiet`, `home_guard_rollup`, `home_session_banner`,
  `home_session_banner_break`, `home_guard_seal_lifts`, `home_guard_pact_left`,
  `home_guard_sealed_of` , `home_guard_make_pact`, `home_guard_empty_*` (title,
  body, cta, focus-pointer).
- Editor: `guard_editor_title_new/edit`, `guard_style_pact(+_desc)`,
  `guard_style_ward(+_desc)`, `guard_escalation_label/step`, delete-confirm pair;
  reuse `time_limits_*` field labels and `pacts_*` copy wherever the wording
  already fits (e.g. `time_limits_pact_max_label`, `pacts_backstop_label`).
- Onboarding: `onboarding_pact_title/desc/pick_apps/defaults_note`, retuned
  `onboarding_welcome_desc`, `onboarding_done_pacts`, `onboarding_begin`.
- Intro dialog: `pacts_home_intro_title/message/ok`.
- Retired with their screens: `pacts_title/description/empty/add`,
  `time_limits_title/description/add` (delete or repurpose — lint's
  `UnusedResources` will arbitrate; the repo keeps a lint baseline honest).
- Plurals where counts appear (`home_guard_headline_sealed`, member counts) — the
  repo already uses `pluralStringResource` (SpellbookScreen.kt:151-152).

## 7. What does NOT change

- **Zero data migration.** Same prefs keys, same models (`AppTimeLimit`,
  `PactGroup`, `CooldownState`, allowances, open stats). A downgrade or a
  feature-flag revert loses nothing.
- **Enforcement untouched:** `MyAccessibilityService`, `resolvePactConfig`,
  `PactManager` grant/lapse flow, `SessionCooldownManager` (apart from the additive
  read-only peek accessors), `TimeLimitChecker`, `OverlayActivity` + `PactOfferScreen`.
- Sessions engine: `SessionManager`, breaks, emergency stop, rituals, talisman
  locks, Warden mode, DND.
- Progression: mana/trials/sigils/boons engines; Boons stays a full-screen overlay
  reachable from the mana chip (now on the dashboard) and Insights.
- Settings screen, analytics consent, deep-link confirmation.

## 8. Build order

Each phase compiles, passes `testDebugUnitTest` + `lintDebug` + `assembleDebug`,
and is a shippable PR.

1. **Skeleton** — add FOCUS destination; move `Greeting` wiring to it; HOME renders
   a read-only first-cut dashboard (headline, static guard cards from existing
   snapshot functions, session banner); mana/streak chips relocate; `nav_pacts` +
   dashboard strings (en+fr); one-time intro dialog for existing users.
2. **Live dashboard** — `peekCooldownState`/`peekAll` + tests; `GuardStatus.kt`
   resolution/ordering (pure, tested); 60 s tick + resume refresh; empty state;
   card tap targets stubbed to the old screens (still reachable this phase).
3. **Unified editor & consolidation** — `GuardEditorScreen` (create/edit/convert/
   delete, circle editing); dashboard CTA + card taps target it; delete
   `PactsScreen`, `TimeLimitsScreen`, both add-dialogs, `SpellbookRoute.Pacts/
   TimeLimits`, Spellbook "Everyday guards" group; enchantment-editor pact-circle
   caption; fix Conditional Unlocks `pactPackages` to include group members.
4. **Onboarding & Focus empty state** — first-pact step, copy retune, Done→"Begin";
   Focus tab "Create an enchantment" CTA.
5. **Polish (optional)** — Insights App Opens promotion; copy pass; a11y sweep
   (contentDescriptions on state chips, 48 dp touch targets); dark/light contrast
   check on new chips/banners — this repo has shipped contrast regressions before
   (commits `b1b0d50`, `22f021a`), treat it as a first-class QA item.

## 9. Testing

**Unit (new):**
- `GuardStatusTest` — state priority (sealed > allowance > over-limit > quiet),
  group dedup honoring explicit-config precedence (assert identical outcome to
  `resolvePactConfig` semantics), ordering rule, headline selection, rollup sums
  (ward apps excluded from reflex rollup).
- `SessionCooldownManagerTest` — `peekCooldownState` returns expired entries
  un-pruned and writes nothing; pruning accessor behavior unchanged.
- `SpellbookViewModelTest` (or extend `PactManagerTest`) — `getGuardLiveState`
  snapshot composition; onboarding `onCreateFirstPacts` fan-out produces N pact
  configs with the documented defaults and respects the cap toast path.

**Manual QA checklist per phase:** fresh install through pact-first onboarding →
lands on dashboard showing created pacts; upgrade with existing pacts+limits+groups
→ every guard renders, intro dialog once; seal an app (lapse a pact) → dashboard
chip flips within a minute and on re-foreground; ritual fires → banner appears,
tap lands on Focus; rotation on dashboard/editor/onboarding step 2; dark + light;
French locale renders every new screen; NFC-lock and hide-stop sessions still
behave on the Focus tab; convert pact↔ward and confirm enforcement follows on next
open; delete enchantment carrying a circle → dashboard drops it (existing cleanup).

## 10. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Muscle-memory break for existing users (cast was one tap) | One-time intro dialog; Focus is the adjacent tab; session banner links straight to it whenever a session runs. |
| UI process racing service prefs writes via cooldown pruning | Read-only peek accessors (§4.1); UI never writes cooldown state. |
| Dashboard staleness (seals lift while user stares at it) | Minute tick while visible + resume refresh; countdowns are minute-granular by design, matching the overlay precedent. |
| Two-surface deletion (`PactsScreen`/`TimeLimitsScreen`) breaking a forgotten entry point | Grep gate in Phase 3: no references to deleted routes/screens; `UnusedResources` lint on retired strings. |
| `Greeting`'s prop bundle churn while moving branches | Phase 1 moves the wiring verbatim (cut/paste, no signature edits) — chips removal is the only `Greeting` diff, isolated in the same PR. |
| Onboarding hard-requiring a pact alienates session-only users | Accepted per decision record; pacts delete in two taps; revisit if feedback demands a skip. |
| French copy drift | New keys land in both files in the same commit; CI lint (`MissingTranslation` severity per lint config) plus the QA locale pass. |

## 11. Open questions / later ideas (explicitly out of scope)

- Home-screen widget with seal states; "seal lifted" notifications.
- "Seal everything now" panic button on the dashboard.
- Wards for enchantment groups (needs a per-member vs shared-budget decision).
- Per-guard schedules ("this pact only applies after 9 pm") — would reuse ritual
  time infrastructure.
- Whether `Greeting`/`HomeScreen.kt` should be renamed `FocusScreen.kt` once the
  dust settles (pure rename, deferred to avoid diff noise during the restructure).

---

## Addendum (July 2026): Pacts + Focus merged into one Home tab

Living with the 4-tab layout showed the Pacts/Focus split to be awkward in
practice: starting a session and watching guards are the same daily habit, and
the session banner was a workaround for having split them. A follow-up
restructure merged the two:

- 3 tabs again: **Home · Spellbook · Insights**. `AppDestinations.FOCUS` is gone.
- Home = the guard dashboard with the focus caster embedded at the top
  (`PactsHomeScreen` takes a `focusSection` composable slot; `Greeting` was
  renamed `FocusSection` and no longer owns its own scrolling).
- Idle: one compact "Ready to Cast" card — Quick Spell chips, enchantment
  toggle chips, duration chips, breaks switch, full-width Cast button. The
  two `ExposedDropdownMenu` selectors and the 216 dp cast orb were retired.
- Active: the full timer/breaks/dispel UI renders in place of the card, with
  guard cards still reachable below. The session banner was deleted.
- The QR scan affordance moved to the Home top-app-bar action slot (it stays
  inline in the NFC-lock session state, where it's a dispel path).
