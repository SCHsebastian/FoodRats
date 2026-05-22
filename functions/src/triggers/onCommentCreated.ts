import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { sendToUid } from "../fcm/push";
import { KEY_NEW_COMMENT, FALLBACK } from "../i18n/keys";

export const onCommentCreated = onDocumentCreated(
  {
    document: "crews/{crewId}/meals/{mealId}/comments/{commentId}",
    region: "us-central1",
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
      },
    });
  },
);
