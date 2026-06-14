import { onDocumentDeleted } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { logger } from "firebase-functions/v2";

// When a meal is deleted (by its author or by the crew owner), Firestore does NOT cascade
// to the meal's subcollections, and Storage objects are never touched by a Firestore delete.
// This trigger reclaims both:
//   1. the orphaned subcollections (`comments`, deprecated `ratings`), and
//   2. the meal's plate image blob.
//
// A plate shared to several crews is stored as one independent image copy PER crew
// (`crews/{crewId}/meals/{mealId}.jpg`) — a member can only mint a signed read URL for a
// plate under their own crew, so a shared object would be unviewable. That per-crew ownership
// means deletion needs NO ref-counting: each crew's deleted meal doc reclaims exactly its own
// copy, and a sibling copy in another crew is reclaimed when that crew's meal is deleted.
export const onMealDeleted = onDocumentDeleted(
  {
    document: "crews/{crewId}/meals/{mealId}",
    region: "europe-west3",
  },
  async (event) => {
    const { crewId, mealId } = event.params;
    const db = getFirestore();

    // 1) Subcollection sweep — the meal doc itself is already gone when this fires.
    try {
      await db.recursiveDelete(db.doc(`crews/${crewId}/meals/${mealId}`));
    } catch (e) {
      logger.error(
        `onMealDeleted: recursiveDelete failed for crews/${crewId}/meals/${mealId}`,
        e,
      );
    }

    // 2) Image blob. Prefer the path persisted on the deleted doc; fall back to the
    //    deterministic upload path. `ignoreNotFound` tolerates a publish that already
    //    cleaned up its own orphan, or a double-fire.
    const storedPath = event.data?.data()?.platePath as string | undefined;
    const platePath = storedPath ?? `crews/${crewId}/meals/${mealId}.jpg`;
    try {
      await getStorage().bucket().file(platePath).delete({ ignoreNotFound: true });
    } catch (e) {
      logger.error(`onMealDeleted: plate image delete failed for ${platePath}`, e);
    }
  },
);
