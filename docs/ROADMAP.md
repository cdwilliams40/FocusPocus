# Focus Pocus — Product Roadmap

*Drafted July 2026, against v1.6 (versionCode 30). This document looks forward: the two
prior plans — the progression layer (`PROGRESSION_PLAN.md`) and the pacts-first UI
restructure (`PACTS_FIRST_UI_PLAN.md`) — are fully implemented and merged. The goal from
here is explicit: become the most comprehensive and best-designed app in the
focus / app-blocker category on the Play Store.*

---

## 1. Where we are (v1.6)

Focus Pocus already covers more of the category's surface than most shipping competitors:

| Pillar | What we have |
|---|---|
| Blocking engine | Accessibility-based foreground detection, blacklist/whitelist enchantments, auto-banish new installs, notification silencing |
| Standing protection | Pacts (sealed-by-default with anti-reflex pause, substitutes, daily backstops), Wards (daily limits, session cooldowns, escalating friction), pact circles over live enchantments |
| Sessions | Timed sessions, breaks, Pomodoro auto-breaks, emergency stop with cadence cooldown, hide-stop-button, live countdown notification, wall-clock enforcement across reboots |
| Automation | Scheduled rituals, NFC talismans (start/stop/lock), deep links, Quick Spells |
| Hard mode | Warden device-owner mode: OS-level app suspension, around-the-clock grey-out of pacted apps, uninstall protection, 24 h removal cooling-off |
| Motivation | Mana economy, trials (daily/weekly), sigils, boons + in-app perks, streaks, evening wrap-up |
| Insights | Session history, usage trends, block events, open/reflex tracking with 30-day history |
| Craft | Arcane Dusk Material 3 theme, guided onboarding, French locale, CI (tests + lint + assemble), crash reporting |

**What we don't have yet** clusters into four themes, and those are the roadmap:

1. **Distribution** — the app is not on the Play Store; policy compliance is unresolved.
2. **Coverage** — websites, other devices, and contexts (place/time-of-day per guard) are unprotected.
3. **Durability** — no backup/export; a single SharedPreferences file holds everything; rituals depend on the accessibility service being alive.
4. **Reach** — no widgets, no Wear OS, two locales, phone-portrait-first layouts.

## 2. Definition of "best in class"

"Most comprehensive and well designed" needs measurable teeth. The bar we hold every
release to:

- **Coverage**: every escape hatch a distracted brain will try has an answer — other
  apps, websites, new installs, reinstalls, settings, uninstall, the browser, the watch.
- **Trust**: blocking never fails silently. If enforcement can't run (service dead,
  battery-killed, permission revoked), the user is told before they need it.
- **Respect**: strictness is always chosen, never sprung; everything is reversible
  through a deliberate (never impulsive) path. No dark patterns, no data leaves the
  device without opt-in.
- **Delight**: the arcane theme, plain-language subtitles, and positive-tone progression
  stay coherent across every new surface. Design review is part of every feature PR.
- **Store health** (post-launch): ≥ 4.6 rating, ≥ 99.5 % crash-free sessions, ANR rate
  under Play's threshold, top-3 search placement for "app blocker" intent terms.

## 3. Competitive landscape (mid-2026)

What the category leaders are known for, and where we stand:

| Competitor | Their edge | Our position |
|---|---|---|
| **AppBlock** | Location/Wi-Fi–based blocking rules, strict mode, scheduling depth | We match schedules (rituals) and exceed strict mode (Warden); **no context rules — gap** |
| **Forest** | Beloved gamification, social planting | Progression layer matches the loop and is woven into real enforcement; **no social layer — gap** |
| **one sec** | Breathing-pause intervention before opening apps | Pacts' anti-reflex pause + reflex analytics go deeper; we win here |
| **StayFree / YourHour** | Rich usage analytics, widgets | Insights are solid; **widgets and exportable reports — gap** |
| **Opal** (iOS) | Design polish, focus score, cross-device | Design is competitive; **no sync/cross-device, no single "score" — gap** |
| **Cold Turkey / Freedom** | Cross-platform (desktop + mobile), website blocking | **Website blocking removed pending VPN approach; no desktop story — gap** |

Structural advantages nobody in the list combines: device-owner enforcement (Warden),
NFC talismans as physical ritual, pact circles with live membership, and an
honor-system reward economy tied to actual enforcement data. The roadmap protects these
while closing the gaps.

## 4. Horizon 1 — Ship it (v1.7 → v2.0)

Theme: **Play Store launch, and the reliability/durability work a public audience
demands.** Nothing else matters if distribution is blocked or first-week users hit a
dead service.

### 4.1 Play Store compliance track (blocking; start immediately)

- **Accessibility policy.** Play only approves AccessibilityService use via a
  declaration form, and app blockers are not "accessibility tools" — approval hinges on
  demonstrating core-functionality benefit and no less-invasive alternative. Our recent
  trimming (typeWindowStateChanged only, no window-content retrieval) is exactly the
  right posture. Actions:
  - Write the declaration narrative now; include the in-app prominent disclosure
    screen the policy requires (shown before the permission prompt, not only in
    onboarding).
  - Build the **fallback enforcement mode**: UsageStats-polling foreground detection +
    overlay, selectable if approval is denied or revoked later. Slower (1–2 s
    detection) but policy-safe; Warden suspension already covers the strict case
    without accessibility. This de-risks the entire distribution strategy.
- **Package visibility.** Audit for `QUERY_ALL_PACKAGES` vs. scoped `<queries>`; app
  pickers legitimately need broad visibility (blocker category is a permitted use),
  but the declaration must be filed with the same care.
- **Data safety form & privacy policy.** Easy story — everything is on-device, Firebase
  Crashlytics/Analytics are opt-in consented — but it must be written, hosted, and
  linked in-app.
- **Device-owner review risk.** Warden's adb provisioning is user-initiated and
  documented; keep it strictly opt-in, keep the in-app copy explicit, and be ready to
  ship a build variant without it if review demands.
- **Launch mechanics**: Play App Signing, internal → closed → open testing tracks,
  staged rollout, pre-launch report triage, store listing (screenshots, feature
  graphic, short/full description tuned for "app blocker / screen time / focus"
  search terms).

### 4.2 Reliability & durability

- **Backup / export / restore (local first).** Export all configuration + history to a
  JSON file via SAF; import with merge/replace choice. A reinstall or new phone
  currently destroys every pact, streak, and sigil — unacceptable once real users
  invest months. (Cloud sync is Horizon 3; the file format designed here becomes its
  wire format.)
- **Ritual scheduling off the minute-tick.** Rituals currently fire from
  `ACTION_TIME_TICK` inside the accessibility service — if the service is dead,
  schedules silently don't fire. Move to `AlarmManager` (exact, doze-safe) with the
  service tick as backstop.
- **Enforcement health surface.** A persistent "protection status" row (dashboard +
  Settings): accessibility alive? usage access granted? battery optimization
  exempted? OEM-specific kill risk (link the dontkillmyapp guidance per
  manufacturer)? One glance answers "am I actually protected right now."
- **Data layer hardening.** Begin migrating the single SharedPreferences file:
  history-shaped data (sessions, block events, ledger, open stats) → Room;
  settings/config → DataStore. Staged, model-by-model, behind the existing
  repository interfaces — the repos were built for exactly this swap.

### 4.3 High-leverage features already queued

Carried from the pacts plan's §11 "later ideas," now due:

- **Home-screen widgets + Quick Settings tile.** Seal states at a glance, one-tap
  Quick Spell cast, streak/mana glance widget. (Glance API; also the category's most
  visible checklist feature.)
- **"Seal everything now" panic button.** Dashboard action that instantly starts a
  strictest-defaults session or seals all pacted apps.
- **Per-guard schedules.** "This pact only applies 9 pm–7 am" / "Wards only on
  weekdays" — reusing ritual time infrastructure. This is the single most-requested
  shape in the category (AppBlock's core loop) and we already own the primitives.
- **"Seal lifted" notifications** (opt-in) so a lifted seal doesn't require opening
  the app to notice.

### 4.4 Craft pass for launch

- Predictive back, per-app language support, `values-night` audit, TalkBack sweep on
  the dashboard/editor, 48 dp touch-target audit.
- Tablet/foldable: at minimum, sane two-pane behavior via the already-present
  `NavigationSuiteScaffold` adaptive layouts.
- Store-facing polish: app icon variants (themed icons), splash screen API,
  onboarding screenshots that sell pacts-first.

**Exit criteria for Horizon 1:** live on the Play Store in open testing or production,
with backup/restore, widgets, per-guard schedules, and the health surface shipped;
crash-free ≥ 99.5 % across the testing tracks.

## 5. Horizon 2 — Widen the moat (v2.x)

Theme: **coverage and intelligence.** Close the gaps competitors are known for, using
mechanisms only we have.

### 5.1 Website blocking, done right (local VPN)

URL blocking was deliberately retired (commit `4e73715`) in favor of a local-VPN
approach — this is its return ticket. A `VpnService`-based DNS/SNI filter blocks
distracting sites in **every** browser and inside webviews, with no accessibility
screen-reading and no per-browser fragility:

- Site lists live on enchantments again, and — the differentiator — **sites join pacts
  and wards**: a sealed YouTube pact can seal youtube.com in the browser too, closing
  the "blocked the app, opened the site" loophole no Android competitor closes well.
- On-device only (no traffic leaves the phone), one active VPN slot honesty in docs,
  battery-tested before ship.

### 5.2 Context rules

- **Place**: start/stop guards or rituals on Wi-Fi SSID or geofence ("office Wi-Fi →
  work enchantment"; "home after 10 pm → wind-down").
- **Calendar**: optional read-only calendar integration — meetings become auto-rituals.
- **Wind-down / bedtime mode**: a first-class evening ritual preset with grayscale
  overlay option and gradually escalating seals.

### 5.3 Insights that coach

- **Weekly report**: reclaimed time, reflex trend, streaks, best/worst hours — as an
  in-app page and an opt-in notification digest. Shareable as an image (organic
  marketing surface).
- **Focus score**: one honest number summarizing the week (inputs: reclaimed minutes,
  reflex rate, pacts honored, sessions completed). Opal proved the retention power of
  a single legible metric; ours is backed by real enforcement data.
- **Blocked-notification digest**: what was silenced during focus, delivered after —
  removes the fear that makes users soften their own blocklists.
- Correlations, gently: "Your reflex opens spike after 11 pm" — heuristics first; any
  on-device ML later only if the heuristics prove wanting.

### 5.4 Progression depth

- Trial variety informed by real usage (streak-protection trials, wind-down trials,
  VPN-era "no browser backdoor" trials).
- Seasonal/long-arc sigils; a "grimoire" view telling the user's whole story.
- Carefully evaluate (and likely still refuse) streak freezes — the decision record's
  no-forgiven-days stance is a brand position, revisit only with user evidence.

### 5.5 Reach

- Locale expansion beyond en/fr: es, de, pt-BR, hi, ja first (top sideload +
  screen-time-concern markets); extract a translation workflow (Weblate or
  crowd-sourced) so locales don't rot — the fr baseline debt taught us.
- Optional **Material You / dynamic color** theme alongside Arcane Dusk (Arcane Dusk
  stays the brand default).
- Group wards (shared daily budget across an enchantment) — resolve the per-member vs
  shared-budget ambiguity that kept it out of the pacts plan, or kill it explicitly.

## 6. Horizon 3 — Beyond the device (v3+)

Theme: **the ecosystem plays.** Sequenced last because each depends on launch-scale
users and the Horizon-1 data format.

- **Cloud backup & multi-device sync.** End-to-end encrypted, account optional
  (Drive/passkey-based). The Horizon-1 export format becomes the sync payload.
  Unlocks: new-phone migration in minutes, and the tablet/phone pair sharing one
  pact state.
- **Wear OS companion.** Glanceable seal/session state, session controls from the
  wrist — and the watch as a **talisman** (tap to cast/dispel), which no competitor
  has and which is pure Focus Pocus brand.
- **Accountability layer (social, opt-in).** Shared pacts ("my partner sees if I break
  the seal"), focus-together sessions, a friend as the keeper of your emergency-break
  key. Forest's social planting shows the demand; our version binds to real
  enforcement, not a cosmetic tree. Requires a backend — the first feature that does —
  so it must justify itself with retention data from Horizons 1–2.
- **Desktop/browser extension.** Cold Turkey/Freedom own cross-platform. A minimal
  browser extension honoring the same site lists (synced via the cloud layer) covers
  the laptop escape hatch.
- **Launcher / minimal-phone mode.** A distraction-free launcher surface (or deep
  integration with one) for users who want the full monastery. Investigate after
  Warden adoption data shows how many users want maximum strictness.

## 7. Continuous engineering track

Runs alongside every horizon:

- **Testing**: Compose UI tests for the dashboard/editor/onboarding flows (none exist
  today); screenshot tests for both themes × both locales; keep the
  pure-function-extraction discipline that makes the current unit suite cheap.
- **Performance**: Baseline Profiles + startup tracing before launch; macrobenchmark
  the overlay-appearance latency (blocking that lags is blocking that fails).
- **Release engineering**: CI-driven Play publishing (internal track on merge to
  master), release notes automation, versionCode discipline, crash-free gates on
  staged rollout promotion.
- **Analytics (consent-gated)**: the zero-`logEvent` state means the roadmap currently
  flies blind. Define a minimal, opt-in event set (feature adoption, enforcement-mode
  distribution, onboarding funnel) — enough to rank Horizon 2 by evidence.
- **Policy watch**: accessibility and VPN policies shift yearly; assign each release a
  policy-review checklist item so a Play update never blindsides an enforcement
  mechanism.

## 8. Monetization (deliberately last)

Not required to start Horizon 1, but decide before open launch — retrofitting is
worse. Principles: **protection is never paywalled** (blocking, pacts, one ritual,
insights basics stay free); polish and scale can be. The natural premium line:
unlimited guards/rituals, VPN site blocking, context rules, cloud sync, Wear OS,
weekly-report history. One-time "lifetime grimoire" purchase + optional subscription
dual-track fits the category's buyer psychology and our honor-system brand better than
ads (ads in a focus app are self-refuting — never).

## 9. Risks

| Risk | Mitigation |
|---|---|
| Accessibility declaration denied | Fallback UsageStats enforcement mode (§4.1) built *before* submission; Warden covers strict users regardless |
| VPN + accessibility + device-admin in one app looks alarming in review | Ship VPN in its own release with careful review notes; every mechanism strictly opt-in with prominent disclosure |
| SharedPreferences scaling wall (one file, full rewrite per `apply()`) | Room/DataStore migration staged in Horizon 1 while data is still small |
| Ritual/service death on aggressive OEMs | AlarmManager migration + health surface + dontkillmyapp guidance (§4.2) |
| Feature breadth erodes design coherence | The §2 bar is a per-PR checklist: plain-subtitle naming, both locales, both themes, TalkBack — no exceptions |
| Social/backend scope creep | Horizon 3 features gated on retention evidence; no backend before then |

## 10. Suggested immediate next steps

1. **Compliance groundwork PR**: prominent-disclosure screen, privacy policy, data
   safety inventory, `<queries>` audit.
2. **UsageStats fallback enforcement mode** (the de-risking keystone).
3. **Backup/export/restore** (the trust keystone).
4. **Widgets + panic button + per-guard schedules** (the visible-delta trio for the
   store listing).
5. Open the Play Console, start the internal testing track, and let real-device
   pre-launch reports drive the launch-hardening list.
