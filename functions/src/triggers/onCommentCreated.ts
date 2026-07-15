import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { sendToUid, mealDeepLink } from "../fcm/push";
import { KEY_NEW_COMMENT, KEY_COMMENT_MENTION, FALLBACK } from "../i18n/keys";

const MAX_MENTION_RECIPIENTS = 10;

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

    const commenterName: string = (comment.authorName as string) ?? "Someone";
    const dishName: string = (meal.dishName as string) ?? "your meal";
    // Tapping a comment notification opens the commented meal (same target as a meal-post push).
    const dayKey = (meal.dayKey as string | undefined) ?? "";
    const data = {
      crewId,
      mealId,
      commentId,
      commenterName,
      dishName,
      ...(dayKey ? { dayKey, link: mealDeepLink(mealId, dayKey) } : {}),
    };

    if (meal.authorId === comment.authorId) {
      logger.info("onCommentCreated: self-comment, skip owner push");
    } else {
      await sendToUid(meal.authorId as string, {
        kind: "NewComment",
        key: KEY_NEW_COMMENT,
        notificationTitle: FALLBACK.newCommentTitle(commenterName, dishName),
        notificationBody: FALLBACK.newCommentBody,
        data,
      });
    }

    await notifyMentions({ crewId, comment, meal, data });
  },
);

/**
 * Fan out the "mentioned you" push to crew members named in `comment.mentions`. Anti-spam: the
 * mention list is intersected with `crew.memberIds` so a client can never smuggle a push to a
 * uid outside the crew. No double notification: the meal owner already received the NewComment
 * push above (unless they authored the comment, in which case they're excluded as author
 * anyway) — so both the author and the meal owner are excluded here regardless of overlap.
 */
async function notifyMentions(args: {
  crewId: string;
  comment: Record<string, unknown>;
  meal: Record<string, unknown>;
  data: Record<string, string>;
}): Promise<void> {
  const { crewId, comment, meal, data } = args;
  const rawMentions = Array.isArray(comment.mentions) ? comment.mentions : [];
  const mentions = rawMentions.filter((x): x is string => typeof x === "string");
  if (mentions.length === 0) return;

  const crew = (await getFirestore().doc(`crews/${crewId}`).get()).data();
  if (!crew) {
    logger.warn(`onCommentCreated: crew ${crewId} not found, skip mention push`);
    return;
  }
  const memberIds = new Set((crew.memberIds as string[]) ?? []);
  const exclude = new Set([comment.authorId as string, meal.authorId as string]);

  const recipients = [...new Set(mentions)]
    .filter((uid) => memberIds.has(uid) && !exclude.has(uid))
    .slice(0, MAX_MENTION_RECIPIENTS);
  if (recipients.length === 0) return;

  const commenterName = data.commenterName;
  const dishName = data.dishName;
  await Promise.all(
    recipients.map((uid) =>
      sendToUid(uid, {
        kind: "CommentMention",
        key: KEY_COMMENT_MENTION,
        notificationTitle: FALLBACK.commentMentionTitle(commenterName, dishName),
        notificationBody: FALLBACK.commentMentionBody,
        data,
      }).catch((err) => {
        logger.warn(`onCommentCreated: mention push to ${uid} failed`, err);
      }),
    ),
  );
}
