import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { sendToCrew, mealDeepLink } from "../fcm/push";
import { KEY_NEW_MEAL_POST, FALLBACK } from "../i18n/keys";
import { processBadgeMilestone, type BadgeDeps } from "./badgeMilestones";

export const onMealCreated = onDocumentCreated(
  {
    document: "crews/{crewId}/meals/{mealId}",
    region: "europe-west3",
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const meal = snap.data();
    const { crewId, mealId } = event.params;

    const crewName = await readCrewName(crewId);
    const authorName: string = (meal.authorName as string) ?? "A crewmate";
    const dishName: string = (meal.dishName as string) ?? "a meal";
    // The meal's day key (ISO date) is the second path segment of the meal deep link. Present on
    // every meal written by the app; guard anyway so a malformed doc still sends a (linkless) push.
    const dayKey = (meal.dayKey as string | undefined) ?? "";

    // Run FCM push and badge milestone in parallel; a badge failure must not drop the push.
    await Promise.all([
      sendToCrew(crewId, meal.authorId as string, {
        kind: "NewMealPost",
        key: KEY_NEW_MEAL_POST,
        notificationTitle: FALLBACK.newMealPostTitle(authorName),
        notificationBody: FALLBACK.newMealPostBody(dishName),
        data: {
          crewId,
          crewName,
          mealId,
          authorName,
          dishName,
          // Tapping opens the specific meal. Omit when we can't build a valid link (no dayKey) —
          // the app then just opens to Feed, which is the right "couldn't target" fallback.
          ...(dayKey ? { dayKey, link: mealDeepLink(mealId, dayKey) } : {}),
        },
      }),
      awardBadgeIfMilestone(meal.authorId as string, crewId, mealId),
    ]);
  },
);

async function readCrewName(crewId: string): Promise<string> {
  try {
    const doc = (await getFirestore().doc(`crews/${crewId}`).get()).data();
    return (doc?.name as string) ?? "your crew";
  } catch (e) {
    logger.warn(`readCrewName failed for ${crewId}`, e);
    return "your crew";
  }
}

/**
 * Award a badge when the author crosses a milestone (1, 10, 50, 100 canonical publishes).
 * Failures are logged and swallowed so they don't block the push notification path.
 */
async function awardBadgeIfMilestone(
  uid: string,
  crewId: string,
  mealId: string,
): Promise<void> {
  try {
    const db = getFirestore();
    const deps: BadgeDeps = {
      readMealCount: async () => {
        // The raw lifetime count lives in a SERVER-ONLY subcollection (deny-all client
        // access, like `nudges`) so it is neither world-readable nor client-forgeable —
        // only the derived `badgeId` tier is public (no leaderboard arms race, no self-cheat
        // by writing an inflated count).
        const doc = await db.doc(`accounts/${uid}/badgeProgress/counter`).get();
        return (doc.data()?.mealPublishCount as number | undefined) ?? 0;
      },
      isAlreadyCounted: async (_uid, canonicalKey) => {
        const marker = await db
          .doc(`accounts/${uid}/publishedMealKeys/${canonicalKey}`)
          .get();
        return marker.exists;
      },
      markCounted: async (_uid, canonicalKey) => {
        await db
          .doc(`accounts/${uid}/publishedMealKeys/${canonicalKey}`)
          .set({ markedAtEpochMs: Date.now() });
      },
      incrementMealCount: async () => {
        // FieldValue.increment is atomic and avoids read-modify-write races. merge:true
        // makes the first increment create the (owner-only) progress doc if absent.
        const ref = db.doc(`accounts/${uid}/badgeProgress/counter`);
        await ref.set({ mealPublishCount: FieldValue.increment(1) }, { merge: true });
        const updated = await ref.get();
        return (updated.data()?.mealPublishCount as number) ?? 1;
      },
      writeBadge: async (_uid, badgeId) => {
        // Admin SDK write — bypasses all Firestore security rules (the only allowed path for badgeId).
        await db.doc(`accounts/${uid}`).update({ badgeId });
        logger.info(`[badge] ${uid} earned badge "${badgeId}"`);
      },
    };

    const awarded = await processBadgeMilestone(uid, crewId, mealId, deps);
    if (awarded) {
      logger.info(`[badge] milestone: uid=${uid} badge=${awarded}`);
    }
  } catch (e) {
    // Badge milestone failure must never drop the FCM push — log and swallow.
    logger.warn(`[badge] awardBadgeIfMilestone failed for uid=${uid} mealId=${mealId}`, e);
  }
}
