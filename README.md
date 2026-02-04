# Focus Pocus

A mystical focus and productivity app for Android that helps you stay on task by blocking distracting apps. Cast spells to enter focus mode, bind talismans (NFC tags) to your rituals, and break free from digital distractions.

## Features

### App Blocking (Enchantments)
- Create custom blocklists ("Enchantments") to block distracting apps
- Choose between **Blacklist** mode (block specific apps) or **Whitelist** mode (allow only specific apps)
- Gentle redirection back to your task when you try to open a blocked app

### Quick Spells (Focus Presets)
- Pre-configured focus sessions with customizable duration and settings
- One-tap activation for common focus scenarios like "Deep Work", "Quick Focus", or "Sleep Mode"
- Bind Quick Spells to NFC talismans for physical activation

### Scheduled Rituals
- Schedule automatic focus sessions for specific days and times
- Perfect for work hours, study sessions, or bedtime routines
- Optional unbinding talisman requirement to end a ritual early

### NFC Talisman Support
- Use NFC tags as physical "talismans" to toggle focus mode
- Bind talismans to specific Quick Spells for instant activation
- Tap again to dispel the focus session

### Timed Sessions with Breaks
- Set focus duration (or unlimited)
- Configurable break duration and frequency
- Breaks temporarily allow access to all apps

### Do Not Disturb Integration
- Automatically enable DND when focus mode is active
- Disable DND during breaks
- Respects your notification preferences

## Requirements

- Android 7.0 (API 24) or higher
- NFC-capable device (optional, for talisman features)

## Permissions

Focus Pocus requires the following permissions:

| Permission | Purpose |
|------------|---------|
| **Accessibility Service** | Detect when you open apps and redirect from blocked apps |
| **NFC** | Read NFC tags for talisman features |
| **Notifications** | Show notifications for scheduled rituals |
| **Do Not Disturb Access** | Mute notifications during focus sessions |

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

2. **Create an Enchantment**: Go to the Enchantments tab and create a blocklist with the apps you want to block during focus sessions.

3. **Start Focusing**: Return to the Home tab, select your enchantment, and tap "Cast Spell" to begin your focus session.

## Usage

### Home Tab
- Select a Quick Spell or customize your session
- Choose an enchantment (blocklist)
- Set duration and break preferences
- Tap "Cast Spell" to start or "Dispel" to end

### Enchantments Tab
- View and manage your blocklists
- Create new enchantments with specific apps
- Edit or delete existing enchantments

### Rituals Tab
- Schedule automatic focus sessions
- Set days, times, and enchantments for each ritual
- Optionally require a talisman to end early

### Profile Tab
- Manage your NFC talismans
- Configure break settings
- Toggle notification muting
- Change app theme (Light/Dark/System)

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 36
- **Architecture**: Single-activity with Compose navigation

## Project Structure

```
app/src/main/java/com/infinicada/focuspocus/
├── MainActivity.kt          # Main UI and app logic
├── MyAccessibilityService.kt # Background app blocking service
├── OverlayActivity.kt       # Blocker overlay shown when app is blocked
├── DndController.kt         # Do Not Disturb management
├── Constants.kt             # Shared constants and preference keys
├── Blocker.kt              # Data class for blocklists
├── NamedTag.kt             # Data class for NFC tags
└── ui/theme/               # Material 3 theme configuration
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
