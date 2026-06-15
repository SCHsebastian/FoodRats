import { onSchedule } from "firebase-functions/v2/scheduler";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { DateTime } from "luxon";
import { sendToUid } from "../fcm/push";
import { readTokens } from "../fcm/tokens";
import { KEY_SOCIAL_NUDGE, FALLBACK } from "../i18n/keys";
import { paginateCrews, firestoreCrewPager, ProcessCrew } from "./crewScan";
import {
  ActiveWindow,
  computeMealtimeProfile,
  isHourInWindow,
} from "./mealtimeProfile";

/**
 * Social-proof streak nudge — the SERVER side of roadmap §1.1.
 *
 * Every run, for each crew, count how many members have posted a meal *today*, then push
 * "N of M crewmates already posted today — your turn" to the members who have NOT posted yet
 * AND who hold a live FCM token. This is more reliable than the client `DailyInactivityWorker`
 * (works cross-device, survives the app being killed) and only nudges people who can actually
 * still close the gap.
 *
 * The push is a reminder: it carries NO deep link (per the existing "reminders just open Feed"
 * convention — see onMealCreated vs DailyInactivityWorker). The client localizes the body from
 * `data.key` + `data.postedCount`/`data.crewSize`.
 *
 * Design decisions (the roadmap left these to "sensible defaults"):
 * - **Today = UTC calendar day**, matching `weeklyDigest`'s UTC window and the meal `dayKey` the
 *   client writes. (Per-recipient timezone-aware quiet hours are a future refinement — devices
 *   aren't stamped with a tz today.)
 * - **At risk = posted before but not today.** A crew where NOBODY posted today is skipped: with
 *   zero social proof the "N of M already posted" framing is false, so there's nothing to nudge
 *   with. (`postedCount === 0` ⇒ no nudge — also covers a brand-new/empty crew.)
 * - **Dedupe = at most one nudge per uid per UTC day.** The send is recorded under
 *   `accounts/{uid}/nudges/{dayKey}`; a member already recorded for today is skipped. Idempotent
 *   across retries and across multiple crews the user belongs to.
 * - **Skip recipients without a live token** (handled by `sendToUid`, which no-ops on empty tokens;
 *   we also pre-check so we don't burn a dedupe record on a tokenless user).
 * - **Re-check at send time:** posters are recomputed from today's meals each run, so a member who
 *   posted seconds ago won't be nudged on the next run.
 * - **Smart mealtime gate (roadmap §1.4):** the hourly run only nudges a crew whose learned active
 *   window contains the current UTC hour. The window is derived from the crew's own posting-hour
 *   distribution (see `mealtimeProfile.ts`), so the one daily nudge lands near when the crew eats
 *   instead of at an arbitrary hour. Crews with too little history fall back to a midday window.
 *   The dedupe still guarantees at most one nudge/uid/day regardless of how the window overlaps the
 *   schedule. Timezone caveat: the window is in UTC (no per-crew tz is stored) — see
 *   `mealtimeProfile.ts`.
 */

/** Today's ISO day key (`yyyy-LL-dd`) in UTC — the same shape the client writes to `meal.dayKey`. */
export function todayKey(now: DateTime): string {
  return now.toUTC().toFormat("yyyy-LL-dd");
}

/** A crew's nudge picture for one day: who's in it, who already posted, the resulting non-posters. */
export interface CrewNudgePlan {
  /** Distinct uids that posted at least one meal today. */
  posterIds: Set<string>;
  /** Crew size (member count). */
  crewSize: number;
  /** Members who have NOT posted today — the nudge candidates (before token/dedupe filtering). */
  nonPosterIds: string[];
}

/**
 * Pure selection: from a crew's members and today's poster ids, derive the nudge plan.
 * No I/O — unit-testable. Returns `null` when there's nothing to nudge:
 *  - crew has < 2 members (social proof needs at least one other member), or
 *  - nobody posted today (no social proof to show).
 */
export function planCrewNudge(memberIds: string[], posterIds: Set<string>): CrewNudgePlan | null {
  const crewSize = memberIds.length;
  if (crewSize < 2) return null;
  const postedToday = [...posterIds].filter((id) => memberIds.includes(id));
  if (postedToday.length === 0) return null;
  const nonPosterIds = memberIds.filter((id) => !posterIds.has(id));
  if (nonPosterIds.length === 0) return null;
  return { posterIds, crewSize, nonPosterIds };
}

/** Reads the distinct author ids of meals posted in a crew today. */
export type ReadTodayPosters = (crewId: string, dayKey: string) => Promise<Set<string>>;

/**
 * Reads the `publishedAtEpochMs` timestamps of a crew's recent meals (the learning window for the
 * mealtime profile). The exact lookback is the data-layer reader's concern; the pure profile code
 * just consumes the timestamps.
 */
export type ReadRecentPostingTimestamps = (crewId: string) => Promise<number[]>;

/** True iff `uid` was already nudged today (dedupe). */
export type WasNudgedToday = (uid: string, dayKey: string) => Promise<boolean>;

/** Records that `uid` was nudged today, so a later run / another crew won't double-nudge. */
export type MarkNudged = (uid: string, dayKey: string) => Promise<void>;

/**
 * Sends the social-proof nudge to one non-poster. `windowSource` tags the send with how the
 * mealtime window was derived (`"learned"` vs `"fallback"`) for later A/B analysis (spec §1.4).
 */
export type SendNudge = (
  uid: string,
  postedCount: number,
  crewSize: number,
  windowSource: ActiveWindow["source"],
) => Promise<void>;

/** Injectable collaborators — lets the per-crew logic be unit-tested without firebase-admin. */
export interface NudgeDeps {
  readMemberIds: (crewId: string) => Promise<string[]>;
  readTodayPosters: ReadTodayPosters;
  /** Recent posting timestamps used to learn the crew's smart mealtime window (§1.4). */
  readRecentPostingTimestamps: ReadRecentPostingTimestamps;
  hasToken: (uid: string) => Promise<boolean>;
  wasNudgedToday: WasNudgedToday;
  markNudged: MarkNudged;
  sendNudge: SendNudge;
}

/**
 * Processes one crew: gates on the smart mealtime window, builds the plan, then for each non-poster
 * with a token who hasn't been nudged today, sends the nudge and records the dedupe marker. Returns
 * the number of pushes sent.
 *
 * `currentHourUtc` is the UTC hour (0–23) of the current hourly run. The crew's learned active
 * window (from its posting history) must contain it, otherwise the run skips this crew entirely —
 * that's the §1.4 "land near when the crew eats, not at an arbitrary hour" gate. Because the window
 * is derived from posting timestamps, the smart gate is checked BEFORE the membership/poster reads
 * so an out-of-window run does the minimum work and never touches the dedupe records.
 */
export async function processCrewNudge(
  crewId: string,
  dayKey: string,
  currentHourUtc: number,
  deps: NudgeDeps,
): Promise<number> {
  const profile = computeMealtimeProfile(await deps.readRecentPostingTimestamps(crewId));
  if (!isHourInWindow(currentHourUtc, profile.window)) return 0;

  const memberIds = await deps.readMemberIds(crewId);
  const posterIds = await deps.readTodayPosters(crewId, dayKey);
  const plan = planCrewNudge(memberIds, posterIds);
  if (!plan) return 0;

  const postedCount = plan.posterIds.size;
  let sent = 0;
  for (const uid of plan.nonPosterIds) {
    if (await deps.wasNudgedToday(uid, dayKey)) continue;
    if (!(await deps.hasToken(uid))) continue;
    await deps.sendNudge(uid, postedCount, plan.crewSize, profile.window.source);
    await deps.markNudged(uid, dayKey);
    sent += 1;
  }
  return sent;
}

// --- Firestore-backed collaborators (the real, side-effecting implementations) ---

/**
 * Lookback (in days) over which to learn a crew's mealtime window. 28 days ≈ four weeks of habit
 * while staying recent enough to follow a shifting routine; matches the spirit of the stats engine's
 * 30-day window without straddling a full month.
 */
const PROFILE_LOOKBACK_DAYS = 28;

function firestoreDeps(now: DateTime): NudgeDeps {
  const db = getFirestore();
  const sinceKey = now.toUTC().minus({ days: PROFILE_LOOKBACK_DAYS }).toFormat("yyyy-LL-dd");
  return {
    readMemberIds: async (crewId) => {
      const crew = (await db.doc(`crews/${crewId}`).get()).data();
      return (crew?.memberIds as string[]) ?? [];
    },
    readRecentPostingTimestamps: async (crewId) => {
      const snap = await db
        .collection(`crews/${crewId}/meals`)
        .where("dayKey", ">=", sinceKey)
        .get();
      const out: number[] = [];
      for (const d of snap.docs) {
        const ms = d.data().publishedAtEpochMs as number | undefined;
        if (typeof ms === "number") out.push(ms);
      }
      return out;
    },
    readTodayPosters: async (crewId, dayKey) => {
      const snap = await db
        .collection(`crews/${crewId}/meals`)
        .where("dayKey", "==", dayKey)
        .get();
      const posters = new Set<string>();
      for (const d of snap.docs) {
        const authorId = d.data().authorId as string | undefined;
        if (authorId) posters.add(authorId);
      }
      return posters;
    },
    hasToken: async (uid) => (await readTokens(uid)).length > 0,
    wasNudgedToday: async (uid, dayKey) =>
      (await db.doc(`accounts/${uid}/nudges/${dayKey}`).get()).exists,
    markNudged: async (uid, dayKey) => {
      await db.doc(`accounts/${uid}/nudges/${dayKey}`).set({ sentAt: Date.now() });
    },
    sendNudge: async (uid, postedCount, crewSize, windowSource) => {
      await sendToUid(uid, {
        kind: "SocialNudge",
        key: KEY_SOCIAL_NUDGE,
        notificationTitle: FALLBACK.socialNudgeTitle,
        notificationBody: FALLBACK.socialNudgeBody(postedCount, crewSize),
        data: {
          // No `link` — a reminder; tapping just opens the app to Feed (per convention).
          postedCount: String(postedCount),
          crewSize: String(crewSize),
          // Tags the send with the §1.4 mealtime-window source for later A/B analysis.
          windowSource,
        },
      });
    },
  };
}

/**
 * Hourly schedule (every hour on the hour, UTC). Each run only nudges crews whose LEARNED mealtime
 * window (roadmap §1.4, derived per-crew from posting history in `mealtimeProfile.ts`) contains the
 * current UTC hour — so the net effect is one well-timed nudge/day per recipient, not a 24x blast.
 * The per-uid daily dedupe still guarantees at most one nudge per person per day even where a wide
 * window spans several hours.
 */
export const streakNudge = onSchedule(
  {
    schedule: "0 * * * *",
    timeZone: "UTC",
    region: "europe-west3",
  },
  async () => {
    const now = DateTime.utc();
    const dayKey = todayKey(now);
    const currentHourUtc = now.hour;
    const deps = firestoreDeps(now);
    let totalSent = 0;
    const processCrew: ProcessCrew = async (crewId) => {
      totalSent += await processCrewNudge(crewId, dayKey, currentHourUtc, deps);
    };
    const processed = await paginateCrews(firestoreCrewPager(), processCrew);
    logger.info(
      `streakNudge: scanned ${processed} crews, sent ${totalSent} nudges for ${dayKey} ` +
        `at hour ${currentHourUtc}:00 UTC`,
    );
  },
);
