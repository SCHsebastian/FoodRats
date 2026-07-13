import { onDocumentDeleted } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { logger } from "firebase-functions/v2";
import { mealPhotoPaths } from "../meal/mealPhotoPaths";

// When a meal is deleted (by its author or by the crew owner), Firestore does NOT cascade
// to the meal's subcollections, and Storage objects are never touched by a Firestore delete.
// This trigger reclaims both:
//   1. the orphaned subcollections (`comments`, deprecated `ratings`), and
//   2. the meal's Storage blobs: EVERY photo (primary + multi-photo extras) AND the generated
//      `_thumb.jpg`.
//
// A plate shared to several crews is stored as one independent image copy PER crew
// (`crews/{crewId}/meals/{mealId}.jpg`) — a member can only mint a signed read URL for a
// plate under their own crew, so a shared object would be unviewable. That per-crew ownership
// means deletion needs NO ref-counting: each crew's deleted meal doc reclaims exactly its own
// copy, and a sibling copy in another crew is reclaimed when that crew's meal is deleted.
//
// The `onPlateImageFinalized` pipeline later writes a downscaled `_thumb.jpg` sibling
// (`crews/{crewId}/meals/{mealId}_thumb.jpg`) and records its path on the meal doc as
// `thumbnailPath`. We reclaim that too so a deleted meal leaves no orphaned thumbnail.
//
// Multi-photo meals (up to `MealPublishPolicy.MAX_PHOTOS_PER_MEAL`) persist every photo's path in
// the doc's ordered `plates[]` array (`crews/{crewId}/meals/{mealId}_p{n}.jpg` for the non-primary
// photos, n = 1..9); the primary photo keeps the legacy `{mealId}.jpg` object either way. We reclaim
// the FULL set — see `mealPhotoPaths` — not just the primary. Thumbnails stay PRIMARY-ONLY (only the
// primary photo ever gets a `_thumb.jpg`), so thumbnail reclaim below is unaffected by multi-photo.

/** Object-store seam — just the delete op, so the reclaim logic is unit-testable without the Admin SDK. */
export interface MealBlobStore {
  /** Best-effort delete; MUST tolerate a missing object (return without throwing). */
  delete(path: string): Promise<void>;
}

/**
 * The deleted meal's PRIMARY photo + its thumbnail path. Prefers the paths persisted on the deleted
 * doc; falls back to the deterministic upload scheme for older meals (e.g. published before
 * `thumbnailPath` was written, or a plate whose thumbnail pipeline never ran). For a multi-photo
 * meal's FULL photo set (primary + every extra photo), see `mealPhotoPaths` — thumbnails stay
 * PRIMARY-ONLY, so this function's `thumbnailPath` half is unaffected by multi-photo.
 */
export function mealStoragePaths(
  crewId: string,
  mealId: string,
  doc: { platePath?: string; thumbnailPath?: string } | undefined,
): { platePath: string; thumbnailPath: string } {
  return {
    // `||` (not `??`) on purpose: a doc that persisted platePath: "" must fall back to the
    // deterministic scheme — deleting "" silently orphans the real blob. Same semantics as
    // `mealPhotoPaths`, which treats "" (or a non-string) platePath as missing.
    platePath: doc?.platePath || `crews/${crewId}/meals/${mealId}.jpg`,
    thumbnailPath: doc?.thumbnailPath || `crews/${crewId}/meals/${mealId}_thumb.jpg`,
  };
}

/**
 * Reclaims a deleted meal's FULL photo set (primary + every multi-photo extra — see
 * `mealPhotoPaths`) plus its generated thumbnail. Each delete is independent and best-effort: a
 * missing object (older meal, never-thumbnailed plate, or a double-fire) or a transient error on one
 * object is logged and does NOT abort the other deletes. Idempotent — safe to re-run; deleting an
 * absent object is a no-op.
 */
export async function reclaimMealObjects(
  blobs: MealBlobStore,
  crewId: string,
  mealId: string,
  doc: { platePath?: string; thumbnailPath?: string; plates?: unknown } | undefined,
): Promise<void> {
  const { thumbnailPath } = mealStoragePaths(crewId, mealId, doc);
  const platePaths = mealPhotoPaths(`crews/${crewId}/meals/${mealId}`, doc);

  for (const platePath of platePaths) {
    try {
      await blobs.delete(platePath);
    } catch (e) {
      logger.error(`onMealDeleted: plate image delete failed for ${platePath}`, e);
    }
  }

  try {
    await blobs.delete(thumbnailPath);
  } catch (e) {
    logger.error(`onMealDeleted: thumbnail delete failed for ${thumbnailPath}`, e);
  }
}

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

    // 2) Storage blobs (full plate + generated thumbnail). `ignoreNotFound` tolerates a publish
    //    that already cleaned up its own orphan, an older meal with no thumbnail, or a double-fire.
    const bucket = getStorage().bucket();
    const blobs: MealBlobStore = {
      delete: async (path) => {
        await bucket.file(path).delete({ ignoreNotFound: true });
      },
    };
    const doc = event.data?.data() as
      | { platePath?: string; thumbnailPath?: string; plates?: unknown }
      | undefined;
    await reclaimMealObjects(blobs, crewId, mealId, doc);
  },
);
