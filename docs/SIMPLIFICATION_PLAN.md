# Simplification plan (target: 2.0.0)

Settled 2026-11-11 against 1.10.3 (versionCode 42). This replaces the direction
in `ROADMAP.md`, `PROGRESSION_PLAN.md`, `PACTS_FIRST_UI_PLAN.md` and
`MANA_WELL_PLAN.md`; those four are deleted in the last phase.

## Why

The app grew three overlapping ways to limit an app (wards, pacts with their
own allowance and seal, the Mana Well), three ways to start a session (Focus
tab, Quick Spells, talismans, plus rituals), and a reward system whose rules
are hidden (streak multipliers, sigils, boons, a wrap-up notification). Actual
use is much narrower: a "Shield" blocklist that goes on every morning and only
comes off with an NFC talisman, plus an occasional timed session on a list of
distracting apps.

## Target shape

The app does three things, and each has one mechanism.

1. **The Well** is the only always-on limit. One list of distracting apps
   draws minutes from one pool. The pool refills slowly on its own and can be
   topped up with mana.
2. **Focus sessions** block enchantments for a while. A session starts by
   hand, from a ritual, or from a talisman tap.
3. **Mana** is the one currency. A published table says what earns it and what
   it buys.

Warden mode, Protection Health, backup and Insights stay around those three.

## What is removed

| Removed | What covers it afterwards |
|---|---|
| Wards (daily limit, session limit, cooldown, escalation) | Nothing. Unused, deleted with no migration. |
| Per-app pact allowance, seal, friction levels, backstop, Well exemption | The Well. A Well app keeps only an optional substitute app. |
| Group Seal, "Seal everything now", seal-lifted alerts | Nothing. "End pact" (refund the rest of a draw) stays. |
| Pact circles, guard hours | Nothing. Circle members become ordinary Well apps. |
| Conditional unlocks | Productive apps that earn mana. |
| Quick Spells, the Quick Settings tile, deep links, timed tag actions | The Focus tab remembers the last setup. A talisman carries its own setup. |
| Sigils, boons, the sealed-minutes perk | Nothing. |
| Streak multiplier, streak milestones, daily wrap-up | The streak stays as a plain number on Insights. |

"Pact" survives as the name for one draw from the Well.

## Decisions

- **No per-app pact cap.** When making a pact, the picker offers 2 to 30
  minutes, never more than the Well currently holds.
- **Passive refill stays.** The Well drips back on its own (default 1 minute
  per 20). Mana is redeemed for extra minutes on top.
- **Doubling price stays, with no daily cap.** A pour is 5 minutes. The first
  pour of the day costs 75 mana and each further pour that day costs double.
  The price resets at midnight. Mana can be saved up for a day that needs it.
- **A pour never overfills.** `ManaWell.channel` already refuses a pour that
  does not fit under the capacity, so pours are for when the Well is low.
- **Talismans start and stop sessions, and act as ritual keys.** Each talisman
  stores what it starts.
- **A session started by a talisman ends only with that talisman** (a
  per-talisman switch, on by default). This preserves "only turned off with
  the talisman" however the Shield is started.
- **The streak is display only**: days in a row with at least one recorded
  session.
- **The mana balance carries over unchanged** at the 2.0 migration.

## Mana rules (starting values)

Every number lives in one file, `ManaRules.kt`, and the "How mana works"
screen is rendered from that file so the two cannot drift.

| Earn | Mana |
|---|---|
| Timed session started in the app | 1 per focused minute, up to 240 a day |
| Open-ended session (ritual or talisman) | Flat 30, once a day, once it has run 60 minutes |
| Time in a chosen productive app | 1 per 2 minutes, up to 60 a day |
| Daily trial (two a day) | 40 to 60 each |
| Weekly trial (one a week) | 150 to 200 |

| Spend | Mana |
|---|---|
| Pour 5 minutes into the Well | 75, doubling with each pour that day |
| Extra break in a session | 50 |

An ordinary day (Shield, some productive-app time, both daily trials) earns
about 190. Cumulative price of pours in one day:

| Pours | Minutes | Total mana | Roughly |
|---|---|---|---|
| 1 | 5 | 75 | under half a day's earnings |
| 2 | 10 | 225 | one day |
| 3 | 15 | 525 | two and a half days |
| 4 | 20 | 1,125 | six days |
| 5 | 25 | 2,325 | twelve days |

Trial templates:

- Daily: timed focus minutes (50); start a timed session by hand (40); draw at
  most N Well minutes (60); at most N reflex opens (40); N minutes in a
  productive app (40).
- Weekly: timed focus minutes (200); Well minutes under N for the week (200);
  reflex opens under N for the week (150).
- Dropped: "finish a ritual" and "N sessions", which a daily Shield completes
  without any effort.

## Phases

Each phase is one or more PRs against `master`, each with its own version-bump
commit. Every data change goes through one `Migrations` file keyed on a stored
schema version, and each step is safe to run twice.

### Phase 0: land what is open

1. Merge #146, retarget #147 to `master` and merge, then the same for #148.
2. Export a backup from the phone and keep the 1.10.3 APK.
3. Copy the backup to a git-ignored path as a migration test fixture. It
   contains personal data and must not be committed.

### Phase 1: removals with no replacement

- Sigils (`model/Sigil.kt`, `unlockSigils`, Insights section, strings).
- Boons (`redeemBoon`, the boon list and editor). `BoonsScreen` becomes a
  slim `ManaScreen` holding the balance, the Well pour and the extra break,
  until Phase 4 rebuilds it.
- Sealed-minutes perk (`Perk.SEALED_MINUTES`).
- Daily wrap-up (`maybeSendDailyWrapup`, `shouldSendWrapup`, its notification).
- Streak multiplier and `STREAK_MILESTONE_MANA`. `calculateCurrentStreak` stays.
- `QuickSpellTileService` and deep links (`resolveDeepLink`, the manifest intent filter).
- The unused `navigation-compose` dependency.
- `Migrations.kt` with step 1: delete the stored keys of the removed features.
- Ledger entries of removed kinds must still load. Gson turns an unknown enum
  value into null, so keep the old `LedgerKind` values or handle null.

Moved out of Phase 1 while implementing it: Group Seal, "Seal everything
now" and seal-lifted alerts go in Phase 2a, because they are woven through
the seal code that phase deletes. `ACTIVE_BLOCKER` goes in Phase 3 with the
rest of the session code. `FOCUS_SESSION_CHANNEL_ID` is in use and stays.

### Phase 2: the Well as the only limit

**2a. Delete the unused limit features.** Group Seal (`GROUP_SEAL_*`
settings, the group pact overlay, the dashboard panel), "Seal everything
now", seal-lifted alerts (`GuardNotifier`). Wards, guard hours and pact circles:
`SessionCooldownManager`, `GuardWindow`, `PactGroup`, the ward fields of
`AppTimeLimit`, and steps 1, 3 and 5 of `checkTimeLimitAndBlock`.

**2b. Make the Well the model.**

- New `WellApp(packageName, substitutePackage?)` list. Migration: every pact
  app and every circle member becomes a Well app, and the Well is switched on
  with its current settings.
- `checkTimeLimitAndBlock` shrinks to: is this a Well app, and is a draw
  active or affordable. `GuardStatus.lapseSeal`, `sealRequestFor` and
  `FrictionLevel` go.
- `GuardEditorScreen` (1,117 lines) becomes an app list with a substitute picker.
- `PactsHomeScreen`, `OverlayActivity` and Settings lose their seal, ward and
  Group Seal parts.
- Removing an app from the Well takes 24 hours, through a cut-down
  `PactRevisionManager`. Adding is instant.
- Warden: `DeviceOwnerManager.computePactSuspensions` reads the Well list.
- Start recording per-day Well history now (see Phase 5) so Insights has data
  by the time it is rebuilt.
- Backup `FORMAT_VERSION` 2. A version 1 file is imported through the same
  migration.

Done in Phase 2 as built: the pact screen offers 2, 5, 10, 15 and 30
minutes, trimmed to what the Well holds. The Well's stored `enabled` flag is
kept but is always true (migration and every restore set it); the Settings
switch is gone. Stay-under-limits trials can no longer be drawn or won, and
Phase 4 replaces the trial list. The Home tab is still labelled "Pacts";
renaming it is left for Phase 6.

### Phase 3: sessions and talismans

Independent of Phase 4; either can go first.

- `Talisman` replaces `NamedTag`: id, name, enchantments, breaks on or off,
  optional duration, and the "only this talisman ends it" switch.
- Migration: a talisman with a bound Quick Spell takes that spell's
  enchantments, breaks setting and duration (`TEMP_DISABLE` spells are
  dropped). A talisman with no spell takes the enchantments selected on the
  Focus tab at migration time.
- A tap resolves in this order: key for the running ritual, else stop the
  session this talisman started, else start its session. Everything goes
  through `SessionManager.startSession` and `stopSession`, so tag sessions are
  recorded for the first time.
- Delete the `FOCUS_TAG_ID` path (seven files), `NfcResult.ToggleFocusTag` and
  `PresetToggled`, `FocusPreset`, `PresetAction`, `PresetRepository`,
  `QuickSpellEditorScreen` and `QuickSpellsListScreen`.
- `FocusSession` gains a `source` field (manual, ritual, talisman) and whether
  it was timed. Both nullable for old records.
- Focus tab: pick enchantments, pick a duration, cast. It remembers the last
  setup. Spellbook holds Enchantments, Rituals and Talismans.
- The talisman lock setting (`NFC_LOCK_MODE`) is unchanged.

Done in Phase 3 as built: a tap while some other session runs is refused
rather than replacing it. Talismans keep their stored key (`namedTags`) and
gain nullable setup fields, so talismans from older backups still load.
Backup format 3 restores a pre-3 file's Quick Spells onto their talismans.
A physical tag tap is covered by unit tests only; the emulator has no NFC.

### Phase 4: mana

- `ManaRules.kt` and the "How mana works" screen.
- `computeManaAward` follows the earn table: per minute for timed manual
  sessions with a daily cap, flat for open-ended ones. The hide-stop and
  ritual bonuses go.
- Productive apps: a chosen list, credited from foreground time on the
  service's minute tick (`UsageStatsHelper.getPackageUsageToday`), up to the
  daily cap. Then delete `ConditionalUnlocksScreen`,
  `ConditionalUnlockRepository`, `WellUnlocks` and the Warden mirrors.
- Pours: keep `ProgressionMath.wellChannelCost`. Show the next price on the
  home panel and on the Well-empty overlay.
- New trial templates in `TrialEngine`.
- Ledger kinds become session, open-ended session, productive app, trial,
  pour, break. Keep per-day earned and spent totals as well, because the
  ledger holds only 300 entries (`MAX_MANA_LEDGER`).
- Home panel: Well level and refill, mana balance, next pour price, today's
  trials.

Done in Phase 4 as built: a talisman session with a length counts as
timed (paid per minute); one without, and every ritual, is open-ended.
Productive time is credited every 5 minutes by the service and whenever the
home screen refreshes. Conditional-unlock rules became productive apps
(migration step 4), which also drops unfinished trials so the new list is
drawn at once. Per-day `WellHistory` and `ManaHistory` (60 days) now exist
and are backed up (format 4). The dry-Well screen offers a pour.

### Phase 5: Insights redesign

The screen is one 976-line composable. Split it into section files and
organise it around the three mechanisms. A range switch (today, 7 days, 30
days) applies to every section.

| Section | Shows | Data |
|---|---|---|
| Summary | Well minutes, focus minutes, net mana, streak | below |
| The Well | Minutes drawn per day against the passive allowance; dripped and poured minutes; per-app split; number of pacts and average length; minutes refunded by ending early; times it ran dry | New per-day `WellHistory`, 30 days |
| Reflexes | Opens and reflex opens per Well app, with the trend | `OpenReflexTracker` (existing, 30 days) |
| Focus | Minutes per day by source; sessions and average length; blocked attempts and most-blocked apps; session history | `FocusSession.source`, `BlockEvent` |
| Mana | Balance; earned and spent per day; earned by source; pours with the price paid; trials completed this week | Ledger and per-day totals |
| Screen time | The existing per-app usage list, collapsed by default | `UsageStatsHelper` |

Removed from Insights: the sigils section, the boons button and the
time-limit status list.

`WellHistory` is a map from date to: drawn milliseconds, poured minutes,
per-app milliseconds, number of draws, refunded milliseconds, dry blocks. It
replaces `WELL_DRAWN_DATE` and `WELL_DRAWN_MILLIS`, which hold today only.

### Phase 6: finish

- Prune English and French strings (886 and 916 today).
- Rewrite onboarding around the Well list, one ritual and one talisman.
- Update the README; write one design doc and delete the four old plan docs
  and this file.
- Run the migration test against the Phase 0 backup, then install over 1.10.3
  on the emulator and on the phone.
- Release 2.0.0.

## Risks

- **Losing the Shield setup in migration.** Phase 3 changes how a tag tap is
  resolved. Test with the real backup before installing on the phone.
- **Enforcement regressions in Phase 2b.** `checkTimeLimitAndBlock` is the
  core of the app. Keep the existing Well tests and add cases for a migrated
  pact app.
- **Economy values are guesses.** They are isolated in `ManaRules.kt` so they
  can be tuned after a week or two of real use.
