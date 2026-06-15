import { onSchedule } from "firebase-functions/v2/scheduler";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { DateTime } from "luxon";
import { sendToCrew, digestDeepLink } from "../fcm/push";
import { KEY_WEEKLY_DIGEST, FALLBACK } from "../i18n/keys";
import { computeAwards, ratingAggregatesFrom, MealInput, Awards } from "../stats/computeWindow";
import { paginateCrews, firestoreCrewPager } from "./crewScan";

// Re-exported so existing importers (and tests) keep their import path stable; the bounded
// crew-scan now lives in `./crewScan` and is shared with `streakNudge`.
export { paginateCrews, CREWS_PAGE_SIZE } from "./crewScan";
export type { CrewPage, ListCrewPage, ProcessCrew } from "./crewScan";

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
      // Deep link to the in-app swipeable recap (roadmap §2.4). The apps forward data.link to the
      // DeepLinkBus on tap; parseDeepLink maps /digest/{weekStart} → Route.WeeklyStory.
      link: digestDeepLink(window.prevStartKey),
      ...flattenAwardsToData(awards),
    },
  });
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
