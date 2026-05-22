import { onSchedule } from "firebase-functions/v2/scheduler";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { DateTime } from "luxon";
import { sendToCrew } from "../fcm/push";
import { KEY_WEEKLY_DIGEST, FALLBACK } from "../i18n/keys";
import { computeAwards, MealInput, Awards } from "../stats/computeWindow";

export const weeklyDigest = onSchedule(
  {
    schedule: "0 9 * * 1",
    timeZone: "UTC",
    region: "europe-west3",
  },
  async () => {
    const now = DateTime.utc();
    const thisMonday = now.startOf("week");
    const prevWeekStart = thisMonday.minus({ weeks: 1 });
    const prevWeekEnd = thisMonday.minus({ milliseconds: 1 });
    const prevStartKey = prevWeekStart.toFormat("yyyy-LL-dd");
    const prevEndKey = prevWeekEnd.toFormat("yyyy-LL-dd");

    logger.info(`weeklyDigest: window ${prevStartKey}..${prevEndKey}`);

    const crewsSnap = await getFirestore().collection("crews").get();
    for (const crewDoc of crewsSnap.docs) {
      const crewId = crewDoc.id;
      const mealsSnap = await getFirestore()
        .collection(`crews/${crewId}/meals`)
        .where("dayKey", ">=", prevStartKey)
        .where("dayKey", "<=", prevEndKey)
        .get();
      if (mealsSnap.empty) continue;

      const meals: MealInput[] = mealsSnap.docs.map((d) => {
        const data = d.data();
        return {
          authorId: (data.authorId as string) ?? "",
          authorName: (data.authorName as string) ?? "Someone",
          dishName: (data.dishName as string) ?? "a meal",
          ratingSum: (data.ratingSum as number) ?? 0,
          voterCount: (data.voterCount as number) ?? 0,
          publishedAtEpochMs: (data.publishedAtEpochMs as number) ?? 0,
        };
      });

      const awards = computeAwards(meals);
      const bodyParts = formatAwards(awards);
      if (bodyParts.length === 0) continue;

      await sendToCrew(crewId, null, {
        kind: "WeeklyDigest",
        key: KEY_WEEKLY_DIGEST,
        notificationTitle: FALLBACK.weeklyDigestTitle,
        notificationBody: FALLBACK.weeklyDigestBody(bodyParts),
        data: {
          crewId,
          weekStartIso: prevStartKey,
          ...flattenAwardsToData(awards),
        },
      });
    }
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
