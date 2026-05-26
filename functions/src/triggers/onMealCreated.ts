import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { sendToCrew, mealDeepLink } from "../fcm/push";
import { KEY_NEW_MEAL_POST, FALLBACK } from "../i18n/keys";

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

    await sendToCrew(crewId, meal.authorId as string, {
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
    });
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
