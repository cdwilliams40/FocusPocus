# Focus Pocus — Mana Well Plan

A codebase-verified plan for a shared, slowly refilling time budget across pact apps.
Integration points were checked against the code at `b764213` (v1.9.2).

---

## 1. The problem

Every limit in the app is per app. A pact grants its own allowance and starts its own
seal, so nothing caps total time across pact apps:

- At the defaults (15-minute pact, 30-minute seal), three pact apps are enough to
  scroll indefinitely: by the time the third seals, the first has unsealed.
- Group Seal Mode stops hopping inside one window, but it is a fixed on/off cycle. At
  its defaults (15 open, 30 sealed) pact apps are still available a third of the day.
- The sealed-minutes perk (150 mana → 10 minutes) is once *per app* per day, so it
  also scales with the number of pact apps.

What is missing is one budget that all pact apps share, that is small enough to
prevent a binge, and that comes back slowly enough to prevent a cycle.

## 2. Decision record

Rows marked ✔ were decided with the project owner.

| Decision | Choice |
|---|---|
| ✔ Mechanism | **The Mana Well**: one pool of minutes shared by pact apps. Requesting pact time draws from it; when it is dry, no pact can be made. |
| ✔ Refill | **Two sources.** A slow passive refill on the clock, plus mana top-ups ("channeling") earned through focus sessions. |
| ✔ Capacity | **30 minutes** by default (adjustable 10–60). |
| ✔ Scope | **Every pact app draws from the Well**, with a per-pact (and per-circle) exemption. |
| Passive rate | Default **1 minute per 20 minutes** of real time (3 per hour). Adjustable 10–60 minutes per Well minute, or off entirely ("earned only"). |
| Separate meter | The Well is **not** the mana balance. Mana is uncapped and earned in bulk, so a banked balance could fund a binge, and a passive trickle would cheapen boons. Mana feeds the Well; it does not replace it. |
| Draw timing | **Up front**, when the pact is chosen — the same moment as today's conscious minute choice. Pact allowances already run on the wall clock, so this matches. |
| Mana top-up | **75 mana → +5 minutes**, never past the capacity. Each further top-up on the same day costs double (75, 150, 300 …). Replaces the sealed-minutes perk while the Well is on. |
| Default state | **Off.** Opt in from Settings; existing behaviour is unchanged until then. |
| Commitment | Loosening the Well (turning it off, larger capacity, faster refill) takes effect **24 hours** after it is requested, like a pact change. Tightening is immediate. |
| Group Seal | Stays an independent option. With both on, one draw opens every Well-bound pact app for the shared window. Exempt pacts sit outside the group and keep their own allowance and seal. |
| Notifications | **None** for "the Well is full" — that would be a prompt to go and use it (the same reasoning that keeps seal-lifted alerts off by default). |

### What the defaults mean in practice

- A 30-minute capacity is the largest single binge possible.
- After a 15-minute draw, getting those 15 minutes back takes 5 hours.
- Sustained use cannot exceed the refill rate: 72 minutes per 24 hours. A day that
  starts with a full Well and 16 waking hours tops out at about 78 minutes.
- Hopping no longer helps: a second app is a second draw from the same pool.

## 3. Mechanics

### 3.1 The pool

State is two numbers: the level (milliseconds of app time) and when it was last
written. The level at any moment is computed, never ticked:

```
level(now) = min(capacity, storedLevel + (now − storedAt) / refillInterval)
```

- Nothing runs in the background; reads are a couple of primitive pref lookups.
- The store is only rewritten on a mutation (draw, refund, channel, settings change),
  so repeated reads cannot accumulate rounding loss.
- A clock that moved backwards counts as zero elapsed time.
- A Well that has never been used starts full.

### 3.2 Drawing

- A Well-bound pact offers the usual minute choices, limited to what the Well holds.
- Choosing one draws those minutes and grants the allowance in one step. If the Well
  was drained in the meantime (two overlays, a race), the grant is refused.
- A dry Well shows when the next choice becomes affordable, plus the pact's healthier
  substitute if one is set. There is nothing to tap through.
- Per-app seals, escalation, daily backstops and guard hours all keep working as they
  do today. Outside guard hours a pact stands down and draws nothing.

### 3.3 Exemptions

`wellExempt` on a pact or circle means "this pact keeps its own allowance, outside the
shared pool". The field is named so that `false` — what Gson fills in for configs
stored before the field existed — is the bound state. Changing it is a pact edit, so
the existing 24-hour revision queue already covers it.

### 3.4 Group Seal with the Well

- The group is every Well-bound pact app. Opening one draws
  `min(shared window, Well level)` once and opens all of them for that long.
- Exempt pacts are not part of the group: they use plain per-app pacts, and they stay
  requestable from the dashboard next to the group panel.
- With the Well off, Group Seal behaves exactly as before.

### 3.5 Channeling mana

- `Perk.WELL_CHANNEL`: 75 mana for 5 minutes, doubling per channel per day, counted
  from the structured ledger like the existing once-per-day perk check.
- Refused when the Well has less than 5 minutes of room, so mana is never wasted.
- While the Well is on, the sealed-minutes perk is hidden and refused: it is a
  per-app bypass of the pool, and it also lifts a seal, which channeling never does.

### 3.6 Changing the Well's terms

A requested change is split per field. Anything that tightens applies at once;
anything that loosens is queued for 24 hours and can be cancelled ("keep current
terms"). A queued change is applied at its due instant, not at discovery, so the
level is settled at the old rate up to that moment. A backup restore goes through the
same split, so it cannot loosen an enforced Well either.

## 4. Data model

New prefs keys (primitives only — no new Gson classes, so no R8 keep rules):

| Key | Type | Meaning |
|---|---|---|
| `wellEnabled` | Boolean | Master switch (default false) |
| `wellCapacityMinutes` | Int | 10–60, default 30 |
| `wellRefillIntervalMinutes` | Int | Real minutes per Well minute; 10–60, default 20; 0 = no passive refill |
| `wellLevelMillis`, `wellUpdatedAtMillis` | Long | The pool (§3.1) |
| `wellDrawnDate`, `wellDrawnMillis` | String, Long | Net minutes drawn today, for the dashboard |
| `wellPendingAppliesAtMillis` + three pending values | Long, … | The queued loosening (§3.6); 0 = none |
| `wellUnlockCreditDate`, `wellUnlockCreditedRules` | String, String | Conditional-unlock rules that already paid today (comma-joined ids) |

Model fields: `AppTimeLimit.wellExempt`, `PactGroup.wellExempt` (both default false).

Backup: the three settings travel; the level, the daily counter and the pending
change stay on the device, like seals and allowances do.

## 5. Integration points

| Where | Change |
|---|---|
| `limit/ManaWell.kt` (new) | Pure math (`WellMath`) plus the synchronized prefs-backed store, in the style of `Progression`. |
| `MyAccessibilityService.checkTimeLimitAndBlock` step 4 | Flags the pact overlay as Well-bound; Group Seal only governs Well-bound configs while the Well is on. `sealRequestFor` follows the same rule. |
| `OverlayActivity` | Limits choices to the Well level, shows the level, draws before granting, and renders the dry state. |
| `SpellbookViewModel.requestPactTime` / `groupOpenPacts` | Draw before granting; refuse with a toast when dry. |
| `GuardStatus` | `groupOpenEligiblePackages` / `groupSealSummary` take the Well state and skip exempt configs. |
| `PactsHomeScreen` | Well card (level, refill rate, drawn today, channel button); Well-aware request dialog. |
| `GuardEditorScreen` | "Draws from the Mana Well" switch on pacts and circles, shown while the Well is on. |
| `SettingsScreen` / `SettingsViewModel` | Mana Well card: switch, capacity, refill, pending-change line with cancel. |
| `Progression.redeemPerk`, `BoonsScreen` | `WELL_CHANNEL` perk; sealed minutes hidden and refused while the Well is on. |
| `BackupCodec` | Export the settings; hold enforced terms on restore. |
| `SpellbookViewModel.sealNow` | One path for the panic seal and per-pact "Seal now"; refunds unused Well time. |
| `limit/WellUnlocks.kt` (new), `DeviceOwnerManager` | Rules pay into the Well instead of freeing Well-bound pacts; Warden greying mirrors it. |

## 6. Phases

| Phase | Scope | Status |
|---|---|---|
| 1 | Well engine, settings with 24-hour cooling-off, enforcement on every grant path, exemptions, dashboard card, backup | Implemented |
| 2 | Channeling mana into the Well; sealed-minutes perk retired while the Well is on | Implemented |
| 3 | Seal early and get unused minutes back (per-pact "seal now", and the panic button) | Implemented |
| 4 | Conditional unlocks add minutes to the Well instead of bypassing the pact gate for the rest of the day | Implemented |
| 5 | Later ideas: overflow from a full Well trickles into mana; drawn-per-day history on Insights; Well trials ("keep the Well above half all day"); home-screen widget | Ideas |

### Phase 3: sealing early

A pact runs on the wall clock and there was no per-app way to end one, so paying up
front pushed towards using every minute.

- A card with a running pact carries **Seal now** (apps and circles). It ends the
  pact and seals on the panic seal's terms: base length, not an offence.
- **Seal everything now** does the same for every pact app.
- Either way, time left on allowances paid from the Well goes back into it, and comes
  off the day's drawn total.
- No record of "paid" allowances is kept. Each distinct expiry among the revoked
  Well-bound allowances is one draw and is refunded once; a draw is only refunded when
  nothing sharing its expiry stays open (`WellMath.refundableMillis`). That is what
  makes a Group Seal window — one draw, many apps — refund once, and only when the
  whole window is closed. For that reason a pact inside a Group Seal window has no
  Seal-now button of its own; the panic button closes the window.
- Known looseness: an allowance granted just before the Well was switched on is
  refunded although it was never drawn. It is bounded by the capacity and can only
  happen in the minutes after switching on.

### Phase 4: conditional unlocks

`isTimeLimitConditionallyUnlocked` returns before the pact gate, so a met rule used to
free its apps for the rest of the day and skip the Well entirely.

- With the Well on, a rule never frees a **Well-bound pact**: the gate, the seal and
  the daily backstop all hold. Warden greying follows the same rule.
- Instead, the first time each day such a rule is found satisfied it adds
  **10 minutes** to the Well (`WellUnlocks.GRANT_MINUTES`), as many whole minutes as
  fit. A rule that finds the Well full stays unpaid and is tried again later that day.
- The check runs right before a Well-bound pact is offered (service) and on each
  dashboard refresh (so it also lands under Warden greying). It costs no usage query
  once every paying rule has paid.
- Wards, exempt pacts and enchantments named by the same rule are unlocked as before.
- With the Well off, conditional unlocks behave exactly as they always did.

## 7. Testing

Unit tests, in the existing pure-function style:

- `ManaWellTest`: refill math, capacity clamp, backwards clock, draw/refuse, channel
  room check, daily counter rollover, settings split (tighten now, loosen later),
  due-revision settlement, cancel.
- `GuardStatusTest`: exempt configs excluded from the group while the Well is on.
- `ProgressionTest` / `ProgressionMathTest`: channel pricing, doubling, room and
  balance refusals, sealed minutes refused while the Well is on.
- `BackupCodecTest`: settings round-trip; a looser backup is queued, not applied.
- `ManaWellTest` (refunds): time left, lapsed pacts, shared windows, partial closes,
  capacity and daily-total clamps.
- `WellUnlocksTest`: which rules pay, once a day, full and nearly full Wells, Well off,
  no usage query once paid.

Manual checklist (no Compose UI tests exist):

1. Turn the Well on; open a pact app; the choices stop at the Well level.
2. Draw twice across two apps; the second draw sees the reduced level.
3. Drain the Well; the overlay and the dashboard dialog both show the dry state.
4. Mark a pact exempt (after its 24 hours); it opens with the Well dry.
5. Group Seal on: one draw opens all Well-bound apps; an exempt pact stays separate.
6. Raise the capacity; it is queued, shown, and cancellable. Lower it; it is instant.
7. Channel mana twice in a day; the second costs double.
8. Warden greying: requesting time from the dashboard still un-greys the app.
9. Seal a running pact early; the unused minutes return and the day's total drops.
10. Meet a conditional unlock that names a Well-bound pact: the app stays sealed and
    the Well gains 10 minutes, once.

## 8. Risks

| Risk | Mitigation |
|---|---|
| Too strict, so it gets switched off | Off takes 24 hours; capacity and rate are adjustable; exemptions cover apps that are only mildly distracting. |
| Clock tampering refills the Well | Same exposure pact allowances and seals already have; out of scope here. |
| Conditional unlocks bypass the pool | Closed in phase 4 for Well-bound pacts; exempt pacts and wards keep the bypass by design. |
| Minutes wasted by leaving early | Phase 3: "Seal now" refunds the unused time. Walking away without sealing still spends the whole pact. |
| Mana farming (an overnight Sleep Mode session mints up to 240) | Channel price doubles per day and never exceeds the capacity, so farmed mana buys at most a full Well. |

## 9. Open tunables

- Capacity 30 min; refill 1 min per 20 min.
- Channel: 75 mana, 5 minutes, doubling per day.
- Cooling-off for loosening: 24 hours (shared with pact revisions).
- Conditional-unlock grant: 10 minutes per rule per day.
