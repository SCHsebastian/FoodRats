import { onDocumentDeleted } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";

// When a meal is deleted (by its author or by the crew owner), Firestore does NOT
// cascade the delete to the meal's subcollections — the `comments` (and the
// deprecated `ratings`) docs are left orphaned, invisible to the app but still
// billable and reachable by anyone who knows the path. Recursively delete the
// whole meal document tree so nothing dangles.
export const onMealDeleted = onDocumentDeleted(
  {
    document: "crews/{crewId}/meals/{mealId}",
    region: "europe-west3",
  },
  async (event) => {
    const { crewId, mealId } = event.params;
    const mealRef = getFirestore().doc(`crews/${crewId}/meals/${mealId}`);
    try {
      // recursiveDelete walks every subcollection (comments, ratings) under the
      // meal ref and deletes them in batches. The meal doc itself is already gone
      // by the time this trigger fires, so this is effectively a subcollection sweep.
      await getFirestore().recursiveDelete(mealRef);
    } catch (e) {
      logger.error(
        `onMealDeleted: recursiveDelete failed for crews/${crewId}/meals/${mealId}`,
        e,
      );
    }
  },
);
