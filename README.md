# Focus Pocus

[![CI](https://github.com/cdwilliams40/FocusPocus/actions/workflows/ci.yml/badge.svg)](https://github.com/cdwilliams40/FocusPocus/actions/workflows/ci.yml)

A mystical focus and productivity app for Android that helps you stay on task by blocking distracting apps. Cast spells to enter focus mode, bind talismans (NFC tags) to your rituals, and break free from digital distractions.

## Features

### App Blocking (Enchantments)
- Create custom blocklists ("Enchantments") to block distracting apps
- Choose between **Blacklist** mode (block specific apps) or **Whitelist** mode (allow only specific apps)
- Block specific websites in addition to apps
- **Auto-banish new apps**: opt an enchantment in and newly installed apps are added to it automatically, closing the loophole of installing a fresh distraction mid-session (blacklist mode only)
- Gentle redirection back to your task when you try to open a blocked app
- Optionally silence notifications from blocked apps while a session is active

### Quick Spells (Focus Presets)
- Pre-configured focus sessions with customizable duration and settings
- One-tap activation for common focus scenarios like "Deep Work", "Quick Focus", or "Sleep Mode"
- Bind Quick Spells to NFC talismans for physical activation
- Activate via QR codes or deep links (`focuspocus://preset`)

### Scheduled Rituals
- Schedule automatic focus sessions for specific days and times
- Perfect for work hours, study sessions, or bedtime routines
- Optional unbinding talisman requirement to end a ritual early
- Persists across device reboots

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
- Managed front and center on the **Pacts tab — the app's home screen**: one card per guard with its live state (sealed, pact running, limit spent, or quiet) and today's open/reflex counts
- **Pact circles**: bind one pact configuration to a blacklist enchantment and every app in it is gated with the same settings — membership follows the enchantment live, including auto-banished new installs

### Conditional Unlocks
- Earn access to blocked apps by first spending time in a productive one
- Example: unlock social media only after 30 minutes in a study app
- Unlocks can lift enchantment blocks and/or per-app time limits

### Browser URL Blocking
- Block specific websites during focus sessions
- Supports 16+ browsers including Chrome, Firefox, Edge, Brave, and DuckDuckGo

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
- FocusPocus cannot be uninstalled while it holds device-owner status, so a moment of weakness can't undo your setup
- Fully reversible from Settings (but never mid-session)

Provisioning requires a one-time `adb` command from a computer (the same steps are shown in-app under Settings → Warden Mode, with tap-to-copy commands):

1. Temporarily remove **all** accounts from the phone (Settings → Passwords & accounts). Every account can be added back as soon as setup is done.
2. Enable USB debugging (Settings → Developer options) and connect the phone to a computer with `adb`.
3. Run:

   ```bash
   adb shell dpm set-device-owner com.infinicada.focuspocus/.FocusDeviceAdminReceiver
   ```

Then enable **Grey Out Blocked Apps** in Settings → Warden Mode. The accessibility service keeps handling websites and time limits; suspension is layered on top for app blocking.

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
| **Notifications** | Show notifications for scheduled rituals and breaks |
| **Notification Access** | Silence notifications from blocked apps during focus sessions |
| **Do Not Disturb Access** | Mute notifications during focus sessions |
| **Camera** | Scan QR codes to activate Quick Spells |
| **Device Owner** (optional) | Suspend blocked apps system-wide and block uninstall in Warden Mode |

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

1. **Create an Enchantment**: Open the Spellbook tab and create a blocklist with the apps and websites you want to block during focus sessions (the Focus tab offers this directly when you have none).

2. **Start Focusing**: On the Focus tab, select your enchantment and tap "Cast" to begin your focus session.

## Usage

The app has four tabs, plus a Settings screen reached from the top bar.

### Pacts Tab (home)
Your standing protection, always the first thing you see:
- One card per guard — a **Pact** (sealed by default), a **Ward** (a daily time limit, with optional per-session cooldowns), or a **pact circle** (a pact over a whole enchantment)
- Live state on every card: *sealed (lifts in X)*, *pact running (X left)*, *limit spent*, or quiet with today's open/reflex counts
- A banner links to the Focus tab whenever a session or ritual is running
- "Make a pact" opens the unified guard editor: pick an app or enchantment, choose pact or ward, tune allowances, seals, escalation, substitutes, and daily backstops — and convert between styles anytime

### Focus Tab
- Select a Quick Spell or customize your session
- Choose one or more enchantments (blocklists)
- Set duration and break preferences
- Tap "Cast" to start or "Dispel" to end

### Spellbook Tab
Your grimoire of focus-session configuration:
- **Focus sessions**: **Enchantments** (blocklists of apps and websites) and **Rituals** (scheduled automatic sessions)
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
- **Privacy**: opt in or out of analytics

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3 (custom "Arcane Dusk" theme)
- **Min SDK**: 29 (Android 10)
- **Target SDK**: 36
- **Architecture**: Single-activity with Compose navigation, ViewModels, and repository-based data layer
- **Libraries**: Navigation Compose, Gson, ZXing (QR codes), Firebase Crashlytics & Analytics

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
├── BrowserDetector.kt                 # Website blocking for 16+ browsers
├── BlockerRepository.kt               # Blocklist persistence
├── BootCompletedReceiver.kt           # Restore state after device restart
├── Constants.kt                       # Shared constants and preference keys
├── data/                              # Repositories (blockers, presets, schedules,
│                                      #   talismans, sessions, settings, insights,
│                                      #   conditional unlocks)
├── model/                             # Data models (Blocker, FocusPreset, Schedule,
│                                      #   AppTimeLimit, ConditionalUnlock, ...)
├── limit/                             # Session cooldowns and escalating friction levels
├── handler/                           # Trigger handling (NFC, QR, deep links)
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
