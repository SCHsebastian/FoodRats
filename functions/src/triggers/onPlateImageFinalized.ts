import { onObjectFinalized, type StorageEvent } from "firebase-functions/v2/storage";
import { getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { logger } from "firebase-functions/v2";
import sharp from "sharp";
import { rgbaToThumbHash } from "thumbhash";

/**
 * Image pipeline — SERVER side (roadmap §5.1).
 *
 * When a plate photo is finalized in Storage, this trigger:
 *   1. computes a tiny ThumbHash placeholder (a ~25-byte hash, persisted base64) so the client
 *      can paint an instant blurred placeholder while the full image is still loading, and
 *   2. generates a downscaled JPEG thumbnail uploaded alongside the original, so the feed can
 *      load the small variant and the detail screen the full image.
 * Both the hash and the thumbnail object PATH are written onto the owning meal Firestore doc.
 *
 * Why ThumbHash (not BlurHash): the roadmap leaves the choice open. ThumbHash encodes the
 * aspect ratio (and alpha) inside the hash itself, produces a noticeably more faithful blur
 * than BlurHash at a *smaller* byte size, and decodes with no parameters — the client just
 * needs the hash bytes. It is a single compact base64 string on the meal doc.
 *
 * Loop safety: this trigger writes a `_thumb.jpg` object into the SAME `crews/{crewId}/meals/`
 * prefix it watches, which would re-fire it. We guard TWICE — by object NAME shape
 * (only `<mealId>.jpg`, never `*_thumb.jpg`) and by a custom metadata MARKER we stamp on the
 * thumbnail (`thumbnailGenerated=true`). Either guard alone is sufficient; both is defense in depth.
 *
 * Idempotency: re-running on the same plate is a no-op once the meal already carries a `thumbHash`
 * (a double-fire, or a re-upload of an identical object, won't duplicate work or flap the doc).
 *
 * Missing meal: if the meal doc was deleted between upload and this trigger (or never existed),
 * we log and return — no thumbnail is left orphaned because we only upload it AFTER resolving the
 * meal id from the path; an orphaned `_thumb.jpg` is reclaimed by `onMealDeleted`'s prefix-aware
 * cleanup the same way the original is.
 */

/**
 * The Storage bucket this trigger watches. Pinned explicitly (rather than derived from
 * `FIREBASE_CONFIG`) for two reasons: it makes the binding deterministic, and `onObjectFinalized`
 * validates the bucket at MODULE-DEFINITION time — an unset `FIREBASE_CONFIG` (e.g. in a unit-test
 * import) would otherwise throw "Missing bucket name" just by importing this file. Matches
 * `.firebaserc`'s default storage target.
 */
export const PLATE_BUCKET = "foodrats-de4ec.firebasestorage.app";

/** Marker stamped onto generated thumbnails so re-finalization is ignored. */
export const THUMBNAIL_METADATA_MARKER = "thumbnailGenerated";

/** Suffix that turns a plate object name into its thumbnail object name. */
export const THUMBNAIL_SUFFIX = "_thumb";

/** Max edge (px) of the generated thumbnail. The feed never needs more than this. */
export const THUMBNAIL_MAX_EDGE = 512;

/** JPEG quality for the thumbnail. */
export const THUMBNAIL_QUALITY = 75;

/**
 * Max edge (px) fed to ThumbHash. ThumbHash REQUIRES width*height be small (≤ 100 per side);
 * a larger buffer overflows its DCT and throws. 100 is its documented practical cap.
 */
export const THUMBHASH_MAX_EDGE = 100;

/** A resolved plate object: the crew + meal it belongs to, and the storage paths involved. */
export interface PlateObject {
  crewId: string;
  mealId: string;
  /** The original plate object path, e.g. `crews/c1/meals/c1_uid_2026-06-14_lunch.jpg`. */
  platePath: string;
  /** Where the generated thumbnail will be written, e.g. `crews/c1/meals/..._thumb.jpg`. */
  thumbnailPath: string;
}

/**
 * Pure path classifier. Returns the resolved [PlateObject] for an original plate image we should
 * process, or `null` for anything we must ignore: thumbnails we generated, non-plate paths, the
 * wrong extension, or a path missing the crew/meal segments.
 *
 * Path contract (see `PlateStorageDataSource` / `storage.rules`):
 *   `crews/{crewId}/meals/{mealId}.jpg`  with mealId = `<crewId>_<uid>_<dayKey>_<slot>`.
 */
export function classifyPlateObject(
  objectName: string | undefined,
  contentType: string | undefined,
): PlateObject | null {
  if (!objectName) return null;
  // Only JPEGs (the upload always sets image/jpeg). A missing content-type still passes the
  // extension check below, but a present, non-JPEG type is a hard reject.
  if (contentType && !/^image\/jpe?g$/.test(contentType)) return null;

  const match = /^crews\/([^/]+)\/meals\/([^/]+)\.jpg$/.exec(objectName);
  if (match === null) return null;

  const crewId = match[1];
  const fileStem = match[2];
  // The thumbnail we write lives under the same prefix with the same `.jpg` extension — skip it.
  if (fileStem.endsWith(THUMBNAIL_SUFFIX)) return null;

  const mealId = fileStem;
  return {
    crewId,
    mealId,
    platePath: objectName,
    thumbnailPath: `crews/${crewId}/meals/${mealId}${THUMBNAIL_SUFFIX}.jpg`,
  };
}

/** True when this finalized object is a thumbnail WE generated (metadata marker present). */
export function isGeneratedThumbnail(
  metadata: Record<string, string> | undefined,
): boolean {
  return metadata?.[THUMBNAIL_METADATA_MARKER] === "true";
}

/** What [processPlateImage] derives from the original image bytes. */
export interface PlateDerivatives {
  /** ThumbHash bytes, base64-encoded for storage on the meal doc. */
  thumbHash: string;
  /** The downscaled JPEG thumbnail bytes. */
  thumbnail: Buffer;
}

/**
 * The image core, dependency-injected so it is unit-testable without the real `sharp` native
 * binary or any Storage/Firestore. Given the original JPEG bytes, returns the base64 ThumbHash
 * and the thumbnail JPEG. Pure aside from CPU work.
 */
export async function processPlateImage(
  original: Buffer,
  deps: ImageOps = sharpImageOps,
): Promise<PlateDerivatives> {
  // ThumbHash needs a TINY RGBA raster (≤ 100 per side, alpha included).
  const small = await deps.toRgbaRaster(original, THUMBHASH_MAX_EDGE);
  const hashBytes = rgbaToThumbHash(small.width, small.height, small.data);
  const thumbHash = Buffer.from(hashBytes).toString("base64");

  const thumbnail = await deps.toThumbnailJpeg(
    original,
    THUMBNAIL_MAX_EDGE,
    THUMBNAIL_QUALITY,
  );
  return { thumbHash, thumbnail };
}

/** A small RGBA raster: the inputs ThumbHash needs. */
export interface RgbaRaster {
  width: number;
  height: number;
  data: Uint8Array;
}

/** The two `sharp` operations [processPlateImage] needs, behind an interface for testing. */
export interface ImageOps {
  /** Downscale to fit within `maxEdge` and return raw RGBA pixels (alpha forced). */
  toRgbaRaster(original: Buffer, maxEdge: number): Promise<RgbaRaster>;
  /** Downscale to fit within `maxEdge` and re-encode as JPEG at `quality`. */
  toThumbnailJpeg(original: Buffer, maxEdge: number, quality: number): Promise<Buffer>;
}

/** Production `sharp`-backed implementation of [ImageOps]. */
export const sharpImageOps: ImageOps = {
  async toRgbaRaster(original, maxEdge) {
    const { data, info } = await sharp(original)
      .resize(maxEdge, maxEdge, { fit: "inside", withoutEnlargement: true })
      .ensureAlpha()
      .raw()
      .toBuffer({ resolveWithObject: true });
    return { width: info.width, height: info.height, data };
  },
  async toThumbnailJpeg(original, maxEdge, quality) {
    return sharp(original)
      .resize(maxEdge, maxEdge, { fit: "inside", withoutEnlargement: true })
      .jpeg({ quality })
      .toBuffer();
  },
};

/** The meal's processing status, as read from Firestore. */
export type MealStatus =
  | { kind: "missing" } // no meal doc (deleted before this fired, or never written)
  | { kind: "unprocessed" } // meal exists, no thumbHash yet → process it
  | { kind: "processed" }; // meal already carries a thumbHash → idempotent skip

/** The meal projection the pipeline reads + the writes it performs, behind an interface. */
export interface MealStore {
  /** Reports whether the meal doc exists and whether it already has a `thumbHash`. */
  readStatus(crewId: string, mealId: string): Promise<MealStatus>;
  /** Persists the hash + thumbnail path onto the meal doc (merge). */
  writeDerivatives(
    crewId: string,
    mealId: string,
    fields: { thumbHash: string; thumbnailPath: string },
  ): Promise<void>;
}

/** Storage side the pipeline needs, behind an interface for testing. */
export interface PlateBlobStore {
  download(path: string): Promise<Buffer>;
  /** Uploads bytes; MUST stamp the [THUMBNAIL_METADATA_MARKER] so the upload doesn't re-trigger. */
  uploadThumbnail(path: string, bytes: Buffer): Promise<void>;
}

/** Discrete outcomes of [runPlatePipeline] — makes the loop-guard / no-op branches assertable. */
export type PipelineOutcome =
  | "ignored-not-plate"
  | "ignored-thumbnail-marker"
  | "no-meal"
  | "already-processed"
  | "processed";

/**
 * The testable orchestration core (mirrors `mintPlateUrls.buildSignedUrls`): given the finalized
 * object's identity + injected stores, decides whether to process and performs the side effects.
 * No Admin SDK, no real `sharp` — everything is injected, so every branch is unit-testable.
 */
export async function runPlatePipeline(
  deps: { meals: MealStore; blobs: PlateBlobStore; imageOps?: ImageOps },
  object: { name: string | undefined; contentType: string | undefined; metadata: Record<string, string> | undefined },
): Promise<PipelineOutcome> {
  if (isGeneratedThumbnail(object.metadata)) return "ignored-thumbnail-marker";

  const plate = classifyPlateObject(object.name, object.contentType);
  if (plate === null) return "ignored-not-plate";

  const status = await deps.meals.readStatus(plate.crewId, plate.mealId);
  if (status.kind === "missing") return "no-meal";
  if (status.kind === "processed") return "already-processed";

  const original = await deps.blobs.download(plate.platePath);
  const derived = await processPlateImage(original, deps.imageOps);
  await deps.blobs.uploadThumbnail(plate.thumbnailPath, derived.thumbnail);
  await deps.meals.writeDerivatives(plate.crewId, plate.mealId, {
    thumbHash: derived.thumbHash,
    thumbnailPath: plate.thumbnailPath,
  });
  return "processed";
}

export const onPlateImageFinalized = onObjectFinalized(
  {
    bucket: PLATE_BUCKET,
    // Storage (GCS/Eventarc) triggers MUST run in the same region as the watched bucket.
    // The bucket foodrats-de4ec.firebasestorage.app lives in us-east1, so this single trigger
    // is pinned there even though every other function stays in europe-west3.
    region: "us-east1",
    // Bumped from the 256 MiB default: decoding a 5 MiB JPEG to a full RGBA raster is memory-hungry.
    memory: "512MiB",
  },
  async (event: StorageEvent) => {
    const { name, contentType, metadata } = event.data;
    const db = getFirestore();
    const bucket = getStorage().bucket();

    const meals: MealStore = {
      async readStatus(crewId, mealId) {
        const snap = await db.doc(`crews/${crewId}/meals/${mealId}`).get();
        if (!snap.exists) return { kind: "missing" };
        return snap.data()?.thumbHash ? { kind: "processed" } : { kind: "unprocessed" };
      },
      async writeDerivatives(crewId, mealId, fields) {
        await db.doc(`crews/${crewId}/meals/${mealId}`).set(fields, { merge: true });
      },
    };
    const blobs: PlateBlobStore = {
      async download(path) {
        const [buf] = await bucket.file(path).download();
        return buf;
      },
      async uploadThumbnail(path, bytes) {
        // Stamp the loop-guard marker so this object's own finalize is ignored.
        await bucket.file(path).save(bytes, {
          contentType: "image/jpeg",
          metadata: { metadata: { [THUMBNAIL_METADATA_MARKER]: "true" } },
        });
      },
    };

    try {
      const outcome = await runPlatePipeline({ meals, blobs }, { name, contentType, metadata });
      if (outcome === "no-meal") {
        logger.info(`onPlateImageFinalized: no meal for ${name}; skipping.`);
      }
    } catch (e) {
      logger.error(`onPlateImageFinalized: pipeline failed for ${name}`, e);
    }
  },
);
