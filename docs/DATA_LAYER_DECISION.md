# The SharedPreferences → Room/DataStore migration: deferred, and why

*Roadmap §4.2 lists "data layer hardening" as Horizon 1 work: move
history-shaped data to Room and settings to DataStore, because a single
SharedPreferences file is rewritten in full on every `apply()`. §9 lists
"SharedPreferences scaling wall" as a risk to mitigate while the data is still
small.*

**Decision: not in this pass. The premise has already been mitigated by
something cheaper, and doing it now would be the riskiest change in the
codebase bought with the smallest benefit.** Revisit against the trigger
conditions at the bottom, not on a schedule.

## Why the scaling wall is further away than it looks

The risk assumes the file grows without bound. It does not — every store that
grows with use is capped, and the caps now live together in `Constants`:

| Store | Cap | Worst case on disk |
|---|---|---|
| `FOCUS_SESSIONS` | `MAX_FOCUS_SESSIONS` = 500 | ~75 KB |
| `BLOCK_EVENTS` | `MAX_BLOCK_EVENTS` = 1000 | ~120 KB |
| `MANA_LEDGER` | `MAX_MANA_LEDGER` = 300 | ~30 KB |
| `APP_OPEN_STATS` | `OpenReflexTracker.RETENTION_DAYS` = 30 | ~90 KB at 50 tracked apps |
| `INSTALLED_APPS_CACHE` | the number of installed apps | ~36 KB at 300 apps |
| Everything else (blockers, pacts, rituals, presets, talismans, settings) | `MAX_*` caps, user-authored | single-digit KB |

That is a ceiling around **350 KB**, reached only by a heavy user after months.
It is not a wall; it is a shelf.

The second half of the premise — repeated full-file parse cost — was already
addressed, and more precisely than a database would have. The enforcement
engine, `SessionCooldownManager`, `PactManager` and `OpenReflexTracker` each
cache their parsed store keyed on the raw stored JSON, so the hot path (a
blocked open, which reads the cooldown store three times in a row) parses once.
`UsageStatsHelper` and `TimeLimitChecker` cache the expensive UsageStats scans
behind short TTLs for the same reason. Room would not have made those reads
faster; it would have made them asynchronous, which on the accessibility
service's main thread is a different shape of the same problem.

## What the migration would actually cost

Every store is read by the enforcement engine on the main thread of a service
whose failure mode is *silent*: if enforcement stalls or throws, nothing is
blocked and the user finds out by relapsing. Room's API is suspending, so
migrating a store means either blocking on it (worse than today) or making the
engine's decision path asynchronous — which changes the ordering guarantees
that several subtle behaviours rest on, among them `takeLapsedAllowance`'s
take-once semantics against the racing seal path, and the debounce that stops
one blocked app suppressing checks for the next.

That is a large, high-stakes change whose payoff today is a few milliseconds on
a write path that is already off the main thread.

## What would change the answer

Take this up when any of these becomes true — each is a real signal rather than
a calendar date:

1. **A cap has to rise.** The moment "keep a year of history" or "show the
   full grimoire" is a feature, the caps are the constraint and a database is
   the answer. This is the most likely trigger.
2. **Cloud sync (Horizon 3) starts.** Sync needs per-record change tracking and
   conflict resolution; a JSON blob per key cannot express either. The backup
   format designed in `BackupCodec` is the wire format, but the *store* has to
   be row-shaped by then.
3. **`QueuedWork.waitToFinish()` shows up in ANR reports.** That is the
   mechanism by which a big preferences file actually hurts — a blocking flush
   at activity stop. It is measurable in Play's vitals, and it is the honest
   trigger for "the shelf became a wall".
4. **A store loses its cap.** If a new feature adds a growing key without one,
   the table above stops being true. That is what the table is for.

## The cheaper mitigation that is in place

Not migrating is only defensible because the caps are real and discoverable.
They now all live in `Constants` (`MAX_FOCUS_SESSIONS` was previously an
unexplained `500` at its write site) alongside a pointer to this document, so
the bound that makes this decision safe is maintained where the next person
will see it.
