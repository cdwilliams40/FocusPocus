# Focus Pocus

[![CI](https://github.com/cdwilliams40/FocusPocus/actions/workflows/ci.yml/badge.svg)](https://github.com/cdwilliams40/FocusPocus/actions/workflows/ci.yml)

A mystical focus and productivity app for Android that helps you stay on task by blocking distracting apps. Cast spells to enter focus mode, bind talismans (NFC tags) to your rituals, and break free from digital distractions.

## Features

### App Blocking (Enchantments)
- Create custom blocklists ("Enchantments") to block distracting apps
- Choose between **Blacklist** mode (block specific apps) or **Whitelist** mode (allow only specific apps)
- **Auto-banish new apps**: opt an enchantment in and newly installed apps are added to it automatically, closing the loophole of installing a fresh distraction mid-session (blacklist mode only)
- Gentle redirection back to your task when you try to open a blocked app
- Optionally silence notifications from blocked apps while a session is active

### Quick Spells (Focus Presets)
- Pre-configured focus sessions with customizable duration and settings
- One-tap activation for common focus scenarios like "Deep Work", "Quick Focus", or "Sleep Mode"
- Bind Quick Spells to NFC talismans for physical activation
- Activate via deep links (`focuspocus://preset`)

### Scheduled Rituals
- Schedule automatic focus sessions for specific days and times
- Perfect for work hours, study sessions, or bedtime routines
- Optional unbinding talisman requirement to end a ritual early
- Persists across device reboots
- Backed by an exact-alarm backstop: rituals start and end on time even if the accessibility service was killed

### NFC Talisman Support
- Use NFC tags as physical "talismans" to toggle focus mode
- Bind talismans to specific Quick Spells for instant activation
- Tap again to dispel the focus session
- Optionally require a talisman tap to end a session early (talisman lock)

### Timed Sessions with Breaks
- Set focus duration (or unlimited)
- Configurable break duration and maximum breaks per session
- **Auto-breaks (Pomodoro)**: optionally start a break automatically after each uninterrupted focus stretch (5–60 minutes, default 25)
- Breaks temporarily allow access to all apps and suspend Do Not Disturb
- Emergency break feature with weekly cadence-based cooldown
- Session and break timers are enforced by the background service using wall-clock timestamps, so they end on time even if the app is closed or the device reboots
- **Live session notification**: while a session runs, a silent ongoing notification names the active spell or ritual and ticks down the time until it ends — or until focus resumes when you're on a break; untimed sessions show elapsed time instead
- Optionally hide the stop button so a session can't be ended early on impulse

### Per-App Time Limits (Wards)
- Set daily usage limits for individual apps (e.g., 30 minutes/day for social media)
- Apps are automatically blocked once they exceed their daily limit
- **Session limits with cooldowns**: cap continuous use of an app (e.g., 10 minutes at a time), after which it's blocked for a configurable cooldown period — optionally escalating on repeated offences in the same day
- **Escalating friction**: each attempt to open a cooled-down app makes the block overlay harder to dismiss, from a short countdown up to typing a reflective phrase
- View per-app usage statistics on the Insights screen

### Pact Mode (Blocked by Default)
- Flip the default for your worst distractions: the app is **sealed at all times**, not just during focus sessions
- Opening it offers a *pact*: consciously choose how many minutes you need — after a short pause, so muscle memory can't answer for you
- When your time is spent, the app seals itself again for a cooldown of your choosing, with the same escalating friction as session limits
- A warning toast fires shortly before a pact lapses, so the seal never lands mid-scroll without notice
- The pact screen shows **the shape of today's habit**: how many times you've opened the app and how many opens were under-30-second reflexes
- Optionally suggest a **healthier substitute** ("Open Kindle instead") — shown as the most prominent button and usable immediately, while pact choices wait out the anti-reflex pause
- Designed to break the reflexive reach-for-the-phone loop: most impulse opens don't survive a deliberate choice plus a three-second pause
- Optional **daily backstop**: cap total daily use on top of the pact gate, or leave it off entirely
- Managed front and center on the **Pacts tab — the app's home screen**: a **Request time** panel offers every requestable app as a one-tap icon grid, and the guards below it split into *Happening now* (seals, running pacts, spent limits — with countdowns) and the standing guard list, each card showing its live state and today's open/reflex counts
- **Pact circles**: bind one pact configuration to a blacklist enchantment and every app in it is gated with the same settings — membership follows the enchantment live, including auto-banished new installs
- **A pact binds for a day**: changing or removing an existing pact (or circle) takes effect only 24 hours after you confirm it — the current terms stay enforced in the meantime, and you can cancel the pending change to keep them. Creating a new pact, and tuning wards, stays instant

### Guard Hours (Per-Guard Schedules)
- Give any pact, ward, or pact circle a schedule: active days and an optional daily time window ("this pact only applies 21:00–07:00 on weekdays")
- Overnight windows wrap past midnight with the same semantics as rituals
- Outside its hours a guard stands down — the app is free, and Warden greying releases it — though a running seal always holds
- The dashboard shows each guard's hours and an **Off hours** state

### Seal Everything Now (Panic Button)
- One tap on the Pacts dashboard seals every pact-bound app for its configured seal length and revokes running pact time
- Sealing this way never counts as an offence — choosing protection doesn't make later real lapses harsher

### Quick Settings Tile & Home-Screen Widget
- A **Quick Settings tile** casts your first Quick Spell, or dispels a running session, from the notification shade
- The tile is not a back door: talisman lock, hide-stop-button and a talisman-bound ritual hold there exactly as they do in the app, and it opens the app to explain rather than failing silently
- A **home-screen widget** says what is holding right now — the session countdown, the break, or how many apps your guards have sealed — and taps through to the app

### Seal-Lifted Alerts
- Optional quiet notification when a sealed app opens up again, so you don't "check" by opening the app (off by default)

### Enforcement Modes
- **Accessibility service** (default): Android reports a foreground change in ~50 ms, so a guarded app never really finishes opening
- **Usage-access fallback**: the same blocking, driven by polling usage access about once a second, with no accessibility permission at all — slower (1–2 s) and it posts an ongoing notification, but it means a denied or revoked Play accessibility declaration can't take the app down with it
- Both modes run the identical decision engine; only the foreground detector differs. Polling stops entirely while the screen is off

### Protection Health
- A Settings card answers "am I actually protected right now?": accessibility service alive, usage access granted, notifications allowed, battery optimization exempted — each with a one-tap fix
- The dashboard warns when an OEM battery optimizer could kill enforcement in the background

### Backup & Restore
- Export enchantments, guards, rituals, presets, talismans, history, and settings to a JSON file; restore on a reinstall or a new phone
- Live session state and running seals deliberately stay on the device, so a restore can't resurrect a stale seal
- Restores are versioned, validated, and applied atomically before an automatic app restart

### Conditional Unlocks
- Earn access to blocked apps by first spending time in a productive one
- Example: unlock social media only after 30 minutes in a study app
- Unlocks can lift enchantment blocks and/or per-app time limits

### Session Analytics & Insights
- Track completed focus sessions with start time, duration, and breaks used
- View daily app usage trends and per-app statistics
- Monitor block events to understand your distraction patterns
- **App Opens**: per-app open counts with reflex-open breakdown (opens abandoned within 30 seconds), filterable by day/week/month, with 30 days of history

### Do Not Disturb Integration
- Automatically enable DND when focus mode is active
- Disable DND during breaks
- Respects your notification preferences

### Warden Mode (Device Owner)
- Optionally provision FocusPocus as the Android *device owner* for OS-level enforcement
- Blocked apps are **suspended** during focus sessions: their launcher icons grey out and the system refuses to open them — no race with the accessibility service
- **Pact-bound apps stay greyed out around the clock** — suspended (and absent from launcher suggestions) whenever no pact allowance is running. To open one, tap it in the **Request time** panel at the top of the Pacts dashboard; the allowance un-greys the app for exactly the minutes you chose
- FocusPocus cannot be uninstalled while it holds device-owner status, so a moment of weakness can't undo your setup
- Fully reversible from Settings (but never mid-session), and removal must be **requested 24 hours in advance** — a cancelable cooling-off countdown separates the urge from the act

Provisioning requires a one-time `adb` command from a computer (the same steps are shown in-app under Settings → Warden Mode, with tap-to-copy commands):

1. Temporarily remove **all** accounts from the phone (Settings → Passwords & accounts). Every account can be added back as soon as setup is done.
2. Enable USB debugging (Settings → Developer options) and connect the phone to a computer with `adb`.
3. Run:

   ```bash
   adb shell dpm set-device-owner com.infinicada.focuspocus/.FocusDeviceAdminReceiver
   ```

Then enable **Grey Out Blocked Apps** in Settings → Warden Mode (**Grey Out Pact Apps** rides on the same switch and is on by default). The accessibility service keeps handling time limits; suspension is layered on top for app blocking.

> **⚠️ Uninstall protection and Android Studio builds:** apps deployed with Android Studio's *Run* button are flagged **test-only**, and Android deliberately allows removing a test-only device owner — so FocusPocus *can* still be uninstalled in that setup, Warden Mode or not. For real uninstall protection, install a normal build instead: `./gradlew assembleDebug && adb install app/build/outputs/apk/debug/app-debug.apk` (or a release build). The app shows a warning in Settings → Warden Mode when it detects a test-only install.

<details>
<summary><strong>Troubleshooting: "Not allowed to set the device owner because there are already some accounts on the device"</strong></summary>

Android refuses to set a device owner while *any* account is registered — including hidden ones that never appear in Settings. Apps like Instagram, WhatsApp, and Signal register accounts of their own, and OEM accounts (Samsung, Xiaomi, …) live outside the Google account list. To see what's actually left:

```bash
adb shell dumpsys account   # each "Account {name=..., type=...}" names the owning app
adb shell pm list users     # extra users and work profiles count too
```

Uninstall (or clear the storage of) each app that still owns an account, remove extra users with `adb shell pm remove-user <id>`, reboot, and run the `set-device-owner` command again. As a last resort, a factory reset with every sign-in step skipped gives a clean slate. Nothing is lost permanently: once Warden Mode is active, all accounts and apps can be restored — the no-accounts rule only applies at the moment of provisioning.

</details>

## Requirements

- Android 10.0 (API 29) or higher
- NFC-capable device (optional, for talisman features)

## Permissions

Focus Pocus requires the following permissions:

| Permission | Purpose |
|------------|---------|
| **Accessibility Service** | Detect when you open apps and redirect from blocked apps |
| **Usage Stats** | Track per-app usage for time limit enforcement |
| **NFC** | Read NFC tags for talisman features |
| **Notifications** | Show notifications for scheduled rituals and breaks, plus the live countdown while a session is active |
| **Notification Access** | Silence notifications from blocked apps during focus sessions |
| **Do Not Disturb Access** | Mute notifications during focus sessions |
| **Device Owner** (optional) | Suspend blocked apps system-wide and block uninstall in Warden Mode |
| **Foreground Service** | Only in the usage-access fallback enforcement mode, to keep watching for guarded apps while you are in another app |

## Privacy

Everything Focus Pocus knows about you stays in the app's private storage on your
phone — there is no server and no account. Blocklists, pacts, rituals, history and
streaks are never uploaded, and automatic Android cloud backup is disabled for the
app so they aren't quietly copied off either. They leave the device only through
the Grimoire export, to a file you choose.

The one exception is crash reports (Firebase Crashlytics), which you can turn off
under Settings → Privacy → Share Analytics. The app records no custom analytics
events and requests no advertising ID.

The full policy is [`docs/PRIVACY_POLICY.md`](docs/PRIVACY_POLICY.md), summarized
in-app under Settings → Privacy → Privacy policy. The Play Console paperwork it
supports lives beside it:
[data safety inventory](docs/PLAY_DATA_SAFETY.md) and
[accessibility declaration & package-visibility audit](docs/PLAY_ACCESSIBILITY_DECLARATION.md).

## Installation

### From Source
1. Clone this repository
2. Open in Android Studio
3. Build and run on your device

```bash
git clone https://github.com/cdwilliams40/FocusPocus.git
cd FocusPocus
./gradlew assembleDebug
```

## Setup

On first launch, a guided onboarding flow seals your first apps behind pacts and walks you through the required permissions (accessibility, Do Not Disturb, usage access, and optional analytics). After that you land on the Pacts dashboard with your new pacts live. For timed focus sessions:

1. **Create an Enchantment**: Open the Spellbook tab and create a blocklist with the apps you want to block during focus sessions (the Focus tab offers this directly when you have none).

2. **Start Focusing**: On the Focus tab, select your enchantment and tap "Cast" to begin your focus session.

## Usage

The app has four tabs, plus a Settings screen reached from the top bar.

### Pacts Tab (home)
Your standing protection, always the first thing you see:
- A **Request time** panel up top: every app you can currently request pact time for, as a one-tap icon grid, most-opened-today first — tap the app, sit out the anti-reflex pause, pick your minutes, and it opens
- Below it, guards split into **Happening now** (sealed, pact running, or limit spent — with live countdowns) and **Your pacts & wards** — one compact card per **Pact** (sealed by default), **Ward** (a daily time limit, with optional per-session cooldowns), or **pact circle** (a pact over a whole enchantment); pact and circle cards also show today's open/reflex counts
- Tap any card to open the unified guard editor with its full settings: allowances, seals, escalation, substitutes, daily backstops, guard hours — and convert between styles anytime
- A banner links to the Focus tab whenever a session or ritual is running
- "Make a pact" creates a new guard in the same editor; "Seal everything now" is the panic button

### Focus Tab
- Select a Quick Spell or customize your session
- Choose one or more enchantments (blocklists)
- Set duration and break preferences
- Tap "Cast" to start or "Dispel" to end
- **Activate any talisman by hand** — casting its bound Quick Spell or starting a talisman session exactly as a physical NFC tap would

### Spellbook Tab
Your grimoire of focus-session configuration:
- **Focus sessions**: **Enchantments** (app blocklists) and **Rituals** (scheduled automatic sessions)
- **More magic**: **Quick Spells** (one-tap presets, also on the Focus tab), **Talismans** (NFC tags and bindings), and **Conditional Unlocks** (earn access after productive time)

### Insights Tab
- App opens with reflex breakdown, right below your streaks
- View session history and focus statistics
- Daily usage trends, per-app usage, and most-blocked apps

### Settings
- **Appearance**: Light/Dark/System theme
- **Focus Behavior**: break duration, breaks per session, auto-breaks (Pomodoro interval), emergency break cadence, hide stop button
- **Notifications**: mute notifications from blocked apps during focus
- **Security**: talisman lock (require an NFC tap to end sessions)
- **Enforcement**: choose the accessibility service or the usage-access fallback
- **Privacy**: opt in or out of analytics, and read the privacy policy

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3 (custom "Arcane Dusk" theme)
- **Min SDK**: 29 (Android 10)
- **Target SDK**: 36
- **Architecture**: Single-activity with Compose navigation, ViewModels, and repository-based data layer
- **Libraries**: Navigation Compose, Gson, Firebase Crashlytics & Analytics

## Project Structure

```
app/src/main/java/com/infinicada/focuspocus/
├── MainActivity.kt                    # Single activity hosting the Compose UI
├── FocusPocusApplication.kt           # Application class and initialization
├── MyAccessibilityService.kt          # Background blocking, timer/break enforcement, auto-breaks
├── FocusNotificationListenerService.kt# Silences notifications from blocked apps
├── OverlayActivity.kt                 # Blocker overlay shown when an app is blocked
├── SessionManager.kt                  # Focus session state management
├── SessionRecorder.kt                 # Session history persistence
├── TimeLimitChecker.kt                # Per-app daily time limit enforcement
├── AppTimeLimitManager.kt             # Time limit configuration persistence
├── DndController.kt                   # Do Not Disturb management
├── UsageStatsHelper.kt                # Device usage statistics queries
├── BlockerRepository.kt               # Blocklist persistence
├── BootCompletedReceiver.kt           # Restore state after device restart
├── Constants.kt                       # Shared constants and preference keys
├── data/                              # Repositories (blockers, presets, schedules,
│                                      #   talismans, sessions, settings, insights,
│                                      #   conditional unlocks)
├── model/                             # Data models (Blocker, FocusPreset, Schedule,
│                                      #   AppTimeLimit, ConditionalUnlock, ...)
├── limit/                             # Session cooldowns and escalating friction levels
├── handler/                           # Trigger handling (NFC, deep links)
├── navigation/                        # Navigation routes and destinations
├── viewmodel/                         # ViewModels for session, settings, spellbook, insights
└── ui/                                # Theme, shared composables, and screens
    ├── theme/                         # Arcane Dusk Material 3 theme
    └── screens/                       # Compose screens (Home, Spellbook, Insights,
                                       #   Settings, Onboarding, editors, ...)
```

## Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License.

## Acknowledgments

- Built with [Jetpack Compose](https://developer.android.com/jetpack/compose)
- Material Design 3 components from [Material Components Android](https://github.com/material-components/material-components-android)
