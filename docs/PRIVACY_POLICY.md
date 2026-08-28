# Focus Pocus — Privacy Policy

**Last updated: 28 August 2026**
*Applies to Focus Pocus for Android, package `com.infinicada.focuspocus`.*

## The short version

Focus Pocus is a screen-time app that works entirely on your phone. Your
blocklists, pacts, schedules, session history and streaks are stored in your
device's private app storage and are **never uploaded**. We have no server, no
account system, and no way to see how you use the app.

The single exception is optional crash reporting, described below, which you can
turn off in Settings → Privacy.

## What stays on your device

All of it. Focus Pocus stores the following in its private app storage, readable
only by the app itself:

| What | Why |
|---|---|
| Enchantments (blocklists) and the packages in them | To know what to block |
| Pacts, wards and pact circles, and their schedules | Standing protection rules |
| Rituals, Quick Spells, NFC talisman bindings | Automation you configured |
| Focus session history, streaks, block events | The Insights screen |
| Per-app open and "reflex open" counts (30 days) | Awareness counters on the pact screen and Insights |
| Mana, boons, trials, sigils | The progression layer |
| Your settings, including theme and consent choices | To remember your preferences |

None of this is transmitted anywhere. It leaves your device only when **you**
export it yourself (Settings → Backup → Export), which writes a JSON file to a
location you pick through the system file picker. That file is yours; we never
see it.

Automatic Android cloud backup and device-to-device transfer are **disabled**
for this app (`allowBackup="false"`, plus explicit exclusion rules for Android
12+), so your configuration is not silently copied to Google's servers either.

## What can leave your device

**Crash reports (optional, on by default, switchable off).** Focus Pocus uses
Firebase Crashlytics to report crashes so they can be fixed. A crash report
contains the stack trace, the device model, the OS version and the app version.
It does **not** contain your blocklists, the names of apps you guard, your usage
statistics, or any content from your screen.

You control this with **Settings → Privacy → Share Analytics**. Turning it off
stops collection immediately.

**Analytics.** The app ships with the Firebase Analytics SDK, but analytics
collection is deactivated in the app manifest and the app records **zero**
custom events. In practice no analytics data is collected. If that ever changes,
this policy will be updated before the change ships, and the same consent switch
will govern it.

**Advertising ID.** Removed from the app at build time. Focus Pocus shows no
ads and does no cross-app tracking.

## Permissions, and why each one exists

- **Accessibility service** — the app is told the *package name* of the app that
  just came to the foreground, which is how it knows to block a guarded app. It
  subscribes to window-state-change events only, and does not request the
  ability to retrieve window content. It cannot read your screen, your
  keystrokes, or your passwords. Nothing it observes is stored beyond the block
  events and open counts described above, and none of it leaves the device.
- **Usage access** (`PACKAGE_USAGE_STATS`) — daily time limits, conditional
  unlocks and the Insights screen need to know how long apps were in the
  foreground. Read on-device only.
- **Notification access** (optional) — used to silence notifications from
  blocked apps during a focus session. Notification *content* is never read,
  stored or transmitted; the app looks only at which package posted it and its
  category, so it can dismiss it. Calls and alarms are never silenced.
- **Do Not Disturb access** — to turn DND on during focus and off during breaks.
- **Notifications** — session countdowns, ritual alerts, seal-lifted notes.
- **NFC** — to read the ID of a talisman tag you tap.
- **Exact alarms** and **run at boot** — so rituals start and end on time and
  survive a restart.
- **Battery optimization exemption** — so enforcement is not killed in the
  background. Requested, never required.
- **Device owner / device admin (Warden mode, entirely optional)** — an
  opt-in mode you provision yourself over `adb`, which lets Android suspend
  guarded apps at the OS level and prevents Focus Pocus being uninstalled while
  it holds the role. Fully reversible from Settings.
- **Package visibility** — the app queries the list of launchable apps and
  browsers so it can show you an app picker. It does not use the broad
  `QUERY_ALL_PACKAGES` permission; visibility is scoped through `<queries>`
  intent filters.

## Children

Focus Pocus is not directed at children under 13 and collects no personal
information from anyone.

## Your controls

- **See and move your data** — Settings → Backup → Export writes everything to a
  JSON file you control.
- **Turn off crash reporting** — Settings → Privacy → Share Analytics.
- **Delete everything** — uninstalling the app removes all of it. (If Warden
  mode is active, remove it from Settings → Warden Mode first; removal is
  requested 24 hours in advance by design.) You can also use Android's
  Settings → Apps → Focus Pocus → Storage → Clear data.
- **Delete crash reports already sent** — email the address below and we will
  delete them.

## Changes

If this policy changes materially, the updated version will be published here
and the "last updated" date above will change before the new behaviour ships.

## Contact

Questions, or a request to delete crash-report data:
**cdwilliams40@gmail.com**
