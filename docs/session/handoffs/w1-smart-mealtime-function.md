# Handoff — `w1-smart-mealtime-function` → (future) capture-user-timezone (client)

The server smart-mealtime gate (§1.4) is live and green, but it operates in **UTC** because no
timezone is stored anywhere. To upgrade it to a *true local* mealtime (and to support multi-region
crews), a CLIENT task must capture and persist a timezone. **Optional / future — not blocking.**

## What the server does today (so you don't duplicate it)

`functions/src/triggers/mealtimeProfile.ts` learns each crew's active posting-hour window from
`crews/{id}/meals.publishedAtEpochMs` over the last 28 days **in UTC**, and `streakNudge` only
nudges a crew when the current UTC hour is inside that window. The per-uid daily dedupe is intact.
Each send carries `data.windowSource` (`"learned"` | `"fallback"`) for A/B.

## What a timezone-capture client task would add

1. **Persist a tz on the account doc.** Add `AccountDto.timeZoneId: String? = null` (IANA id, e.g.
   `"Europe/Madrid"`), written at sign-in / `ensureAccountDoc` from
   `TimeZone.currentSystemDefault().id` (kotlinx-datetime). Pre-launch → no migration. World-readable
   `accounts/{uid}` is fine — a tz is not PII.
2. **No client scheduling.** The push stays server-scheduled (the chosen channel). The client change
   is purely "stamp the tz".
3. **Then a small server follow-up** can switch `mealtimeProfile` from UTC hours to per-recipient
   local hours: bucket each `publishedAtEpochMs` into the *author's* local hour (`DateTime.fromMillis(ms,
   { zone: account.timeZoneId })`), and gate each recipient against `now` in *their* tz. The pure
   profile functions already take raw hours, so only the histogram-bucketing reader + the gate's
   "current hour" need to become tz-aware. The window-derivation algorithm is unchanged.

## Also pending from the §1.1 handoff (still open, unrelated to tz)

The earlier `w1-streak-nudges-function` handoff recommends **disabling the client
`DailyInactivityWorker`** once the server nudge is verified, to avoid two daily "go post"
notifications. That is still a client task and is now doubly relevant: the server nudge is now both
*social-proof* AND *smart-timed*, so the local fixed-14:00 worker is fully superseded.
