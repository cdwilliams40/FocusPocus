# Focus Pocus design

How the app is put together as of 2.0, and why. This replaces the earlier
plan documents (`ROADMAP.md`, `PROGRESSION_PLAN.md`, `PACTS_FIRST_UI_PLAN.md`,
`MANA_WELL_PLAN.md`, `SIMPLIFICATION_PLAN.md`); git history has them.

## The three parts

The app does three things, and each has one mechanism.

1. **The Mana Well** is the only always-on limit. One list of Well apps
   draws minutes from one pool that refills slowly on its own.
2. **Focus sessions** block enchantments (app lists) for a while. A session
   starts by hand, from a ritual, or from a talisman tap.
3. **Mana** is the one currency. It is earned by focusing and spent on Well
   minutes and extra breaks, by rules published in the app.

Warden Mode, Protection Health, backup and Insights sit around those three.

## The Mana Well

- A Well app is closed by default. Opening it offers a **pact**: 2, 5, 10, 15
  or 30 minutes (`WellApps.PACT_CHOICES`), trimmed to what the Well holds,
  behind a three-second pause.
- All Well apps share the pool, so hopping between them spends the same
  time. That is the reason the Well replaced per-app allowances.
- A pact that runs out just closes the app. There are no seals, cooldowns or
  friction levels: the Well is the limit, and a seal on top of it punished a
  two-minute check as hard as a long session.
- **End pact** refunds the unused part (`WellMath.refundableMillis`).
- Commitments only loosen slowly. Taking an app off the Well, raising the
  capacity or speeding the refill all wait 24 hours and can be called off.
  Adding apps and tightening are instant. A backup restore follows the same
  rule.
- The Well's `enabled` flag is still stored but is always true; migrations
  and restores set it.

Enforcement lives in `MyAccessibilityService.checkWellAndBlock`: a Well app
with no running pact is closed and the pact screen (`OverlayActivity`) is
shown. A one-shot wakeup closes the app the moment its pact ends; the minute
tick is the backstop. Warden Mode greys out Well apps with no running pact.

## Focus sessions

- `SessionManager.startSession` / `stopSession` is the one path. Every
  session is recorded (`SessionRecorder`) with its **source** (manual,
  ritual, talisman) and its planned length.
- A **ritual** may name a talisman as its key; then only that tag ends it.
- A **talisman** carries a setup (enchantments, length or open-ended,
  breaks, "only this talisman ends it"). A tap is, in order: the key for the
  running ritual, the end of the session this talisman started, or the start
  of its own. A tap never replaces a session something else started
  (`TriggerHandler.handleNfcTag`).
- Breaks, auto-breaks, the emergency stop and the talisman lock work for
  every kind of session.

## Mana

`ManaRules.kt` holds every number, and the Mana page's "How mana works" is
drawn from it.

| Earn | |
|---|---|
| Timed session | 1 per minute, up to 240 a day |
| Open-ended session (ritual, or a talisman with no length) | 30 once a day, after 60 minutes |
| Productive apps | 1 per 2 minutes, up to 60 a day |
| Daily trials / weekly trial | 40–60 / 150–200 |

| Spend | |
|---|---|
| Pour 5 minutes into the Well | 75, doubling with each pour that day |
| Extra break | 50 |

Why these shapes:

- **Open-ended sessions pay flat.** A daily ritual of several hours used to
  mint hundreds of mana with no effort, which made mana meaningless.
- **The pour price doubles but has no cap.** Saved-up mana can still buy a
  hard day's extra time; it just gets expensive fast.
- **Trials count timed sessions only**, for the same reason as the flat
  ritual payment. Retired trial types stay in the enum so old records load.

All mana changes go through `Progression` (one synchronized monitor), which
also writes the per-day `ManaHistory`.

## History and Insights

- `DailyHistory` keeps 60 days of `WellDay` (net minutes drawn, refunds,
  draws, pours, dry opens, minutes per app) and `ManaDay` (earned by source,
  spent by use).
- `OpenReflexTracker` keeps 30 days of opens and reflex opens.
- `InsightsReport.build` turns those, the session list and block events into
  every figure on the Insights screen, for today, 7 days or 30 days. It has
  no Android dependencies and is unit-tested.
- Charts (`ui/screens/insights/InsightsCharts.kt`) are thin bars with a
  tap-to-read caption. Stacked series use the first three colours of the
  data-viz reference palette, with a legend that carries values.

## Stored data and migrations

Everything lives in one SharedPreferences file. `Migrations` advances a
stored `schemaVersion`; steps never get reordered, and each is safe to run
twice:

1. Boons, sigils, streak milestones and the wrap-up removed.
2. Pacts and pact-circle members became Well apps; wards, seals, circles,
   guard hours and Group Seal removed; the Well switched on.
3. Quick Spells became talisman setups; a running tag-only session became a
   recorded talisman session.
4. Conditional-unlock rules became productive apps; unfinished trials
   dropped so the new list is drawn.

Removed keys are named only in `Migrations.LegacyKeys`.

`BackupCodec` exports an explicit key list (format 4). Older files restore
through the same migration steps. Every prefs key is classified as exported
or device-local, and `BackupCodecTest` fails on an unclassified one.

## Working on it

- Build and test: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- Feature work goes on a branch and lands by pull request, each with a
  `Bump to X (versionCode N)` commit.
- New prefs keys need a backup classification; new stored shapes need a
  migration step and a test.
