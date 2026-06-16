import { describe, expect, it, vi } from "vitest";
import {
  classifyPlateObject,
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

function blobStore(): { store: PlateBlobStore; uploads: { path: string }[] } {
  const uploads: { path: string }[] = [];
  return {
    uploads,
    store: {
      download: async () => Buffer.from("ORIGINAL"),
      uploadThumbnail: async (path) => {
        uploads.push({ path });
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
    expect(b.uploads).toEqual([{ path: "crews/c1/meals/c1_alice_2026-06-14_lunch_thumb.jpg" }]);
    expect(m.written).toHaveLength(1);
    const w = m.written[0] as { crewId: string; mealId: string; fields: { thumbHash: string; thumbnailPath: string } };
    expect(w.crewId).toBe("c1");
    expect(w.fields.thumbnailPath).toBe("crews/c1/meals/c1_alice_2026-06-14_lunch_thumb.jpg");
    expect(w.fields.thumbHash.length).toBeGreaterThan(0);
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
});
