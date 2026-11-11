# Focus Pocus

[![CI](https://github.com/cdwilliams40/FocusPocus/actions/workflows/ci.yml/badge.svg)](https://github.com/cdwilliams40/FocusPocus/actions/workflows/ci.yml)

A mystical focus app for Android built on three ideas: your most distracting apps stay closed until you make a conscious **pact**, paid from one slowly refilling pool of time — the **Mana Well**; **focus sessions** block whole lists of apps, started by hand, on a schedule, or with an NFC talisman; and **mana**, earned for focusing, buys extra time when you really need it.

## Features

### The Mana Well
- Put your worst distractions behind the Well. A Well app is **closed by default**, not just during focus sessions
- Opening one offers a **pact**: pick 2, 5, 10, 15 or 30 minutes — after a three-second pause, so muscle memory can't answer for you — and the minutes come out of the Well
- Every Well app draws from **the same pool**, so hopping to the next app when one runs out spends the same time
- The Well holds 30 minutes by default and **refills on its own** (one minute every 20 by default); there is no midnight reset to binge against
- When a pact runs out the app simply closes; a toast warns you a minute before
- **End pact** closes a running pact early and pours the unused minutes back
- The pact screen shows today's opens and quick "reflex" opens, and can offer a **healthier substitute** ("Open Kindle instead") first
- When the Well is dry, the screen says when time will be back and offers a **pour** of mana
- **Taking an app off the Well waits 24 hours**, and loosening the Well's capacity or refill rate does too; adding apps and tightening are instant

### Focus Sessions
- **Enchantments**: blocklists in **Blacklist** mode (block these apps) or **Whitelist** mode (allow only these); optionally auto-add newly installed apps
- **Timed or open-ended** sessions from the Focus tab, with breaks and optional **auto-breaks (Pomodoro)**
- **Rituals**: scheduled sessions for set days and times, with an exact-alarm backstop so they start and end on time; a ritual can require its **talisman** to end
- **Talismans**: NFC tags. Each has its own setup (enchantments, length, breaks). Tap to start its session; tap again to end it. By default **only that talisman can end its session**
- **Talisman lock**: optionally block Settings apps during a session
- Live countdown notification, Do Not Disturb while focusing, silenced notifications from blocked apps, a hidden stop button option, and a rare emergency stop

### Mana
Every number lives in one place (`ManaRules.kt`) and the app shows the same rules on the Mana page:

| Earn | |
|---|---|
| Timed focus session | 1 per minute, up to 240 a day |
| Ritual, or a talisman session with no length | 30 once a day, after an hour |
| Time in a **productive app** you choose | 1 per 2 minutes, up to 60 a day |
| Daily trials (two a day) / weekly trial | 40–60 each / 150–200 |

| Spend | |
|---|---|
| Pour 5 minutes into the Well | 75, doubling with each pour that day |
| One extra break in a session | 50 |

Trials include focusing in timed sessions, starting one by hand, keeping Well draws or reflex opens under a limit, and time in productive apps.

### Insights
One switch — today, 7 days, 30 days — for every section: minutes drawn from the Well (per day, per app, pacts, pours, dry opens), reflex opens, focus minutes by source (by hand, rituals, talismans) with blocked attempts and session history, mana earned by source and spent on pours, your streak, and screen time. Tap any bar to read its day.

### Protection Health
- A Settings card answers "am I actually protected right now?": accessibility service alive, usage access granted, notifications allowed, battery optimization exempted — each with a one-tap fix

### Backup & Restore
- Export enchantments, Well apps, rituals, talismans, productive apps, history and settings to a JSON file; restore on a reinstall or a new phone
- Running sessions, running pacts and the Well's level stay on the device
- A restore can't be used to break a commitment: it's refused during a focus session, it can add Well apps but only queues the removal of ones it leaves out, and it can't loosen the Well without the usual 24 hours
- Backups from older versions restore through the same migration an upgrade runs

### Warden Mode (Device Owner)
- Optionally provision FocusPocus as the Android *device owner* for OS-level enforcement
- Blocked apps are **suspended** during focus sessions: their launcher icons grey out and the system refuses to open them — no race with the accessibility service
- **Well apps stay greyed out around the clock** — suspended (and absent from launcher suggestions) whenever no pact is running. To open one, tap it in the **Request time** panel on the Well tab; the pact un-greys the app for exactly the minutes you chose
- FocusPocus cannot be uninstalled while it holds device-owner status, so a moment of weakness can't undo your setup
- Fully reversible from Settings (but never mid-session), and removal must be **requested 24 hours in advance** — a cancelable cooling-off countdown separates the urge from the act

Provisioning requires a one-time `adb` command from a computer (the same steps are shown in-app under Settings → Warden Mode, with tap-to-copy commands):

1. Temporarily remove **all** accounts from the phone (Settings → Passwords & accounts). Every account can be added back as soon as setup is done.
2. Enable USB debugging (Settings → Developer options) and connect the phone to a computer with `adb`.
3. Run:

   ```bash
   adb shell dpm set-device-owner com.infinicada.focuspocus/.FocusDeviceAdminReceiver
   ```

Then enable **Grey Out Blocked Apps** in Settings → Warden Mode (**Grey Out Well Apps** rides on the same switch and is on by default). The accessibility service stays the fallback; suspension is layered on top.

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
| **Usage Stats** | Count time in productive apps (mana) and show screen time in Insights |
| **NFC** | Read NFC tags for talisman features |
| **Notifications** | Show notifications for scheduled rituals and breaks, plus the live countdown while a session is active |
| **Notification Access** | Silence notifications from blocked apps during focus sessions |
| **Do Not Disturb Access** | Mute notifications during focus sessions |
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

On first launch, a guided onboarding flow puts your first apps behind the Mana Well and walks you through the permissions (accessibility, notification access, usage access, and optional analytics). You then land on the Well tab. From there:

1. **Create an Enchantment** in the Spellbook (the Focus tab offers this when you have none), then cast a session from the Focus tab.
2. **Schedule a Ritual** for sessions that should start on their own, and **bind a Talisman** if you want an NFC tag to end it — or to start and end its own session.
3. **Choose productive apps** (Spellbook → Productive apps) so time in them earns mana.

## Usage

The app has four tabs, plus Settings and the Mana page reached from the top bar and the mana chip.

### Well Tab (home)
- The **Mana Well** card: what's left, when it's full again, minutes drawn today, and a pour button with today's price
- **Today's trials**, a **Request time** grid of your Well apps (most-opened first), and running pacts with **End pact**
- The Well apps list and **Edit Well apps**: add apps, set substitutes, take apps off (24 hours)

### Focus Tab
- Choose enchantments, a length and breaks, and tap **Cast**; take or end breaks while a session runs
- While a ritual or a locking talisman's session runs, the stop button shows **Bound**

### Spellbook Tab
- **Enchantments** and **Rituals**
- **Talismans** (bind a tag and set up what it starts) and **Productive apps**

### Insights Tab
- See [Insights](#insights) above

### Settings
- Appearance, focus behaviour (breaks, auto-breaks, emergency stop, hide stop button), notifications, talisman lock, the Mana Well's capacity and refill, Warden Mode, Protection Health, backup, and privacy

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3 (custom "Arcane Dusk" theme)
- **Min SDK**: 29 (Android 10)
- **Target SDK**: 36
- **Architecture**: Single activity, Compose UI with ViewModels, repository-based data layer on SharedPreferences, versioned migrations (`Migrations.kt`)
- **Libraries**: Gson, Firebase Crashlytics & Analytics

## Project Structure

```
app/src/main/java/com/infinicada/focuspocus/
├── MainActivity.kt                    # Single activity; NFC tag reading
├── FocusPocusApplication.kt           # Initialization; runs Migrations
├── MyAccessibilityService.kt          # Enforcement: sessions, the Well, timers, breaks
├── OverlayActivity.kt                 # Block screen and the pact / dry-Well screens
├── SessionManager.kt, SessionRecorder.kt  # Session state and history
├── Progression.kt, ManaRules.kt, TrialEngine.kt  # Mana, its rules, and trials
├── ProductiveApps.kt                  # Productive apps that earn mana
├── Migrations.kt                      # Stored-data schema steps
├── BackupCodec.kt                     # Backup export / import
├── DeviceOwnerManager.kt              # Warden Mode
├── handler/TriggerHandler.kt          # What an NFC tap means
├── limit/                             # ManaWell, WellApps, PactManager, DailyHistory
├── insights/InsightsReport.kt         # Every Insights figure, from stored history
├── data/, model/, navigation/, viewmodel/
└── ui/                                # Theme, components, and screens
```

Design notes: [`docs/DESIGN.md`](docs/DESIGN.md).

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
