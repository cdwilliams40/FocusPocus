# Play Console — Data safety form

*The answers to fill into Play Console → App content → Data safety, with the
evidence for each. Re-check this file whenever a dependency is added or a
`logEvent` call is introduced — the form must match the shipped binary, and a
wrong answer here is a policy violation regardless of intent.*

**Privacy policy URL:** publish `docs/PRIVACY_POLICY.md` and paste the URL.
The same URL goes in the app listing *and* is linked in-app under
Settings → Privacy.

---

## Section 1 — Data collection and sharing

> Does your app collect or share any of the required user data types?

**Yes** — one type only, and only with consent: **Crash logs**.

Everything else the app holds is stored in app-private storage on the device
and never transmitted, which Play defines as *not* collection.

## Section 2 — Data types

| Category | Type | Collected | Shared | Notes |
|---|---|---|---|---|
| App activity | App interactions | No | No | Session/open/block history is on-device only |
| App activity | Other user-generated content | No | No | Blocklists and pacts never leave the device |
| App info & performance | **Crash logs** | **Yes** | No | Firebase Crashlytics, consent-gated |
| App info & performance | Diagnostics | No | No | Analytics SDK present but deactivated; zero `logEvent` calls |
| Device or other IDs | Device or other IDs | No | No | `AD_ID` permission removed at build time |
| Personal info | — | No | No | No accounts, no sign-in, no contact details |
| Location | — | No | No | No location permissions requested |
| Files & docs | — | No | No | Export writes only to a file the user picks via SAF |

### Crash logs — the required follow-ups

- **Collected or shared?** Collected. Not shared with third parties for their
  own purposes; Firebase is a processor.
- **Processed ephemerally?** No — crash reports persist in the Crashlytics
  console.
- **Required or optional?** **Optional.** Settings → Privacy → Share Analytics
  switches it off, and the switch takes effect immediately
  (`setCrashlyticsCollectionEnabled`).
- **Purpose:** App functionality; Analytics (crash diagnosis).

## Section 3 — Security practices

- **Encrypted in transit?** Yes — Firebase transport is HTTPS.
- **Can users request deletion?** Yes — an email address in the privacy policy,
  plus in-app deletion of all local data by clearing app storage or
  uninstalling.
- **Independent security review?** No.

---

## Standing evidence

Facts the answers above rest on. Each is cheap to re-verify before a release:

| Claim | How to check |
|---|---|
| No custom analytics events | `grep -rn "logEvent" app/src/main` returns nothing |
| Analytics deactivated by default | `firebase_analytics_collection_deactivated=true` in `AndroidManifest.xml` |
| Crashlytics off until consent | `firebase_crashlytics_collection_enabled=false` in the manifest; `MainActivity` applies the stored consent on every start |
| No advertising ID | `<uses-permission android:name="com.google.android.gms.permission.AD_ID" tools:node="remove" />` |
| No cloud backup of app data | `allowBackup="false"`, plus `data_extraction_rules.xml` / `backup_rules.xml` excluding every domain |
| No broad package visibility | No `QUERY_ALL_PACKAGES`; see `PLAY_ACCESSIBILITY_DECLARATION.md` §3 |
| No location, contacts, camera, mic, or storage permissions | Read the `<uses-permission>` block in `AndroidManifest.xml` — it is short on purpose |

## Open item

The consent switch is labelled "Share Analytics" and governs Crashlytics.
Analytics itself is currently held off by
`firebase_analytics_collection_deactivated`, a manifest flag that **cannot** be
re-enabled at runtime — so the switch cannot turn analytics on even if it looks
like it should. This is more private than the label implies, not less, so it is
not a compliance problem; but if the minimal opt-in event set from the roadmap's
continuous-engineering track is ever built, that manifest flag has to become
`firebase_analytics_collection_enabled="false"` for the switch to actually
govern it — and this form's "Diagnostics" row changes at the same time.
