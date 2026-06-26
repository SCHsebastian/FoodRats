import { onCall, HttpsError, type CallableRequest } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { logger } from "firebase-functions/v2";

/**
 * Membership-checked V4 signed READ URLs for plate photos + crew-member avatars.
 *
 * Why this exists: uploads no longer mint Firebase download-token URLs (the `?token=…`
 * form bypasses Storage rules — anyone with the string can fetch). Storage objects are
 * now `read: if false`, so the only legitimate read path is a short-lived signed URL
 * minted by this server after it verifies the caller is a member of the crew. The client
 * stores the deterministic object PATH and resolves it through here on demand
 * (see `ImageUrlPort` / `FirebaseImageUrlResolver`).
 *
 * The handler is a thin wrapper over [buildSignedUrls], which is dependency-injected and
 * unit-tested without the Admin SDK (mirrors `weeklyDigest`'s `paginateCrews`).
 */

/**
 * How long a minted URL stays valid. GCS V4 signed URLs cap at 7 days; we use 6 to stay safely
 * under the ceiling. A long TTL lets the client cache (in memory + on disk) reuse the SAME URL for
 * days, which collapses both mintPlateUrls call volume AND image re-downloads — a rotated URL is a
 * Coil cache-miss → a full re-fetch from Storage egress. The client refreshes shortly before expiry.
 *
 * Revocation trade-off: a URL already minted by a member who is later removed from the crew stays
 * fetchable until it expires (≤6 days). Acceptable here — these are closed-crew food photos the
 * member could already see, and membership is still enforced at MINT time (a removed member cannot
 * mint new URLs). The client persists only immutable paths (plates, content-versioned avatars); the
 * fixed-path crew banner is kept on a short client-side TTL so a banner change still surfaces fast.
 */
export const URL_TTL_MS = 6 * 24 * 60 * 60 * 1000;

/** Maximum number of paths accepted in a single mintPlateUrls call. Requests above this
 *  limit are silently truncated before the signing loop to cap Cloud Function CPU + cost. */
export const MAX_PATHS = 200;

export interface MintRequest {
  crewId: string;
  paths: string[];
}

export interface MintResponse {
  /** Epoch-ms after which every URL in [urls] is expired. */
  expiresAtMs: number;
  /** Object path → signed read URL, for the authorized subset of the requested paths. */
  urls: Record<string, string>;
}

/** Minimal crew projection this callable needs. `null` ⇒ crew doc missing. */
export type ReadCrew = (crewId: string) => Promise<{ memberIds: string[] } | null>;
/** Mints a single V4 read URL for [path], valid until [expiresAtMs]. */
export type SignReadUrl = (path: string, expiresAtMs: number) => Promise<string>;

/**
 * Keeps only the paths the caller's crew is allowed to read:
 *  - plate photos under this crew: `crews/{crewId}/meals/*.jpg`
 *  - avatars of accounts that are members of this crew: content-versioned
 *    `avatars/{memberUid}/{token}.jpg` (and the legacy fixed `avatars/{memberUid}.jpg`)
 *  - the crew banner (C9): `crew_banners/{crewId}/banner.jpg`
 * Anything else is silently dropped (a stale/foreign path must not break a whole screen).
 */
export function authorizedPaths(
  crewId: string,
  memberIds: readonly string[],
  paths: readonly string[],
): string[] {
  const members = new Set(memberIds);
  const platePrefix = `crews/${crewId}/meals/`;
  const bannerPath = `crew_banners/${crewId}/banner.jpg`;
  const seen = new Set<string>();
  const out: string[] = [];
  for (const p of paths) {
    if (seen.has(p)) continue;
    seen.add(p);
    const isPlate = p.startsWith(platePrefix) && p.endsWith(".jpg");
    // uid is the first segment; an optional `/{token}` segment carries the content version.
    const avatar = /^avatars\/([^/]+)(?:\/[^/]+)?\.jpg$/.exec(p);
    const isMemberAvatar = avatar !== null && members.has(avatar[1]);
    const isBanner = p === bannerPath;
    if (isPlate || isMemberAvatar || isBanner) out.push(p);
  }
  return out;
}

/**
 * Keeps only the caller's OWN avatar paths — the no-crew "self-avatar" request (a signed-in user
 * with no active crew, e.g. just after sign-up or leaving a crew, still needs to resolve their own
 * top-bar avatar). Matches the content-versioned `avatars/{uid}/{token}.jpg` and the legacy fixed
 * `avatars/{uid}.jpg`, but only when `{uid}` is the caller. Everything else (plates, banners, other
 * users' avatars) is dropped. Dedupes like [authorizedPaths].
 */
export function ownAvatarPaths(uid: string, paths: readonly string[]): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const p of paths) {
    if (seen.has(p)) continue;
    seen.add(p);
    const avatar = /^avatars\/([^/]+)(?:\/[^/]+)?\.jpg$/.exec(p);
    if (avatar !== null && avatar[1] === uid) out.push(p);
  }
  return out;
}

/**
 * The testable core: verify membership, authorize the requested paths, sign them.
 * Throws [HttpsError] for the auth/permission failures the callable surfaces verbatim.
 *
 * An empty/blank `crewId` is a "self-avatar only" request: it skips the crew lookup + membership
 * check and authorizes only the caller's own avatar paths (see [ownAvatarPaths]).
 */
export async function buildSignedUrls(
  deps: { readCrew: ReadCrew; sign: SignReadUrl; nowMs: number },
  uid: string | undefined,
  request: MintRequest,
): Promise<MintResponse> {
  if (!uid) {
    throw new HttpsError("unauthenticated", "Sign-in required.");
  }
  const crewId = (request.crewId ?? "").trim();
  if (crewId === "") {
    // No active crew: authorize ONLY the caller's own avatar — no crew lookup, no membership check.
    const expiresAtMs = deps.nowMs + URL_TTL_MS;
    const allowed = ownAvatarPaths(uid, (request.paths ?? []).slice(0, MAX_PATHS));
    const signed = await Promise.all(allowed.map((p) => deps.sign(p, expiresAtMs)));
    const urls: Record<string, string> = {};
    allowed.forEach((p, i) => {
      urls[p] = signed[i];
    });
    return { expiresAtMs, urls };
  }
  const crew = await deps.readCrew(crewId);
  if (crew === null || !crew.memberIds.includes(uid)) {
    // Same error for "no such crew" and "not a member" — don't leak crew existence.
    throw new HttpsError("permission-denied", "Not a member of this crew.");
  }
  const expiresAtMs = deps.nowMs + URL_TTL_MS;
  const allowed = authorizedPaths(crewId, crew.memberIds, (request.paths ?? []).slice(0, MAX_PATHS));
  const signed = await Promise.all(allowed.map((p) => deps.sign(p, expiresAtMs)));
  const urls: Record<string, string> = {};
  allowed.forEach((p, i) => {
    urls[p] = signed[i];
  });
  return { expiresAtMs, urls };
}

export const mintPlateUrls = onCall(
  { region: "europe-west3" },
  async (request: CallableRequest<MintRequest>): Promise<MintResponse> => {
    const readCrew: ReadCrew = async (crewId) => {
      const snap = await getFirestore().doc(`crews/${crewId}`).get();
      if (!snap.exists) return null;
      const memberIds = (snap.data()?.memberIds as string[] | undefined) ?? [];
      return { memberIds };
    };
    const sign: SignReadUrl = async (path, expiresAtMs) => {
      const [url] = await getStorage()
        .bucket()
        .file(path)
        .getSignedUrl({ version: "v4", action: "read", expires: expiresAtMs });
      return url;
    };
    try {
      return await buildSignedUrls(
        { readCrew, sign, nowMs: Date.now() },
        request.auth?.uid,
        request.data,
      );
    } catch (e) {
      if (e instanceof HttpsError) throw e;
      logger.error("mintPlateUrls failed", e);
      throw new HttpsError("internal", "Could not mint image URLs.");
    }
  },
);
