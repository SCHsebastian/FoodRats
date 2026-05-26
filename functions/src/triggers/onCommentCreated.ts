import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { sendToUid, mealDeepLink } from "../fcm/push";
import { KEY_NEW_COMMENT, FALLBACK } from "../i18n/keys";

export const onCommentCreated = onDocumentCreated(
  {
    document: "crews/{crewId}/meals/{mealId}/comments/{commentId}",
    region: "europe-west3",
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const comment = snap.data();
    const { crewId, mealId, commentId } = event.params;

    const meal = (
      await getFirestore().doc(`crews/${crewId}/meals/${mealId}`).get()
    ).data();
    if (!meal) {
      logger.warn(`onCommentCreated: parent meal ${mealId} not found`);
      return;
    }

    if (meal.authorId === comment.authorId) {
      logger.info("onCommentCreated: self-comment, skip push");
      return;
    }

    const commenterName: string = (comment.authorName as string) ?? "Someone";
    const dishName: string = (meal.dishName as string) ?? "your meal";
    // Tapping a comment notification opens the commented meal (same target as a meal-post push).
    const dayKey = (meal.dayKey as string | undefined) ?? "";

    await sendToUid(meal.authorId as string, {
      kind: "NewComment",
      key: KEY_NEW_COMMENT,
      notificationTitle: FALLBACK.newCommentTitle(commenterName, dishName),
      notificationBody: FALLBACK.newCommentBody,
      data: {
        crewId,
        mealId,
        commentId,
        commenterName,
        dishName,
        ...(dayKey ? { dayKey, link: mealDeepLink(mealId, dayKey) } : {}),
      },
    });
  },
);
