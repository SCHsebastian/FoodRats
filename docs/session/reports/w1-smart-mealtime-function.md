# Report — `w1-smart-mealtime-function`

Server-side "smart mealtime" (roadmap §1.4): compute each crew's typical meal-posting hours and
gate the existing hourly `streakNudge` (§1.1) so a crew is only nudged during its learned active
window, instead of at an arbitrary UTC hour. **Server / Cloud-Functions layer only.**

## Status: DONE — verified green.

## Files changed

- **NEW** `functions/src/triggers/mealtimeProfile.ts` — the pure, unit-testable core.
- `functions/src/triggers/streakNudge.ts` — wired the gate into `processCrewNudge`; extended deps.
- **NEW** `functions/__tests__/mealtimeProfile.test.ts` — profile-computation tests (13).
- `functions/__tests__/streakNudge.test.ts` — updated to the new signature + 7 new gating tests.

No changes to `crewScan.ts`, `weeklyDigest.ts`, `index.ts`, the message contract (`i18n/keys.ts`),
or any client/Kotlin code.

## The profile algorithm (`mealtimeProfile.ts`)

Pure functions, no I/O, fully deterministic:

1. **`buildHourHistogram(timestampsMs)`** → a 24-slot array, index = UTC hour, value = count of
   posting samples in that hour. Malformed timestamps (non-finite / negative) are ignored.
2. **`deriveActiveWindow(histogram)`** → an `ActiveWindow { startHour, endHour, source }`:
   - If total samples `< MIN_SAMPLES_FOR_PROFILE` (5) → `FALLBACK_WINDOW` (11–14 UTC, midday;
     matches the client's old fixed-14:00 `DailyInactivityWorker` "around lunch" intent),
     `source: "fallback"`.
   - Otherwise find the **peak hour** (most samples; ties → earlier hour for determinism), then
     **grow a contiguous band outward from the peak** around the 24h ring — each step adds whichever
     adjacent hour (treating the clock as a ring, so it wraps past midnight) holds more samples — until
     the band covers ≥ `WINDOW_COVERAGE` (75%) of all samples. `source: "learned"`.
   - This yields a *narrow* window for a tightly-clustered crew and a *wider* one for a crew that
     eats across two slots (e.g. lunch + dinner), and naturally produces midnight-wrapping windows
     (e.g. 22..2) for late-night crews.
3. **`isHourInWindow(hour, window)`** → inclusive membership, correctly handling a wrapped window
   (`startHour > endHour`).
4. **`computeMealtimeProfile(timestampsMs)`** → bundles histogram + sampleCount + window.

## Timezone decision (the honest constraint)

**There is NO per-user or per-crew timezone stored anywhere.** I verified `AccountDto` and `CrewDto`
carry none, and the Firestore meal doc (`MealDto`) persists only `publishedAtEpochMs` (a UTC epoch)
and `dayKey` (`YYYY-MM-DD`). The `publishedHour`/`publishedMinute` the spec §1.4 mentions are
**device-local values derived at read time on the client (`FeedMealUi`) and never written back to
Firestore** — the server cannot see them.

→ I did **not invent a timezone.** I took **approach (a)** from the brief: derive the active window
from the observed posting-hour distribution **in UTC**. For a closed crew of 3–8 friends (almost
always one region) the dominant UTC posting hours track when they actually eat — their fixed local
offset is baked into every timestamp — so the window is "smart" relative to real behavior. A true
per-recipient local hour, and correct handling of multi-region crews, requires capturing a real
timezone. **That is a flagged client follow-up** (see handoff).

## What is gated

- `processCrewNudge(crewId, dayKey, currentHourUtc, deps)` now **computes the crew's mealtime
  profile first and short-circuits (returns 0) when `currentHourUtc` is outside the learned
  window** — *before* the membership/poster reads, so an out-of-window run does minimal work and
  **touches no dedupe records** (a later in-window run can still nudge).
- The **hourly schedule (`0 * * * *` UTC) is preserved.** Net effect: one well-timed nudge/day per
  recipient instead of a 24x blast. The **per-uid daily dedupe** (`accounts/{uid}/nudges/{dayKey}`)
  is intact, so even a wide multi-hour window still yields at most one nudge/uid/day.
- All §1.1 invariants preserved: at-risk selection (`planCrewNudge`), token/permission gating,
  re-check at send time, no `link` (reminder → opens Feed).
- **A/B tagging (spec §1.4):** each send now carries `data.windowSource` = `"learned"` | `"fallback"`
  so later analysis can compare nudges fired from a learned slot vs. the midday fallback.

## Data reader

New injectable `readRecentPostingTimestamps(crewId)`; the Firestore impl queries
`crews/{crewId}/meals where dayKey >= (today − 28 days)` and collects `publishedAtEpochMs`.
`PROFILE_LOOKBACK_DAYS = 28` (≈ four weeks of habit; recent enough to follow a shifting routine).
This is a single-field range query on `dayKey` — **no composite index needed** (same shape the
weekly digest already uses).

## Data you'd need stored that isn't (flagged follow-up)

- **Per-user (or per-crew) timezone.** Without it the window is UTC-relative, not true-local, and
  multi-region crews get a window biased toward whichever region posts most. Capturing a tz on the
  account doc (e.g. `AccountDto.timeZoneId: String?` from `TimeZone.currentSystemDefault()`) would let
  the server compute a true local mealtime and per-recipient windows. This is the natural next
  increment; see the handoff. The current UTC approach is correct and useful for single-region crews
  in the meantime.
- (Optional, future) Per-slot windows (breakfast/lunch/dinner) instead of one band — the spec floats
  "median posting hour per slot". The single-window approach is simpler and sufficient for one
  daily nudge; revisit if multiple daily nudges are ever wanted.

## Verification

```
$ pnpm --dir functions build      # tsc
$ tsc                             # (no errors emitted)

$ pnpm --dir functions test
 ✓ __tests__/mealtimeProfile.test.ts (13 tests)
 ✓ __tests__/streakNudge.test.ts (18 tests)
 ✓ __tests__/weeklyDigest.test.ts (7 tests)
 Test Files  8 passed (8)
      Tests  80 passed (80)
```

(`eslint src --ext .ts` also clean.) The lone stderr line during the run is the
**pre-existing intentional** error-path log from `deleteAccount.test.ts`; that suite passes 11/11.

## Deploy steps (for the user)

1. `pnpm --dir functions deploy` — redeploys `streakNudge` (the Cloud Scheduler `0 * * * *` job is
   unchanged; only the function body changed). **Region `europe-west3`.**
2. No Firestore rules change required (no new collection; `nudges` deny block already exists).
3. No new composite index required (single-field `dayKey >=` range query).
4. (Optional) Watch logs: `pnpm --dir functions logs` — each run now logs the current UTC hour and
   the count of crews/nudges, so you can confirm the windowing is firing as expected.
