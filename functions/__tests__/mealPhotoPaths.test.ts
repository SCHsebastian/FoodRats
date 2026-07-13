import { describe, expect, it } from "vitest";
import { mealPhotoPaths } from "../src/meal/mealPhotoPaths";

const CREW = "c1";
const MEAL = "c1_alice_2026-06-14_lunch";
const DOC_PATH = `crews/${CREW}/meals/${MEAL}`;
const PRIMARY = `crews/${CREW}/meals/${MEAL}.jpg`;
const EXTRA1 = `crews/${CREW}/meals/${MEAL}_p1.jpg`;
const EXTRA2 = `crews/${CREW}/meals/${MEAL}_p2.jpg`;

describe("mealPhotoPaths — full photo set from a meal doc (multi-photo)", () => {
  it("resolves to just the deterministic primary for a legacy doc (no platePath, no plates)", () => {
    expect(mealPhotoPaths(DOC_PATH, undefined)).toEqual([PRIMARY]);
    expect(mealPhotoPaths(DOC_PATH, {})).toEqual([PRIMARY]);
  });

  it("uses the persisted platePath instead of the deterministic fallback when present", () => {
    expect(mealPhotoPaths(DOC_PATH, { platePath: "stored/plate.jpg" })).toEqual([
      "stored/plate.jpg",
    ]);
  });

  it("falls back to the deterministic primary when platePath is an empty string (never target '')", () => {
    expect(mealPhotoPaths(DOC_PATH, { platePath: "" })).toEqual([PRIMARY]);
  });

  it("falls back to the deterministic primary when platePath is a non-string (defensive)", () => {
    expect(mealPhotoPaths(DOC_PATH, { platePath: 42 })).toEqual([PRIMARY]);
  });

  it("unions platePath with every well-formed plates[].path, de-duplicated, in order", () => {
    const doc = {
      platePath: PRIMARY,
      plates: [
        { path: PRIMARY, source: "camera" },
        { path: EXTRA1, source: "gallery" },
        { path: EXTRA2, source: "gallery" },
      ],
    };
    expect(mealPhotoPaths(DOC_PATH, doc)).toEqual([PRIMARY, EXTRA1, EXTRA2]);
  });

  it("still includes extra photos when platePath itself is absent (fallback + plates union)", () => {
    const doc = { plates: [{ path: PRIMARY }, { path: EXTRA1 }] };
    expect(mealPhotoPaths(DOC_PATH, doc)).toEqual([PRIMARY, EXTRA1]);
  });

  it("skips malformed plates entries (missing path, non-string path, non-object entry) defensively", () => {
    const doc = {
      platePath: PRIMARY,
      plates: [{ path: PRIMARY }, { path: 42 }, {}, null, "not-an-object", { path: EXTRA1 }],
    };
    expect(mealPhotoPaths(DOC_PATH, doc)).toEqual([PRIMARY, EXTRA1]);
  });

  it("tolerates a non-array plates field", () => {
    expect(mealPhotoPaths(DOC_PATH, { platePath: PRIMARY, plates: "oops" })).toEqual([PRIMARY]);
  });

  it("de-duplicates a plates[] entry that repeats the primary path", () => {
    const doc = { platePath: PRIMARY, plates: [{ path: PRIMARY }, { path: PRIMARY }] };
    expect(mealPhotoPaths(DOC_PATH, doc)).toEqual([PRIMARY]);
  });

  it("de-duplicates a repeated extra-photo path within plates[] itself", () => {
    const doc = { platePath: PRIMARY, plates: [{ path: EXTRA1 }, { path: EXTRA1 }] };
    expect(mealPhotoPaths(DOC_PATH, doc)).toEqual([PRIMARY, EXTRA1]);
  });

  it("returns an empty array for a mealDocPath that isn't a well-formed crews/{c}/meals/{m} path", () => {
    expect(mealPhotoPaths("not/a/meal/path", { platePath: "x.jpg" })).toEqual([]);
    expect(mealPhotoPaths("crews/c1/meals/m1/extra", { platePath: "x.jpg" })).toEqual([]);
    expect(mealPhotoPaths("", { platePath: "x.jpg" })).toEqual([]);
  });
});
