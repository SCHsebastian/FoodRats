import { describe, expect, it, vi } from "vitest";
import {
  classifyPlateObject,
  EXTRA_PHOTO_SUFFIX,
  IMMUTABLE_CACHE_CONTROL,
  isGeneratedThumbnail,
  processPlateImage,
  runPlatePipeline,
  THUMBNAIL_METADATA_MARKER,
  THUMBNAIL_MAX_EDGE,
  THUMBNAIL_QUALITY,
  THUMBHASH_MAX_EDGE,
  type ImageOps,
  type MealStatus,
  type MealStore,
  type PlateBlobStore,
} from "../src/triggers/onPlateImageFinalized";

const PLATE = "crews/c1/meals/c1_alice_2026-06-14_lunch.jpg";

describe("classifyPlateObject — path filter (§5.1)", () => {
  it("resolves an original plate JPEG to its crew/meal + thumbnail path", () => {
    expect(classifyPlateObject(PLATE, "image/jpeg")).toEqual({
      crewId: "c1",
      mealId: "c1_alice_2026-06-14_lunch",
      platePath: PLATE,
      thumbnailPath: "crews/c1/meals/c1_alice_2026-06-14_lunch_thumb.jpg",
    });
  });

  it("accepts image/jpg as well as image/jpeg", () => {
    expect(classifyPlateObject(PLATE, "image/jpg")?.crewId).toBe("c1");
  });

  it("accepts a missing content-type (extension is authoritative)", () => {
    expect(classifyPlateObject(PLATE, undefined)?.mealId).toBe("c1_alice_2026-06-14_lunch");
  });

  it("IGNORES a thumbnail we generated (loop guard by name)", () => {
    expect(classifyPlateObject("crews/c1/meals/c1_alice_2026-06-14_lunch_thumb.jpg", "image/jpeg"))
      .toBeNull();
  });

  it("ignores non-plate paths, wrong extensions, non-JPEG types, and undefined names", () => {
    expect(classifyPlateObject("avatars/alice.jpg", "image/jpeg")).toBeNull();
    expect(classifyPlateObject("crews/c1/meals/x.png", "image/png")).toBeNull();
    expect(classifyPlateObject("crews/c1/meals/x.jpg", "application/octet-stream")).toBeNull();
    expect(classifyPlateObject("exports/u1/dump.zip", "application/zip")).toBeNull();
    expect(classifyPlateObject(undefined, "image/jpeg")).toBeNull();
    // nested path under meals/ is not a direct child → no match
    expect(classifyPlateObject("crews/c1/meals/sub/x.jpg", "image/jpeg")).toBeNull();
  });

  it("ignores a bare '_thumb.jpg' stem and a stem that merely ends in _thumb", () => {
    expect(classifyPlateObject("crews/c1/meals/_thumb.jpg", "image/jpeg")).toBeNull();
    expect(classifyPlateObject("crews/c1/meals/dinner_thumb.jpg", "image/jpeg")).toBeNull();
  });

  it("ignores paths with empty crew or meal segments", () => {
    expect(classifyPlateObject("crews//meals/x.jpg", "image/jpeg")).toBeNull();
    expect(classifyPlateObject("crews/c1/meals/.jpg", "image/jpeg")).toBeNull();
  });

  it("extension and content-type checks are lowercase-only (documented current behavior)", () => {
    // GCS content types + client uploads are lowercase, so the strict match is fine in practice —
    // but an uppercase variant IS rejected today. Documented so a future relaxation is deliberate.
    expect(classifyPlateObject("crews/c1/meals/x.JPG", "image/jpeg")).toBeNull();
    expect(classifyPlateObject(PLATE, "image/JPEG")).toBeNull();
  });
});

describe("classifyPlateObject — multi-photo extra-photo skip", () => {
  it("skips a first extra photo (_p1) — PRIMARY-ONLY pipeline never sees it", () => {
    expect(
      classifyPlateObject("crews/c1/meals/c1_alice_2026-06-14_lunch_p1.jpg", "image/jpeg"),
    ).toBeNull();
  });

  it("skips a double-digit extra photo (_p12)", () => {
    expect(
      classifyPlateObject("crews/c1/meals/c1_alice_2026-06-14_lunch_p12.jpg", "image/jpeg"),
    ).toBeNull();
  });

  it("still processes the PRIMARY photo (no _p suffix)", () => {
    expect(classifyPlateObject(PLATE, "image/jpeg")).toEqual({
      crewId: "c1",
      mealId: "c1_alice_2026-06-14_lunch",
      platePath: PLATE,
      thumbnailPath: "crews/c1/meals/c1_alice_2026-06-14_lunch_thumb.jpg",
    });
  });

  it("still skips the generated thumbnail (both guards live in the same function)", () => {
    expect(
      classifyPlateObject("crews/c1/meals/c1_alice_2026-06-14_lunch_thumb.jpg", "image/jpeg"),
    ).toBeNull();
  });

  it("does NOT collide with a legacy hex-token mealId that merely ends in a digit (no literal 'p')", () => {
    // Documents the non-collision the naming scheme relies on: a token tail like "_2" has no
    // literal "p" before the digits, so EXTRA_PHOTO_SUFFIX does not match it and the object is
    // still processed as a (legacy) primary plate.
    const name = "crews/c1/meals/c1_alice_2026-06-14_lunch_2.jpg";
    expect(EXTRA_PHOTO_SUFFIX.test("c1_alice_2026-06-14_lunch_2")).toBe(false);
    expect(classifyPlateObject(name, "image/jpeg")).toEqual({
      crewId: "c1",
      mealId: "c1_alice_2026-06-14_lunch_2",
      platePath: name,
      thumbnailPath: "crews/c1/meals/c1_alice_2026-06-14_lunch_2_thumb.jpg",
    });
  });
});

describe("classifyPlateObject — multi-photo suffix edge cases (test-hardening pass)", () => {
  it("skips a hypothetical extra-photo thumbnail name (_p1_thumb.jpg) via the thumb-suffix guard", () => {
    // This exact object never gets generated in production (thumbnails are PRIMARY-ONLY — see
    // module doc), but the thumb-suffix check fires BEFORE the extra-photo check (fileStem ends
    // in "_thumb", not "_p1"), so it's excluded either way — defense in depth, locked here.
    expect(
      classifyPlateObject("crews/c1/meals/c1_alice_2026-06-14_lunch_p1_thumb.jpg", "image/jpeg"),
    ).toBeNull();
  });

  it("skips a ZERO-PADDED extra photo (_p01) — \\d+ has no leading-zero restriction", () => {
    expect(
      classifyPlateObject("crews/c1/meals/c1_alice_2026-06-14_lunch_p01.jpg", "image/jpeg"),
    ).toBeNull();
  });

  it("skips double- and triple-digit extra photos (_p10, _p999)", () => {
    expect(
      classifyPlateObject("crews/c1/meals/c1_alice_2026-06-14_lunch_p10.jpg", "image/jpeg"),
    ).toBeNull();
    expect(
      classifyPlateObject("crews/c1/meals/c1_alice_2026-06-14_lunch_p999.jpg", "image/jpeg"),
    ).toBeNull();
  });

  it("does NOT skip a mealId that merely CONTAINS '_p2_' mid-string, not as a suffix (lock actual)", () => {
    // EXTRA_PHOTO_SUFFIX is end-anchored (/_p\d+$/): "..._p2_x" doesn't end in digits right after
    // "p" (it ends in "_x"), so this is processed as an (unusual) PRIMARY plate, not skipped as an
    // extra photo. This mealId shape can't arise from the real id scheme (dayKey/slot vocabulary
    // never produces it), so the anchoring only matters against adversarial/synthetic names, not a
    // real collision risk in practice.
    const name = "crews/c1/meals/c1_alice_2026-06-14_lunch_p2_x.jpg";
    expect(EXTRA_PHOTO_SUFFIX.test("c1_alice_2026-06-14_lunch_p2_x")).toBe(false);
    expect(classifyPlateObject(name, "image/jpeg")).toEqual({
      crewId: "c1",
      mealId: "c1_alice_2026-06-14_lunch_p2_x",
      platePath: name,
      thumbnailPath: "crews/c1/meals/c1_alice_2026-06-14_lunch_p2_x_thumb.jpg",
    });
  });

  it("still processes a legacy hex-token tail that looks numeric with two digits (_99, no literal 'p')", () => {
    const name = "crews/c1/meals/c1_alice_2026-06-14_lunch_99.jpg";
    expect(EXTRA_PHOTO_SUFFIX.test("c1_alice_2026-06-14_lunch_99")).toBe(false);
    expect(classifyPlateObject(name, "image/jpeg")).toEqual({
      crewId: "c1",
      mealId: "c1_alice_2026-06-14_lunch_99",
      platePath: name,
      thumbnailPath: "crews/c1/meals/c1_alice_2026-06-14_lunch_99_thumb.jpg",
    });
  });
});

describe("isGeneratedThumbnail — loop guard by metadata marker", () => {
  it("is true only when the marker is exactly 'true'", () => {
    expect(isGeneratedThumbnail({ [THUMBNAIL_METADATA_MARKER]: "true" })).toBe(true);
  });
  it("is false for absent / other metadata", () => {
    expect(isGeneratedThumbnail(undefined)).toBe(false);
    expect(isGeneratedThumbnail({})).toBe(false);
    expect(isGeneratedThumbnail({ other: "x" })).toBe(false);
    expect(isGeneratedThumbnail({ [THUMBNAIL_METADATA_MARKER]: "false" })).toBe(false);
  });
});

describe("processPlateImage — hash + thumbnail core (sharp mocked)", () => {
  // Deterministic fake `sharp`: a 4x4 mid-grey RGBA raster + a stub thumbnail buffer.
  const fakeOps: ImageOps = {
    toRgbaRaster: vi.fn(async (_orig: Buffer, _max: number) => ({
      width: 4,
      height: 4,
      data: new Uint8Array(4 * 4 * 4).fill(128),
    })),
    toThumbnailJpeg: vi.fn(async () => Buffer.from("THUMB-BYTES")),
  };

  it("produces a base64 ThumbHash and the thumbnail, calling sharp with the configured sizes", async () => {
    const out = await processPlateImage(Buffer.from("ORIGINAL"), fakeOps);

    expect(out.thumbnail.toString()).toBe("THUMB-BYTES");
    // ThumbHash → base64 round-trips back to bytes.
    expect(out.thumbHash).toMatch(/^[A-Za-z0-9+/]+=*$/);
    expect(Buffer.from(out.thumbHash, "base64").length).toBeGreaterThan(0);

    expect(fakeOps.toRgbaRaster).toHaveBeenCalledWith(expect.any(Buffer), THUMBHASH_MAX_EDGE);
    expect(fakeOps.toThumbnailJpeg).toHaveBeenCalledWith(
      expect.any(Buffer),
      THUMBNAIL_MAX_EDGE,
      THUMBNAIL_QUALITY,
    );
  });
});

// --- Orchestration core: every branch unit-tested without Admin SDK / real sharp ---

const okImageOps: ImageOps = {
  toRgbaRaster: async () => ({ width: 4, height: 4, data: new Uint8Array(64).fill(200) }),
  toThumbnailJpeg: async () => Buffer.from("thumb"),
};

function mealStore(status: MealStatus): { store: MealStore; written: unknown[] } {
  const written: unknown[] = [];
  return {
    written,
    store: {
      readStatus: async () => status,
      writeDerivatives: async (crewId, mealId, fields) => {
        written.push({ crewId, mealId, fields });
      },
    },
  };
}

function blobStore(): {
  store: PlateBlobStore;
  uploads: { path: string; cacheControl: string }[];
  cacheControlSet: { path: string; cacheControl: string }[];
} {
  const uploads: { path: string; cacheControl: string }[] = [];
  const cacheControlSet: { path: string; cacheControl: string }[] = [];
  return {
    uploads,
    cacheControlSet,
    store: {
      download: async () => Buffer.from("ORIGINAL"),
      uploadThumbnail: async (path, _bytes, cacheControl) => {
        uploads.push({ path, cacheControl });
      },
      setCacheControl: async (path, cacheControl) => {
        cacheControlSet.push({ path, cacheControl });
      },
    },
  };
}

const plateObject = {
  name: PLATE,
  contentType: "image/jpeg",
  metadata: undefined as Record<string, string> | undefined,
};

describe("runPlatePipeline — orchestration", () => {
  it("processes an unprocessed meal: uploads thumbnail + writes hash & path", async () => {
    const m = mealStore({ kind: "unprocessed" });
    const b = blobStore();

    const outcome = await runPlatePipeline(
      { meals: m.store, blobs: b.store, imageOps: okImageOps },
      plateObject,
    );

    expect(outcome).toBe("processed");
    expect(b.uploads).toEqual([
      {
        path: "crews/c1/meals/c1_alice_2026-06-14_lunch_thumb.jpg",
        cacheControl: IMMUTABLE_CACHE_CONTROL,
      },
    ]);
    expect(m.written).toHaveLength(1);
    const w = m.written[0] as { crewId: string; mealId: string; fields: { thumbHash: string; thumbnailPath: string } };
    expect(w.crewId).toBe("c1");
    expect(w.fields.thumbnailPath).toBe("crews/c1/meals/c1_alice_2026-06-14_lunch_thumb.jpg");
    expect(w.fields.thumbHash.length).toBeGreaterThan(0);
  });

  it("stamps the immutable cache-control on BOTH the plate and the thumbnail (IMAGE-6)", async () => {
    const m = mealStore({ kind: "unprocessed" });
    const b = blobStore();

    const outcome = await runPlatePipeline(
      { meals: m.store, blobs: b.store, imageOps: okImageOps },
      plateObject,
    );

    expect(outcome).toBe("processed");
    expect(IMMUTABLE_CACHE_CONTROL).toBe("public, max-age=2592000, immutable");
    // Thumbnail: cache header set atomically on upload (the new content-addressed object).
    expect(b.uploads).toEqual([
      {
        path: "crews/c1/meals/c1_alice_2026-06-14_lunch_thumb.jpg",
        cacheControl: IMMUTABLE_CACHE_CONTROL,
      },
    ]);
    // Plate: cache header back-filled on the already-uploaded original.
    expect(b.cacheControlSet).toEqual([
      { path: PLATE, cacheControl: IMMUTABLE_CACHE_CONTROL },
    ]);
  });

  it("no-ops when the meal is missing (deleted before trigger fired)", async () => {
    const m = mealStore({ kind: "missing" });
    const b = blobStore();
    const download = vi.spyOn(b.store, "download");

    const outcome = await runPlatePipeline({ meals: m.store, blobs: b.store, imageOps: okImageOps }, plateObject);

    expect(outcome).toBe("no-meal");
    expect(download).not.toHaveBeenCalled();
    expect(b.uploads).toEqual([]);
    expect(m.written).toEqual([]);
  });

  it("is idempotent: already-processed meal does no work", async () => {
    const m = mealStore({ kind: "processed" });
    const b = blobStore();
    const download = vi.spyOn(b.store, "download");

    const outcome = await runPlatePipeline({ meals: m.store, blobs: b.store, imageOps: okImageOps }, plateObject);

    expect(outcome).toBe("already-processed");
    expect(download).not.toHaveBeenCalled();
    expect(m.written).toEqual([]);
  });

  it("loop guard: ignores a finalize carrying our thumbnail metadata marker", async () => {
    const m = mealStore({ kind: "unprocessed" });
    const b = blobStore();
    const readStatus = vi.spyOn(m.store, "readStatus");

    const outcome = await runPlatePipeline(
      { meals: m.store, blobs: b.store, imageOps: okImageOps },
      { ...plateObject, metadata: { [THUMBNAIL_METADATA_MARKER]: "true" } },
    );

    expect(outcome).toBe("ignored-thumbnail-marker");
    expect(readStatus).not.toHaveBeenCalled();
    expect(b.uploads).toEqual([]);
  });

  it("loop guard: ignores the generated *_thumb.jpg by name even without the marker", async () => {
    const m = mealStore({ kind: "unprocessed" });
    const b = blobStore();

    const outcome = await runPlatePipeline(
      { meals: m.store, blobs: b.store, imageOps: okImageOps },
      { ...plateObject, name: "crews/c1/meals/c1_alice_2026-06-14_lunch_thumb.jpg" },
    );

    expect(outcome).toBe("ignored-not-plate");
    expect(b.uploads).toEqual([]);
  });

  it("skips a multi-photo extra-photo object with ZERO Firestore reads (no garbage doc, multi-photo)", async () => {
    const m = mealStore({ kind: "unprocessed" });
    const b = blobStore();
    const readStatus = vi.spyOn(m.store, "readStatus");

    const outcome = await runPlatePipeline(
      { meals: m.store, blobs: b.store, imageOps: okImageOps },
      { ...plateObject, name: "crews/c1/meals/c1_alice_2026-06-14_lunch_p1.jpg" },
    );

    expect(outcome).toBe("ignored-not-plate");
    expect(readStatus).not.toHaveBeenCalled();
    expect(b.uploads).toEqual([]);
    expect(m.written).toEqual([]);
  });

  it("ignores non-plate objects (avatars, exports, junk)", async () => {
    const m = mealStore({ kind: "unprocessed" });
    const b = blobStore();

    const outcome = await runPlatePipeline(
      { meals: m.store, blobs: b.store, imageOps: okImageOps },
      { ...plateObject, name: "avatars/alice.jpg" },
    );

    expect(outcome).toBe("ignored-not-plate");
    expect(m.written).toEqual([]);
  });

  it("side-effect order: thumbnail upload → plate cache backfill → doc write LAST", async () => {
    // The doc write is the durable "processed" signal (readStatus checks thumbHash) — it must land
    // only after both Storage effects, so a crash mid-pipeline re-runs instead of stranding.
    const order: string[] = [];
    const meals: MealStore = {
      readStatus: async () => ({ kind: "unprocessed" }),
      writeDerivatives: async () => {
        order.push("writeDerivatives");
      },
    };
    const blobs: PlateBlobStore = {
      download: async () => {
        order.push("download");
        return Buffer.from("ORIGINAL");
      },
      uploadThumbnail: async () => {
        order.push("uploadThumbnail");
      },
      setCacheControl: async () => {
        order.push("setCacheControl");
      },
    };

    await runPlatePipeline({ meals, blobs, imageOps: okImageOps }, plateObject);
    expect(order).toEqual(["download", "uploadThumbnail", "setCacheControl", "writeDerivatives"]);
  });

  it("a thumbnail-upload failure propagates and the meal doc is NOT marked processed", async () => {
    const m = mealStore({ kind: "unprocessed" });
    const failing: PlateBlobStore = {
      download: async () => Buffer.from("ORIGINAL"),
      uploadThumbnail: async () => {
        throw new Error("gcs write failed");
      },
      setCacheControl: async () => undefined,
    };

    await expect(
      runPlatePipeline({ meals: m.store, blobs: failing, imageOps: okImageOps }, plateObject),
    ).rejects.toThrow("gcs write failed");
    // No doc write → the retry sees "unprocessed" and re-runs the whole pipeline.
    expect(m.written).toEqual([]);
  });

  it("a download failure propagates before any upload or doc write", async () => {
    const m = mealStore({ kind: "unprocessed" });
    const b = blobStore();
    b.store.download = async () => {
      throw new Error("object gone");
    };

    await expect(
      runPlatePipeline({ meals: m.store, blobs: b.store, imageOps: okImageOps }, plateObject),
    ).rejects.toThrow("object gone");
    expect(b.uploads).toEqual([]);
    expect(b.cacheControlSet).toEqual([]);
    expect(m.written).toEqual([]);
  });
});
