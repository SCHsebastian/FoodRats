import { getMessaging, BatchResponse } from "firebase-admin/messaging";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { readTokens, pruneToken, DeviceToken } from "./tokens";

export interface PushPayload {
  kind: "NewComment" | "NewMealPost" | "WeeklyDigest";
  key: string;
  notificationTitle: string;
  notificationBody: string;
  data: Record<string, string>;
}

/** Send to every device registered for a single uid. */
export async function sendToUid(uid: string, payload: PushPayload): Promise<void> {
  const tokens = await readTokens(uid);
  if (tokens.length === 0) {
    logger.info(`No tokens for ${uid} — skipping`);
    return;
  }
  await sendToTokens(uid, tokens, payload);
}

/** Send to every member of a crew except an optionally-excluded uid. */
export async function sendToCrew(
  crewId: string,
  exceptUid: string | null,
  payload: PushPayload,
): Promise<void> {
  const crew = (await getFirestore().doc(`crews/${crewId}`).get()).data();
  if (!crew) {
    logger.warn(`Crew ${crewId} not found`);
    return;
  }
  const memberIds: string[] = ((crew.memberIds as string[]) ?? []).filter(
    (id) => id !== exceptUid,
  );
  const tokensByUid = await Promise.all(
    memberIds.map(async (uid) => ({ uid, tokens: await readTokens(uid) })),
  );
  for (const { uid, tokens } of tokensByUid) {
    if (tokens.length === 0) continue;
    await sendToTokens(uid, tokens, payload);
  }
}

async function sendToTokens(
  uid: string,
  tokens: DeviceToken[],
  payload: PushPayload,
): Promise<void> {
  const data: Record<string, string> = {
    kind: payload.kind,
    key: payload.key,
    ...payload.data,
  };
  const message = {
    tokens: tokens.map((t) => t.token),
    notification: {
      title: payload.notificationTitle,
      body: payload.notificationBody,
    },
    data,
  };
  const response: BatchResponse = await getMessaging().sendEachForMulticast(message);
  await pruneUnregistered(uid, tokens, response);
}

async function pruneUnregistered(
  uid: string,
  tokens: DeviceToken[],
  response: BatchResponse,
): Promise<void> {
  for (let i = 0; i < response.responses.length; i++) {
    const r = response.responses[i];
    if (r.success) continue;
    const code = r.error?.code ?? "";
    if (
      code === "messaging/registration-token-not-registered" ||
      code === "messaging/invalid-registration-token"
    ) {
      await pruneToken(uid, tokens[i].token);
    } else {
      logger.warn(`Send failed for ${uid}/${tokens[i].token}: ${code}`);
    }
  }
}
