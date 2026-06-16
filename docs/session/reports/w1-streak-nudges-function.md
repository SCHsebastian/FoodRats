# Report — `w1-streak-nudges-function`

**Task:** SERVER side of social-proof streak nudges — a scheduled Cloud Function that detects
crew members who have NOT posted today (but whose crewmates have) and fans out an FCM push.
SERVER/FUNCTIONS LAYER ONLY. Authoritative spec: `docs/roadmap/2026-06-14-feature-roadmap.md` §1.1.

## What §1.1 actually asks for

§1.1 is **social-proof** nudges, not a generic "you broke your streak" reminder:
> "3 of your 5 crewmates already posted today 👀" — but only to members who haven't posted.
> Cloud Function `streakNudge` (scheduler, `europe-west3`): for each crew, count today's posters;
> for each non-poster with a live token, `sendToUid` with `data.postedCount`/`crewSize`.
> Suppress if recipient already posted (re-check at send time)… i18n the templated body
> (`%1$d of %2$d posted`), reuse `NotificationStringKey`. Analytics: `streak_nudge_sent` is
> server-side; client logs `notification_opened` on tap (deep-link to Feed).

So the push targets non-posters in crews where there IS social proof (≥1 poster today) and carries
the poster count + crew size for the client to localize.

## Files changed

- **`functions/src/triggers/streakNudge.ts`** (new) — the scheduled function + pure, injectable
  selection logic (`todayKey`, `planCrewNudge`, `processCrewNudge`, `NudgeDeps`).
- **`functions/src/triggers/crewScan.ts`** (new) — extracted the bounded paginated crew-scan
  (`CREWS_PAGE_SIZE`, `CrewPage`, `ListCrewPage`, `ProcessCrew`, `paginateCrews`,
  `firestoreCrewPager`) that previously lived inline in `weeklyDigest.ts`, so `streakNudge` reuses
  the exact same #19-bounded scan instead of duplicating it.
- **`functions/src/triggers/weeklyDigest.ts`** — now imports the scan from `./crewScan` and
  re-exports `paginateCrews`/`CREWS_PAGE_SIZE`/`CrewPage`/`ListCrewPage`/`ProcessCrew` from the same
  path so the existing `weeklyDigest.test.ts` import path keeps working unchanged. Behaviour
  identical.
- **`functions/src/fcm/push.ts`** — added `"SocialNudge"` to the `PushPayload.kind` union.
- **`functions/src/i18n/keys.ts`** — added `KEY_SOCIAL_NUDGE = "social_nudge"` + an English
  `socialNudgeTitle`/`socialNudgeBody(posted, size)` OS lock-screen fallback.
- **`functions/src/index.ts`** — registered `export { streakNudge }`.
- **`functions/__tests__/streakNudge.test.ts`** (new) — 12 vitest cases.
- **`firestore.rules`** — added a `match /accounts/{uid}/nudges/{dayKey} { allow read, write: if
  false; }` block (server-only dedupe markers; Admin SDK bypasses rules, clients denied).

## Message contract (decided by mirroring the existing pushes)

The existing pushes (`onMealCreated`, `weeklyDigest`) send a **`notification` + `data` hybrid**, NOT
data-only: `data.key` selects the client `PushPayloadMapper.parse()` branch, while `notification`
carries an English fallback for the OS lock screen. I followed that exactly.

The nudge sends (via the existing `sendToUid` helper):
- `notification.title` / `notification.body` = `FALLBACK.socialNudgeTitle` /
  `FALLBACK.socialNudgeBody(postedCount, crewSize)` (English fallback only).
- `data.kind = "SocialNudge"`, `data.key = "social_nudge"`, `data.postedCount`, `data.crewSize`.
- **NO `data.link`** — this is a reminder; tapping just opens the app to Feed (per the existing
  "reminders carry no link" convention — `onMealCreated` carries a link, `DailyInactivityWorker` /
  digest reminders do not).

The client `w1-streak-nudges-i18n` task localizes title/body from `data.key` + the two params (see
handoff). Today the client `PushPayloadMapper.parse()` returns `null` for an unknown `key`, so until
the i18n task adds the branch the OS still shows the English `notification` fallback but the in-app
`Reminder` mapping ignores it — no crash, graceful.

## Decisions (the roadmap left these to "sensible defaults")

1. **Today = UTC calendar day** (`todayKey`), matching `weeklyDigest`'s UTC window and the
   `meal.dayKey` the client writes. Per-recipient timezone-aware quiet hours are a future
   refinement — devices aren't stamped with a timezone today, so there's nothing to localize on.
2. **At risk = posted-before-but-not-today framing replaced by social proof:** nudge only when ≥1
   crewmate posted today. A crew where nobody posted is skipped (the "N of M already posted" framing
   would be false). This is exactly §1.1's "suppress if crew streak isn't actually at risk."
3. **Crew must have ≥2 members** (social proof needs at least one other member).
4. **Dedupe = at most one nudge per uid per UTC day**, recorded at `accounts/{uid}/nudges/{dayKey}`.
   Idempotent across retries AND across the multiple crews a user belongs to (first crew that would
   nudge them wins for the day).
5. **Skip tokenless recipients** (handled by `sendToUid` no-op; also pre-checked so we don't burn a
   dedupe record on a user who can't receive — a later token re-registration can still nudge them
   that day).
6. **Re-check posters at send time:** posters are recomputed from today's meals on every run, so a
   member who just posted won't be nudged on the next run.
7. **Schedule = hourly on the hour, UTC (`0 * * * *`)**, `timeZone: "UTC"`, `region:
   "europe-west3"` (matches `weeklyDigest`). Hourly + per-uid daily dedupe means the nudge can land
   during a recipient's daytime regardless of region while still firing at most once/person/day. A
   learned per-crew mealtime slot is roadmap §1.4, which builds on this same path.

## Not done in this task (correctly out of scope / flagged)

- **`streak_nudge_sent` analytics event** — §1.1 says it's "server-side." There is no analytics
  emitter in `functions/` today (the analytics base is the client `AnalyticsPort`); adding a
  server analytics sink is a separate concern. Logged via `logger.info` for now. Flagged for the
  user.
- **Client `DailyInactivityWorker`** — NOT removed in this task (it's Android client code; this is
  the functions layer). §1.1's open decision #7 says server-scheduled is *preferred* over the client
  WorkManager. See the handoff — the i18n task / a follow-up should decide whether to disable the
  client job to avoid double-nudging. NOT deleted here (would be unsafe cross-layer scope creep).
- **Client `PushPayloadMapper` branch + `NotificationStringKey` strings** — the
  `w1-streak-nudges-i18n` task. See handoff for the exact contract.

## Verification

`pnpm --dir functions build` (tsc):
```
$ tsc
```
(no diagnostics — exit 0; the `Unsupported engine` line is environmental: node 26 vs wanted 20.)

`pnpm --dir functions test` (vitest):
```
 ✓ __tests__/weeklyDigest.test.ts (7 tests) 17ms
 ✓ __tests__/streakNudge.test.ts (12 tests) 17ms

 Test Files  7 passed (7)
      Tests  61 passed (61)
```
(`weeklyDigest.test.ts` still 7/7 after the scan extraction; `streakNudge.test.ts` 12/12 new:
todayKey ×2, planCrewNudge at-risk selection ×5, processCrewNudge fan-out/dedupe/token-gating ×5.)

## Deploy / schedule steps the user must run

1. **Deploy the function:** `pnpm --dir functions deploy` (alias for
   `firebase deploy --only functions --project foodrats-de4ec`). This creates the Cloud Scheduler
   job for `streakNudge` automatically (gen-2 `onSchedule`). Requires the Cloud Scheduler +
   Cloud Functions APIs enabled on the project (already enabled for `weeklyDigest`).
2. **Deploy rules:** `pnpm dlx firebase-tools deploy --only firestore:rules --project
   foodrats-de4ec` (for the new `nudges` deny block — optional hardening; the collection is already
   inaccessible to clients by default since subcollections don't inherit parent rules).
3. **No new composite index** is needed: `readTodayPosters` is a single-field equality query
   (`where("dayKey", "==", …)`) on the existing `meals` collection group; the crew scan orders by
   `__name__`. Both are auto-indexed.
4. (Optional) adjust the schedule cron if hourly is too aggressive once you observe send volume.
