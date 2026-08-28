# Play Console — AccessibilityService declaration & package-visibility audit

*Google Play approves `AccessibilityService` use case-by-case through a
declaration form. App blockers are not "accessibility tools", so approval turns
on two things: that the permission delivers core functionality the user asked
for, and that no less-invasive API would do the job. This file is the narrative
to paste into the form, plus the evidence behind it.*

---

## 1. The declaration narrative

**What is the core functionality that requires the AccessibilityService API?**

Focus Pocus is a screen-time and focus app. Its core function — the one users
install it for — is to prevent the user from opening apps they have themselves
chosen to block, either during a focus session they started or under a standing
"pact" they configured. Doing that requires knowing, promptly, which app has
just come to the foreground.

**Why is AccessibilityService the mechanism?**

The app subscribes to a single event type, `typeWindowStateChanged`, and reads
one field from it: the package name of the app that just took the foreground.
When that package is one the user has guarded, the app returns the user to their
launcher and shows its own overlay explaining why.

The service configuration is deliberately minimal:

```xml
<accessibility-service
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="50"
    android:accessibilityFlags="flagDefault" />
```

It does **not** declare `canRetrieveWindowContent`, does not request
`flagRequestFilterKeyEvents`, and never calls `getRootInActiveWindow()`. It
therefore cannot read screen content, text fields, or keystrokes. There is no
code path in the app that reads any part of an `AccessibilityEvent` other than
`event.packageName`.

**Is there a less-invasive alternative?**

Partially, and we ship it — but it is not equivalent:

- **UsageStats polling** (`UsageStatsManager.queryEvents`) can identify the
  foreground app without accessibility. It is what the app's *fallback
  enforcement mode* uses. Its detection latency is 1–2 seconds rather than
  ~50 ms, which is the difference between a distracting app never really opening
  and it opening for a second or two first. For an impulse-control product that
  gap matters, but the mode is real, shipped and selectable, and the app is
  usable without the accessibility permission.
- **Device owner suspension** (`DevicePolicyManager.setPackagesSuspended`) is
  strictly better than either, but requires one-time `adb` provisioning from a
  computer, which the overwhelming majority of users will not do.

So the accessibility API is the mechanism that makes the core feature work well
for ordinary users, and the app degrades — visibly and by design — rather than
breaking when it is unavailable.

**How is the user informed?**

A prominent disclosure screen is shown **before** the permission prompt, both in
first-run onboarding and as a gate whenever the app asks the user to enable the
service. It states in plain language what the service observes, what it cannot
observe, and that nothing leaves the device. The user must tap "Agree &
continue" to proceed; "Not now" leaves the app fully usable.

The disclosure text is `R.string.accessibility_disclosure_body`
(`OnboardingScreen.kt`, `FocusPocusApp.kt`), and reads:

> Focus Pocus uses Android's AccessibilityService API for exactly one thing:
> noticing which app comes to the foreground, so the apps you chose to guard can
> be blocked or redirected. It listens only to window-change events — it cannot
> read screen content, keystrokes, or passwords. Nothing it observes leaves your
> device or is shared with anyone.

**What is done with the data?**

Nothing leaves the device. Foreground-package observations feed three on-device
stores, all of which the user can inspect on the Insights screen and export or
delete: block events, per-app open/reflex counts (30-day retention), and session
history. There is no server.

---

## 2. What to attach to the form

- A screen recording of the prominent disclosure, the permission prompt, and a
  block actually happening.
- A link to the privacy policy (`PRIVACY_POLICY.md`, once hosted).
- This narrative.

## 3. Package-visibility audit

**`QUERY_ALL_PACKAGES` is not requested**, and must not be. Visibility is scoped
through `<queries>` in `AndroidManifest.xml`:

| Query | What it makes visible | Why the app needs it |
|---|---|---|
| `action.MAIN` | Every launchable app | The app pickers — the user cannot guard an app they cannot pick, and the whole product is choosing which apps to guard |
| `action.VIEW` + `BROWSABLE` + `http` | Browsers | Resolving a browser for the store/support links |
| `action.VIEW` + `BROWSABLE` + `https` | Browsers | As above |

The `action.MAIN` query is broad by nature, but it is the narrowest form that
answers "what apps could the user want to block", and Play's package-visibility
policy lists device-management and app-blocking as permitted uses of broad
visibility. The declaration should say so in the same words as this table.

Two related behaviours are worth stating in the form because they *look* like
broader visibility and are not:

- `AppUtils.isWhitelistBlockable` and the pickers filter out stock system apps,
  so a whitelist session cannot strand the user outside the system UI. This
  reads `ApplicationInfo` flags for packages already visible through the query
  above; it does not widen visibility.
- `MyAccessibilityService` registers for `ACTION_PACKAGE_ADDED` so an
  enchantment with "auto-banish new apps" enabled picks up a freshly installed
  distraction. Broadcast receipt is not package visibility, and the app reads
  only the package name from the intent.

## 4. Risk posture

If the declaration is denied, or approval is revoked in a later policy round:

1. The fallback enforcement mode (Settings → Enforcement) already covers the
   blocking path without accessibility, so the app keeps working.
2. Warden (device owner) mode covers the strict case with OS-level suspension
   and no accessibility at all.
3. A build variant with the accessibility service removed entirely is therefore
   a configuration change, not a rewrite. Keep it that way: every new
   enforcement feature must be reachable from `EnforcementEngine`, which both
   detectors drive, rather than from the accessibility service directly.
