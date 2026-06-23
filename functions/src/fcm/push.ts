import { getMessaging, BatchResponse } from "firebase-admin/messaging";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { readTokens, pruneToken, DeviceToken } from "./tokens";
import { localizeNotification, normalizeLang } from "../i18n/keys";

export interface PushPayload {
  kind: "NewComment" | "NewMealPost" | "WeeklyDigest" | "SocialNudge";
  key: string;
  notificationTitle: string;
  notificationBody: string;
  data: Record<string, string>;
}

/**
 * Canonical custom-scheme deep link into a meal's detail screen. Mirrors the client URL contract
 * in `shared/.../app/navigation/DeepLink.kt` (`DeepLinks` + `parseDeepLink`): the first path
 * segment is the discriminator, so `foodrats://app/meal/{mealId}/{dayIso}` resolves to
 * `Route.MealDetail`. Carried in the FCM `data` under `link`; the apps forward it to the
 * DeepLinkBus on notification tap. Keep in sync with that contract.
 */
export function mealDeepLink(mealId: string, dayIso: string): string {
  return `foodrats://app/meal/${mealId}/${dayIso}`;
}

/**
 * Canonical deep link into the weekly-recap story (roadmap §2.4). Mirrors the client URL contract in
 * `shared/.../app/navigation/DeepLink.kt` (`SEGMENT_DIGEST` → `Route.WeeklyStory`): the first path
 * segment is the discriminator, so `foodrats://app/digest/{weekStart}` opens the swipeable recap.
 * Carried in the FCM `data` under `link`; the apps forward it to the DeepLinkBus on the digest tap.
 * Keep in sync with that contract.
 */
export function digestDeepLink(weekStartIso: string): string {
  return `foodrats://app/digest/${weekStartIso}`;
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
  await Promise.all(
    tokensByUid
      .filter(({ tokens }) => tokens.length > 0)
      .map(({ uid, tokens }) => sendToTokens(uid, tokens, payload)),
  );
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
  // The OS renders the `notification` block of a backgrounded push directly, with no chance for the
  // client to localize it — so we localize it HERE, per device language. Tokens are grouped by their
  // stored languageTag (English fallback when absent), and each group gets its own multicast with a
  // localized title/body. The `data` block is identical for all (the client re-localizes in foreground).
  const groups = new Map<"en" | "es", DeviceToken[]>();
  for (const t of tokens) {
    const lang = normalizeLang(t.languageTag);
    (groups.get(lang) ?? groups.set(lang, []).get(lang)!).push(t);
  }

  await Promise.all(
    [...groups.entries()].map(async ([lang, groupTokens]) => {
      const localized = localizeNotification(lang, payload.key, data);
      const message = {
        tokens: groupTokens.map((t) => t.token),
        notification: {
          // Fall back to the payload's English default for any key without a localizer.
          title: localized?.title ?? payload.notificationTitle,
          body: localized?.body ?? payload.notificationBody,
        },
        data,
      };
      const response: BatchResponse = await getMessaging().sendEachForMulticast(message);
      await pruneUnregistered(uid, groupTokens, response);
    }),
  );
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
