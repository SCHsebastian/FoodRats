import { onSchedule } from "firebase-functions/v2/scheduler";
import { getFirestore, DocumentSnapshot } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { DateTime } from "luxon";
import { sendToCrew } from "../fcm/push";
import { KEY_WEEKLY_DIGEST, FALLBACK } from "../i18n/keys";
import { computeAwards, ratingAggregatesFrom, MealInput, Awards } from "../stats/computeWindow";

/**
 * Page size for the bounded crews scan. The old implementation did a single
 * `crews.get()` that loaded EVERY crew doc into memory at once — un-boundable as
 * the crew count grows, risking function memory/timeout limits (#19, P2).
 *
 * 200 keeps each Firestore round-trip's working set small (crew docs are tiny —
 * `memberIds` + a little metadata) while keeping the number of round-trips low.
 * Per-crew work (the meals query + award recompute + fan-out push) runs one crew
 * at a time, so peak memory is one page of crew ids plus one crew's meals —
 * independent of total crew count.
 */
export const CREWS_PAGE_SIZE = 200;

/** A single bounded page of crew ids plus the cursor needed to fetch the next one. */
export interface CrewPage {
  ids: string[];
  /** The last doc in this page; pass back as `cursor` to fetch the next page. `null` when exhausted. */
  cursor: DocumentSnapshot | null;
}

/** Fetches one bounded page of crew ids starting after `cursor` (or from the start when null). */
export type ListCrewPage = (cursor: DocumentSnapshot | null) => Promise<CrewPage>;

/** Computes and (if non-empty) sends the digest for a single crew. */
export type ProcessCrew = (crewId: string) => Promise<void>;

/** The previous-week ISO day-key window the digest aggregates over. */
export interface DigestWindow {
  prevStartKey: string;
  prevEndKey: string;
}

/** Derive the previous calendar-week window (Mon..Sun) from `now`. */
export function digestWindow(now: DateTime): DigestWindow {
  const thisMonday = now.startOf("week");
  const prevWeekStart = thisMonday.minus({ weeks: 1 });
  const prevWeekEnd = thisMonday.minus({ milliseconds: 1 });
  return {
    prevStartKey: prevWeekStart.toFormat("yyyy-LL-dd"),
    prevEndKey: prevWeekEnd.toFormat("yyyy-LL-dd"),
  };
}

/**
 * Bounded crew scan: walks the crews collection one page at a time and invokes
 * `processCrew` for every crew exactly once. Memory stays bounded to a single
 * page regardless of total crew count. Returns the number of crews processed.
 *
 * Fully injectable (`listCrewPage` / `processCrew`) so the pagination loop is
 * unit-testable without mocking the whole firebase-admin SDK.
 */
export async function paginateCrews(
  listCrewPage: ListCrewPage,
  processCrew: ProcessCrew,
): Promise<number> {
  let cursor: DocumentSnapshot | null = null;
  let processed = 0;
  // eslint-disable-next-line no-constant-condition
  while (true) {
    const page = await listCrewPage(cursor);
    for (const crewId of page.ids) {
      await processCrew(crewId);
      processed += 1;
    }
    // No cursor (or an empty short page) means the collection is exhausted.
    if (!page.cursor || page.ids.length === 0) break;
    cursor = page.cursor;
  }
  return processed;
}

/**
 * Compute the awards for one crew over the digest window and fan out the digest
 * push. Output is identical to the old inline body — only the iteration changed.
 */
export async function processCrewDigest(crewId: string, window: DigestWindow): Promise<void> {
  const mealsSnap = await getFirestore()
    .collection(`crews/${crewId}/meals`)
    .where("dayKey", ">=", window.prevStartKey)
    .where("dayKey", "<=", window.prevEndKey)
    .get();
  if (mealsSnap.empty) return;

  const meals: MealInput[] = mealsSnap.docs.map((d) => {
    const data = d.data();
    // Recompute from the per-rater `ratings` map — never trust the client-writable
    // `ratingSum`/`voterCount` fields (un-boundable on the vote update path).
    const { ratingSum, voterCount } = ratingAggregatesFrom(data.ratings);
    return {
      authorId: (data.authorId as string) ?? "",
      authorName: (data.authorName as string) ?? "Someone",
      dishName: (data.dishName as string) ?? "a meal",
      ratingSum,
      voterCount,
      publishedAtEpochMs: (data.publishedAtEpochMs as number) ?? 0,
    };
  });

  const awards = computeAwards(meals);
  const bodyParts = formatAwards(awards);
  if (bodyParts.length === 0) return;

  await sendToCrew(crewId, null, {
    kind: "WeeklyDigest",
    key: KEY_WEEKLY_DIGEST,
    notificationTitle: FALLBACK.weeklyDigestTitle,
    notificationBody: FALLBACK.weeklyDigestBody(bodyParts),
    data: {
      crewId,
      weekStartIso: window.prevStartKey,
      ...flattenAwardsToData(awards),
    },
  });
}

/** Firestore-backed crew pager: orders by document id and pages with a cursor. */
function firestoreCrewPager(): ListCrewPage {
  return async (cursor) => {
    let query = getFirestore()
      .collection("crews")
      .orderBy("__name__")
      .limit(CREWS_PAGE_SIZE);
    if (cursor) query = query.startAfter(cursor);
    const snap = await query.get();
    return {
      ids: snap.docs.map((d) => d.id),
      cursor: snap.docs.length < CREWS_PAGE_SIZE ? null : snap.docs[snap.docs.length - 1],
    };
  };
}

export const weeklyDigest = onSchedule(
  {
    schedule: "0 9 * * 1",
    timeZone: "UTC",
    region: "europe-west3",
  },
  async () => {
    const window = digestWindow(DateTime.utc());
    logger.info(`weeklyDigest: window ${window.prevStartKey}..${window.prevEndKey}`);

    const processed = await paginateCrews(firestoreCrewPager(), (crewId) =>
      processCrewDigest(crewId, window),
    );
    logger.info(`weeklyDigest: processed ${processed} crews`);
  },
);

function formatAwards(a: Awards): string[] {
  const parts: string[] = [];
  if (a.bestMeal) parts.push(`Best meal: ${a.bestMeal.dishName} (${a.bestMeal.avgScore.toFixed(1)}★)`);
  if (a.bestCook) parts.push(`Best cook: ${a.bestCook.authorName}`);
  if (a.mostProlific) parts.push(`Most prolific: ${a.mostProlific.authorName} (${a.mostProlific.postCount})`);
  if (a.mostVoted) parts.push(`Most voted: ${a.mostVoted.dishName}`);
  if (a.mostCriticized) parts.push(`Most criticized: ${a.mostCriticized.authorName}`);
  return parts;
}

function flattenAwardsToData(a: Awards): Record<string, string> {
  const out: Record<string, string> = {};
  if (a.bestMeal) {
    out.bestMealDishName = a.bestMeal.dishName;
    out.bestMealScore = a.bestMeal.avgScore.toFixed(2);
  }
  if (a.bestCook) {
    out.bestCookName = a.bestCook.authorName;
    out.bestCookAvg = a.bestCook.avgScore.toFixed(2);
  }
  if (a.mostProlific) {
    out.mostProlificName = a.mostProlific.authorName;
    out.mostProlificCount = String(a.mostProlific.postCount);
  }
  if (a.mostVoted) {
    out.mostVotedDishName = a.mostVoted.dishName;
    out.mostVotedVoterCount = String(a.mostVoted.voterCount);
  }
  if (a.mostCriticized) {
    out.mostCriticizedName = a.mostCriticized.authorName;
    out.mostCriticizedAvg = a.mostCriticized.avgScore.toFixed(2);
  }
  return out;
}
