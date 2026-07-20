import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { sendToCrew, mealDeepLink } from "../fcm/push";
import { KEY_NEW_MEAL_POST, localizeNotification } from "../i18n/keys";
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
    // Empty when the doc is malformed: `localizeNotification` substitutes the PER-LANGUAGE
    // fallback ("A crewmate"/"Un compañero", "a meal"/"una comida") at send time — an English
    // fallback baked into `data` here would leak into the ES-localized OS text.
    const authorName: string = (meal.authorName as string) ?? "";
    const dishName: string = (meal.dishName as string) ?? "";
    // The meal's day key (ISO date) is the second path segment of the meal deep link. Present on
    // every meal written by the app; guard anyway so a malformed doc still sends a (linkless) push.
    const dayKey = (meal.dayKey as string | undefined) ?? "";
    const data = {
      crewId,
      crewName,
      mealId,
      authorName,
      dishName,
      // Tapping opens the specific meal. Omit when we can't build a valid link (no dayKey) —
      // the app then just opens to Feed, which is the right "couldn't target" fallback.
      ...(dayKey ? { dayKey, link: mealDeepLink(crewId, mealId, dayKey) } : {}),
    };
    // English default (only used for tokens without a localizer match); built through the same
    // localizer as the per-language groups so its missing-name fallbacks stay consistent.
    const en = localizeNotification("en", KEY_NEW_MEAL_POST, data);

    // Run FCM push and badge milestone in parallel; a badge failure must not drop the push.
    await Promise.all([
      sendToCrew(crewId, meal.authorId as string, {
        kind: "NewMealPost",
        key: KEY_NEW_MEAL_POST,
        notificationTitle: en?.title ?? "",
        notificationBody: en?.body ?? "",
        data,
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
      countCanonicalPublish: async (_uid, canonicalKey) => {
        // The raw lifetime count lives in a SERVER-ONLY subcollection (deny-all client
        // access, like `nudges`) so it is neither world-readable nor client-forgeable —
        // only the derived `badgeId` tier is public (no leaderboard arms race, no self-cheat
        // by writing an inflated count).
        const markerRef = db.doc(`accounts/${uid}/publishedMealKeys/${canonicalKey}`);
        const counterRef = db.doc(`accounts/${uid}/badgeProgress/counter`);
        // Marker + increment commit in ONE transaction: a crash aborts both (the
        // re-delivery re-runs the unit and short-circuits on the marker), and the
        // transactional read makes prev/new exact under concurrent multi-crew fan-out.
        return db.runTransaction(async (tx) => {
          const marker = await tx.get(markerRef);
          if (marker.exists) return null;
          const counter = await tx.get(counterRef);
          const prevCount = (counter.data()?.mealPublishCount as number | undefined) ?? 0;
          const newCount = prevCount + 1;
          tx.set(markerRef, { markedAtEpochMs: Date.now() });
          // merge:true so the first increment creates the (owner-only) progress doc.
          tx.set(counterRef, { mealPublishCount: newCount }, { merge: true });
          return { prevCount, newCount };
        });
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
