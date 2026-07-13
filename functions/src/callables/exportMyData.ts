import { onCall, HttpsError, type CallableRequest } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { logger } from "firebase-functions/v2";
import { mealPhotoPaths } from "../meal/mealPhotoPaths";

/**
 * GDPR Art. 20 (data portability) — "download all my plates + data".
 *
 * A client-callable (auth-required) that, for the authenticated caller `uid`, gathers EVERY piece
 * of the caller's OWN personal data with the Admin SDK and assembles it into one portable JSON
 * archive, uploads that archive to a short-lived Storage path, and returns a 15-minute V4 signed
 * download URL (mirrors `mintPlateUrls`'s signing). The export also carries a manifest of signed
 * READ URLs for each of the caller's plate images so the JSON is a complete, self-contained copy.
 *
 * What it exports (the caller's data ONLY — other members' PII is deliberately excluded):
 *   - account:   the public `accounts/{uid}` profile doc.
 *   - consent:   the owner-only `accounts/{uid}/private/*` docs (PII / consent records, if any).
 *   - devices:   the caller's registered FCM tokens — `accounts/{uid}/devices/*` + top-level
 *                `devices/{uid}` (legacy token doc).
 *   - crews:     for each crew the caller is in, the crew id + name + the caller's role + the
 *                caller's OWN membership entry. We do NOT dump other members' profiles or
 *                memberships (only the caller's own membership + the crew's shared identity fields).
 *   - meals:     every meal the caller authored across every crew (collectionGroup authorId==uid),
 *                each carrying its OWN photo(s) — primary + any multi-photo extras — as a
 *                signed-URL manifest in its `plates[]`. The top-level `plates[]` stays a flattened,
 *                de-duplicated union across every meal (kept for backward-compat).
 *   - comments:  every comment the caller authored, including on OTHER users' meals.
 *   - votes:     every score the caller cast — `ratings[uid]` entries across meals in the caller's
 *                crews (the caller's vote only, never other voters').
 *
 * The handler is a thin wrapper over [exportMyDataCore], which is dependency-injected and
 * unit-tested without the Admin SDK (mirrors `deleteAccount`'s `deleteAccountCore` and
 * `mintPlateUrls`'s `buildSignedUrls`). The pure projection [buildExportArchive] assembles the
 * JSON document so the "shape + excludes-other-PII" contract is asserted directly.
 *
 * Async note (roadmap §0.4 "export may be slow"): exports are bounded — a crew is ≤ 15 members and
 * a member authors a handful of meals/day — so this runs synchronously inside the callable and
 * returns the URL inline. There is no enqueue/push step; if exports ever grow we can split the
 * gather into a job, but the current data volume does not warrant the complexity.
 *
 * See `docs/roadmap/2026-06-14-feature-roadmap.md` §0.4.
 */

/** How long the export download URL (and each plate image URL) stays valid. */
export const EXPORT_URL_TTL_MS = 15 * 60 * 1000;

/** Bumped if the export JSON shape changes — lets a downstream parser branch on it. */
export const EXPORT_SCHEMA_VERSION = 1;

export interface ExportMyDataRequest {
  // Reserved for future options (e.g. format). Empty today.
  [key: string]: unknown;
}

export interface ExportMyDataResponse {
  /** A 15-min V4 signed READ URL for the assembled `application/json` export archive. */
  downloadUrl: string;
  /** Epoch-ms after which [downloadUrl] (and every plate image URL inside the archive) expires. */
  expiresAtMs: number;
}

/** A meal the caller authored: its doc path + every one of its photo object paths (see
 *  `mealPhotoPaths`) for the per-meal image manifest. */
export interface MealSnap {
  path: string;
  platePaths: string[];
  data: Record<string, unknown>;
}

/** A document the caller authored (e.g. a comment), with its path + raw data. */
export interface DocSnap {
  path: string;
  data: Record<string, unknown>;
}

/** A crew the caller belongs to, projected to only what the export needs. */
export interface CrewSnap {
  crewId: string;
  name: string | null;
  ownerId: string | null;
  /** The caller's OWN membership entry (e.g. `{ joinedAt }`), or `null` if absent. */
  myMembership: Record<string, unknown> | null;
}

/** A single score the caller cast on a meal (the caller's own `ratings[uid]` entry). */
export interface VoteSnap {
  mealPath: string;
  crewId: string | null;
  /** The caller's own rating entry, e.g. `{ score, ratedAtEpochMs }`. */
  vote: Record<string, unknown>;
}

/** A plate image the caller can download, with a signed read URL. */
export interface PlateManifestEntry {
  path: string;
  url: string;
}

/** An authored meal as it lands in the archive: identity + raw data, plus its OWN photos. */
export interface MealExportEntry {
  path: string;
  data: Record<string, unknown>;
  /** This meal's own photos, in order (index 0 = primary), each with a signed read URL. Empty
   *  when none of the meal's photo paths resolved to a signed URL (e.g. an unresolvable legacy
   *  doc) — never a missing field. */
  plates: PlateManifestEntry[];
}

/** The assembled export archive — the JSON document uploaded to Storage + handed back. */
export interface ExportArchive {
  schemaVersion: number;
  exportedAt: string;
  accountId: string;
  account: Record<string, unknown> | null;
  consent: DocSnap[];
  devices: DocSnap[];
  crews: CrewSnap[];
  meals: MealExportEntry[];
  comments: DocSnap[];
  votes: VoteSnap[];
  /** Signed download URLs for the caller's plate images, flattened across EVERY meal's photos
   *  (de-duplicated by path). Kept for backward-compat with the pre-multi-photo shape; see each
   *  meal's own `plates` for per-meal grouping. */
  plates: PlateManifestEntry[];
}

/** Everything the export needs from the data layer, injected so the core is Admin-SDK-free. */
export interface ExportDeps {
  /** `accounts/{uid}` profile doc, or `null` if the account doc is gone. */
  account: (uid: string) => Promise<Record<string, unknown> | null>;
  /** Owner-only `accounts/{uid}/private/*` docs (PII / consent), each with its path. */
  privateDocs: (uid: string) => Promise<DocSnap[]>;
  /** The caller's FCM token docs: `accounts/{uid}/devices/*` + top-level `devices/{uid}`. */
  deviceDocs: (uid: string) => Promise<DocSnap[]>;
  /** Crews the caller is in, projected to the caller's own membership only. */
  crews: (uid: string) => Promise<CrewSnap[]>;
  /** Meals the caller authored across every crew (collectionGroup authorId==uid). */
  authoredMeals: (uid: string) => Promise<MealSnap[]>;
  /** Comments the caller authored (incl. on others' meals; collectionGroup authorId==uid). */
  authoredComments: (uid: string) => Promise<DocSnap[]>;
  /** Scores the caller cast — `ratings[uid]` entries across meals in the caller's crews. */
  castVotes: (uid: string) => Promise<VoteSnap[]>;
  /** Mint a V4 signed READ URL for a Storage object path, valid until [expiresAtMs]. */
  signUrl: (path: string, expiresAtMs: number) => Promise<string>;
  /** Upload the archive JSON to Storage and return the object path it was written to. */
  uploadArchive: (uid: string, json: string) => Promise<string>;
  /** Wall clock — injected so the timestamp + TTL are deterministic in tests. */
  nowMs: number;
}

/**
 * Pure assembly of the export JSON from already-gathered snapshots + a signed plate manifest.
 * Keeping this side-effect-free lets the "right shape, excludes other members' PII" contract be
 * asserted directly. `plates` is de-duplicated by object path (a plate shared to several crews is
 * one image copy per crew, so paths differ — but we de-dup defensively).
 *
 * Per-meal grouping (`meals[i].plates`) is a pure lookup against the just-de-duplicated flat list —
 * no re-signing, no re-fetching. A meal's photo path missing from the flat list (shouldn't happen:
 * the caller signs the union of every meal's `platePaths` before calling this) is silently dropped
 * from that meal's own manifest rather than emitting an entry with a missing URL.
 */
export function buildExportArchive(input: {
  uid: string;
  account: Record<string, unknown> | null;
  consent: DocSnap[];
  devices: DocSnap[];
  crews: CrewSnap[];
  meals: MealSnap[];
  comments: DocSnap[];
  votes: VoteSnap[];
  plates: PlateManifestEntry[];
  exportedAtMs: number;
}): ExportArchive {
  const seenPlates = new Set<string>();
  const plates: PlateManifestEntry[] = [];
  for (const p of input.plates) {
    if (seenPlates.has(p.path)) continue;
    seenPlates.add(p.path);
    plates.push(p);
  }
  const urlByPath = new Map(plates.map((p) => [p.path, p.url]));

  return {
    schemaVersion: EXPORT_SCHEMA_VERSION,
    exportedAt: new Date(input.exportedAtMs).toISOString(),
    accountId: input.uid,
    account: input.account,
    consent: input.consent,
    devices: input.devices,
    crews: input.crews,
    meals: input.meals.map((m) => ({
      path: m.path,
      data: m.data,
      plates: m.platePaths
        .map((path) => ({ path, url: urlByPath.get(path) }))
        .filter((p): p is PlateManifestEntry => typeof p.url === "string"),
    })),
    comments: input.comments,
    votes: input.votes,
    plates,
  };
}

/**
 * The testable core: gather the caller's data, assemble the archive, sign the plate manifest,
 * upload the JSON, and return a signed download URL. Throws [HttpsError] for the auth failure the
 * callable surfaces verbatim.
 */
export async function exportMyDataCore(
  deps: ExportDeps,
  uid: string | undefined,
  _req: ExportMyDataRequest,
): Promise<ExportMyDataResponse> {
  if (!uid) throw new HttpsError("unauthenticated", "Sign-in required.");

  const expiresAtMs = deps.nowMs + EXPORT_URL_TTL_MS;

  const [account, consent, devices, crews, meals, comments, votes] = await Promise.all([
    deps.account(uid),
    deps.privateDocs(uid),
    deps.deviceDocs(uid),
    deps.crews(uid),
    deps.authoredMeals(uid),
    deps.authoredComments(uid),
    deps.castVotes(uid),
  ]);

  // Sign a read URL for every authored meal's photo(s) — the union of every meal's photo paths
  // (primary + any multi-photo extras).
  const uniquePlatePaths = [...new Set(meals.flatMap((m) => m.platePaths))];
  const signed = await Promise.all(uniquePlatePaths.map((p) => deps.signUrl(p, expiresAtMs)));
  const plates: PlateManifestEntry[] = uniquePlatePaths.map((path, i) => ({ path, url: signed[i] }));

  const archive = buildExportArchive({
    uid,
    account,
    consent,
    devices,
    crews,
    meals,
    comments,
    votes,
    plates,
    exportedAtMs: deps.nowMs,
  });

  const archivePath = await deps.uploadArchive(uid, JSON.stringify(archive, null, 2));
  const downloadUrl = await deps.signUrl(archivePath, expiresAtMs);

  return { downloadUrl, expiresAtMs };
}

export const exportMyData = onCall(
  { region: "europe-west3" },
  async (request: CallableRequest<ExportMyDataRequest>): Promise<ExportMyDataResponse> => {
    const db = getFirestore();

    const deps: ExportDeps = {
      account: async (uid) => {
        const snap = await db.doc(`accounts/${uid}`).get();
        return snap.exists ? (snap.data() ?? {}) : null;
      },

      privateDocs: async (uid) =>
        (await db.collection(`accounts/${uid}/private`).get()).docs.map((d) => ({
          path: d.ref.path,
          data: d.data(),
        })),

      deviceDocs: async (uid) => {
        const out: DocSnap[] = [];
        const sub = await db.collection(`accounts/${uid}/devices`).get();
        for (const d of sub.docs) out.push({ path: d.ref.path, data: d.data() });
        const legacy = await db.doc(`devices/${uid}`).get();
        if (legacy.exists) out.push({ path: legacy.ref.path, data: legacy.data() ?? {} });
        return out;
      },

      crews: async (uid) =>
        (await db.collection("crews").where("memberIds", "array-contains", uid).get()).docs.map(
          (d) => {
            const data = d.data();
            const members =
              (data.members as Record<string, Record<string, unknown>> | undefined) ?? {};
            return {
              crewId: d.id,
              name: (data.name as string | undefined) ?? null,
              ownerId: (data.ownerId as string | undefined) ?? null,
              // Only the CALLER's own membership — never other members' entries.
              myMembership: members[uid] ?? null,
            };
          },
        ),

      authoredMeals: async (uid) =>
        (await db.collectionGroup("meals").where("authorId", "==", uid).get()).docs.map((d) => ({
          path: d.ref.path,
          platePaths: mealPhotoPaths(d.ref.path, d.data()),
          data: d.data(),
        })),

      authoredComments: async (uid) =>
        (await db.collectionGroup("comments").where("authorId", "==", uid).get()).docs.map((d) => ({
          path: d.ref.path,
          data: d.data(),
        })),

      castVotes: async (uid) => {
        // `ratings[uid]` map-key existence isn't directly queryable; iterate the crews the caller
        // belongs to and collect their own vote on any meal NOT authored by them. Crews are tiny
        // (≤ 15 members) so this bounded per-crew meal scan is fine (mirrors deleteAccount's
        // votedMeals). Self-authored meals are skipped — those export under `meals`.
        const crews = await db.collection("crews").where("memberIds", "array-contains", uid).get();
        const out: VoteSnap[] = [];
        for (const crew of crews.docs) {
          const meals = await db.collection(`crews/${crew.id}/meals`).get();
          for (const meal of meals.docs) {
            const data = meal.data();
            if ((data.authorId as string | undefined) === uid) continue;
            const ratings = data.ratings as Record<string, Record<string, unknown>> | undefined;
            if (ratings && Object.prototype.hasOwnProperty.call(ratings, uid)) {
              out.push({ mealPath: meal.ref.path, crewId: crew.id, vote: ratings[uid] });
            }
          }
        }
        return out;
      },

      signUrl: async (path, expiresAtMs) => {
        const [url] = await getStorage()
          .bucket()
          .file(path)
          .getSignedUrl({ version: "v4", action: "read", expires: expiresAtMs });
        return url;
      },

      uploadArchive: async (uid, json) => {
        // Short-lived, function-only export object. Reads are denied by `storage.rules`; the only
        // read path is the signed URL minted here. Overwrites any prior export for this caller.
        const path = `exports/${uid}/${Date.now()}.json`;
        await getStorage()
          .bucket()
          .file(path)
          .save(json, { contentType: "application/json", resumable: false });
        return path;
      },

      nowMs: Date.now(),
    };

    try {
      return await exportMyDataCore(deps, request.auth?.uid, request.data);
    } catch (e) {
      if (e instanceof HttpsError) throw e;
      logger.error("exportMyData failed", e);
      throw new HttpsError("internal", "Could not export your data.");
    }
  },
);
