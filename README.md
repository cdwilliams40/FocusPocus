# Focus Pocus

A mystical focus and productivity app for Android that helps you stay on task by blocking distracting apps. Cast spells to enter focus mode, bind talismans (NFC tags) to your rituals, and break free from digital distractions.

## Features

### App Blocking (Enchantments)
- Create custom blocklists ("Enchantments") to block distracting apps
- Choose between **Blacklist** mode (block specific apps) or **Whitelist** mode (allow only specific apps)
- Block specific websites in addition to apps
- Gentle redirection back to your task when you try to open a blocked app

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
- Optionally require a talisman tap to end a session early

### Timed Sessions with Breaks
- Set focus duration (or unlimited)
- Configurable break duration and frequency
- Breaks temporarily allow access to all apps and suspend Do Not Disturb
- Emergency break feature with weekly cadence-based cooldown

### Per-App Time Limits
- Set daily usage limits for individual apps (e.g., 30 minutes/day for social media)
- Apps are automatically blocked once they exceed their daily limit
- View per-app usage statistics on the Insights screen

### Browser URL Blocking
- Block specific websites during focus sessions
- Supports 16+ browsers including Chrome, Firefox, Edge, Brave, and DuckDuckGo

### Session Analytics & Insights
- Track completed focus sessions with start time, duration, and breaks used
- View daily app usage statistics
- Monitor block events to understand your distraction patterns

### Do Not Disturb Integration
- Automatically enable DND when focus mode is active
- Disable DND during breaks
- Respects your notification preferences

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
| **Notifications** | Show notifications for scheduled rituals |
| **Do Not Disturb Access** | Mute notifications during focus sessions |
| **Camera** | Scan QR codes to activate Quick Spells |

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

1. **Grant Accessibility Permission**: On first launch, you'll be prompted to enable the Focus Pocus accessibility service. This is required for app blocking to work.

2. **Grant Usage Access**: For per-app time limits, grant Usage Stats access in your device settings.

3. **Create an Enchantment**: Go to the Enchantments tab and create a blocklist with the apps you want to block during focus sessions.

4. **Start Focusing**: Return to the Home tab, select your enchantment, and tap "Cast Spell" to begin your focus session.

## Usage

### Home Tab
- Select a Quick Spell or customize your session
- Choose an enchantment (blocklist)
- Set duration and break preferences
- Tap "Cast Spell" to start or "Dispel" to end

### Enchantments Tab
- View and manage your blocklists
- Create new enchantments with specific apps and websites
- Edit or delete existing enchantments

### Rituals Tab
- Schedule automatic focus sessions
- Set days, times, and enchantments for each ritual
- Optionally require a talisman to end early

### Insights Tab
- View session history and focus statistics
- Monitor daily per-app usage and block events

### Profile Tab
- Manage your NFC talismans
- Set per-app time limits
- Configure break settings
- Toggle notification muting
- Change app theme (Light/Dark/System)

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Min SDK**: 29 (Android 10)
- **Target SDK**: 36
- **Architecture**: Single-activity with Compose navigation

## Project Structure

```
app/src/main/java/com/infinicada/focuspocus/
├── MainActivity.kt                    # Main UI and navigation
├── MyAccessibilityService.kt          # Background app blocking service
├── OverlayActivity.kt                 # Blocker overlay shown when app is blocked
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
├── Blocker.kt                         # Blocklist data model
├── NamedTag.kt                        # NFC tag data model
└── ui/screens/                        # Jetpack Compose screen definitions
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

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Built with [Jetpack Compose](https://developer.android.com/jetpack/compose)
- Material Design 3 components from [Material Components Android](https://github.com/material-components/material-components-android)
