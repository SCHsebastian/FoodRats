import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";

export interface DeviceToken {
  token: string;
  platform: "android" | "ios";
}

/** Read every registered device token for a uid. */
export async function readTokens(uid: string): Promise<DeviceToken[]> {
  const snap = await getFirestore().collection(`accounts/${uid}/devices`).get();
  return snap.docs.map((d) => {
    const data = d.data();
    return {
      token: d.id,
      platform: (data.platform ?? "android") as "android" | "ios",
    };
  });
}

/** Delete a stale token after FCM rejects it. */
export async function pruneToken(uid: string, token: string): Promise<void> {
  try {
    await getFirestore().doc(`accounts/${uid}/devices/${token}`).delete();
    logger.info(`Pruned stale token for ${uid}`);
  } catch (e) {
    logger.warn(`Failed to prune token for ${uid}`, e);
  }
}
