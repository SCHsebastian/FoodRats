import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { sendToCrew } from "../fcm/push";
import { KEY_NEW_MEAL_POST, FALLBACK } from "../i18n/keys";

export const onMealCreated = onDocumentCreated(
  {
    document: "crews/{crewId}/meals/{mealId}",
    region: "us-central1",
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const meal = snap.data();
    const { crewId, mealId } = event.params;

    const crewName = await readCrewName(crewId);
    const authorName: string = (meal.authorName as string) ?? "A crewmate";
    const dishName: string = (meal.dishName as string) ?? "a meal";

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
