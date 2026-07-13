import { describe, expect, it, vi } from "vitest";
import {
  mealStoragePaths,
  reclaimMealObjects,
  type MealBlobStore,
} from "../src/triggers/onMealDeleted";

const CREW = "c1";
const MEAL = "c1_alice_2026-06-14_lunch";
const PLATE = `crews/${CREW}/meals/${MEAL}.jpg`;
const THUMB = `crews/${CREW}/meals/${MEAL}_thumb.jpg`;

/** A blob store that records delete attempts; `missing` paths resolve as no-ops (mirrors ignoreNotFound). */
function blobStore(missing: Set<string> = new Set()): {
  store: MealBlobStore;
  deletes: string[];
} {
  const deletes: string[] = [];
  return {
    deletes,
    store: {
      delete: async (path) => {
        deletes.push(path);
        // a missing object is a no-op, exactly like getStorage().file(path).delete({ ignoreNotFound: true })
        if (missing.has(path)) return;
      },
    },
  };
}

describe("mealStoragePaths — thumbnail path resolution", () => {
  // NOTE: mealStoragePaths only resolves the thumbnail path now (R9 dead-code cleanup — it used
  // to also compute a `platePath`, but that was unused at its only call site: reclaimMealObjects
  // gets the meal's full photo set, PRIMARY included, from `mealPhotoPaths` instead, which owns
  // the exact same "" -> deterministic-fallback semantics — see mealPhotoPaths.test.ts for that
  // regression's coverage on the path that's actually used to delete the blob).
  it("prefers the thumbnail path persisted on the deleted doc", () => {
    expect(
      mealStoragePaths(CREW, MEAL, {
        platePath: "custom/plate.jpg",
        thumbnailPath: "custom/thumb.jpg",
      }),
    ).toEqual({ thumbnailPath: "custom/thumb.jpg" });
  });

  it("falls back to the deterministic scheme when the doc has no thumbnailPath (older meals)", () => {
    expect(mealStoragePaths(CREW, MEAL, undefined)).toEqual({ thumbnailPath: THUMB });
    expect(mealStoragePaths(CREW, MEAL, {})).toEqual({ thumbnailPath: THUMB });
  });

  it("falls back to the deterministic scheme when only platePath is present (thumb absent)", () => {
    expect(mealStoragePaths(CREW, MEAL, { platePath: "stored/plate.jpg" })).toEqual({
      thumbnailPath: THUMB,
    });
  });

  it("uses the stored thumbnailPath regardless of platePath (thumb present, plate absent)", () => {
    expect(mealStoragePaths(CREW, MEAL, { thumbnailPath: "stored/thumb.jpg" })).toEqual({
      thumbnailPath: "stored/thumb.jpg",
    });
  });

  it("an EMPTY-STRING stored thumbnailPath falls back to the deterministic scheme (regression: '' orphaned the blob)", () => {
    // Fixed 2026-07-13: `?? fallback` only caught null/undefined, so a doc that persisted
    // thumbnailPath: "" targeted "" — a no-op delete that orphaned the real blob. Now `||` treats
    // "" as missing, matching `mealPhotoPaths`'s semantics.
    expect(mealStoragePaths(CREW, MEAL, { platePath: "", thumbnailPath: "" })).toEqual({
      thumbnailPath: THUMB,
    });
  });
});

describe("reclaimMealObjects — plate + thumbnail reclaim", () => {
  it("deletes BOTH the plate image and the generated thumbnail", async () => {
    const b = blobStore();

    await reclaimMealObjects(b.store, CREW, MEAL, undefined);

    expect(b.deletes).toEqual([PLATE, THUMB]);
  });

  it("uses thumbnailPath from the doc when present", async () => {
    const b = blobStore();

    await reclaimMealObjects(b.store, CREW, MEAL, {
      platePath: "stored/plate.jpg",
      thumbnailPath: "stored/thumb.jpg",
    });

    expect(b.deletes).toEqual(["stored/plate.jpg", "stored/thumb.jpg"]);
  });

  it("still succeeds when the thumbnail is missing (older meal / never thumbnailed)", async () => {
    const b = blobStore(new Set([THUMB]));

    // resolves without throwing; both deletes are still attempted
    await expect(reclaimMealObjects(b.store, CREW, MEAL, undefined)).resolves.toBeUndefined();
    expect(b.deletes).toEqual([PLATE, THUMB]);
  });

  it("a thumbnail-delete error does not abort: the plate is still reclaimed (and vice versa)", async () => {
    const deletes: string[] = [];
    const store: MealBlobStore = {
      delete: async (path) => {
        deletes.push(path);
        if (path === THUMB) throw new Error("transient");
      },
    };

    // best-effort: the thrown thumbnail error is logged + swallowed, overall reclaim succeeds
    await expect(reclaimMealObjects(store, CREW, MEAL, undefined)).resolves.toBeUndefined();
    expect(deletes).toEqual([PLATE, THUMB]);
  });

  it("is idempotent: deleting an absent meal's objects twice is a no-op both times", async () => {
    const b = blobStore(new Set([PLATE, THUMB]));

    await reclaimMealObjects(b.store, CREW, MEAL, undefined);
    await reclaimMealObjects(b.store, CREW, MEAL, undefined);

    expect(b.deletes).toEqual([PLATE, THUMB, PLATE, THUMB]);
  });

  it("a plate-delete error does not abort the thumbnail reclaim", async () => {
    const deletes: string[] = [];
    const store: MealBlobStore = {
      delete: async (path) => {
        deletes.push(path);
        if (path === PLATE) throw new Error("transient");
      },
    };

    await expect(reclaimMealObjects(store, CREW, MEAL, undefined)).resolves.toBeUndefined();
    expect(deletes).toEqual([PLATE, THUMB]);
  });

  it("deletes the FULL multi-photo set: primary + every extra photo + the (primary-only) thumbnail", async () => {
    const b = blobStore();
    const EXTRA1 = `crews/${CREW}/meals/${MEAL}_p1.jpg`;
    const EXTRA2 = `crews/${CREW}/meals/${MEAL}_p2.jpg`;
    const doc = {
      plates: [
        { path: PLATE, source: "camera" },
        { path: EXTRA1, source: "gallery" },
        { path: EXTRA2, source: "gallery" },
      ],
    };

    await reclaimMealObjects(b.store, CREW, MEAL, doc);

    expect(b.deletes).toEqual([PLATE, EXTRA1, EXTRA2, THUMB]);
  });

  it("tolerates malformed plates entries when reclaiming a multi-photo meal (defensive)", async () => {
    const b = blobStore();
    const EXTRA1 = `crews/${CREW}/meals/${MEAL}_p1.jpg`;
    const doc = {
      plates: [{ path: PLATE }, { path: 1 }, {}, null, { path: EXTRA1 }],
    };

    await reclaimMealObjects(b.store, CREW, MEAL, doc);

    expect(b.deletes).toEqual([PLATE, EXTRA1, THUMB]);
  });

  it("an extra-photo delete failure does not abort the rest of the multi-photo set or the thumbnail", async () => {
    const EXTRA1 = `crews/${CREW}/meals/${MEAL}_p1.jpg`;
    const EXTRA2 = `crews/${CREW}/meals/${MEAL}_p2.jpg`;
    const deletes: string[] = [];
    const store: MealBlobStore = {
      delete: async (path) => {
        deletes.push(path);
        if (path === EXTRA1) throw new Error("transient");
      },
    };
    const doc = { plates: [{ path: PLATE }, { path: EXTRA1 }, { path: EXTRA2 }] };

    await expect(reclaimMealObjects(store, CREW, MEAL, doc)).resolves.toBeUndefined();
    expect(deletes).toEqual([PLATE, EXTRA1, EXTRA2, THUMB]);
  });
});
