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
    // Namespaced path (multi-photo-crew15 test-hardening fix): platePath is only honored when it
    // lives under the meal's own crews/{crewId}/meals/ prefix — see the "namespace-constrained"
    // describe block below for the out-of-namespace-is-dropped coverage.
    expect(mealPhotoPaths(DOC_PATH, { platePath: "crews/c1/meals/stored-plate.jpg" })).toEqual([
      "crews/c1/meals/stored-plate.jpg",
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

describe("mealPhotoPaths — additional multi-photo edge cases (test-hardening pass)", () => {
  it("tolerates a plates field that is an OBJECT (not an array) — same as absent", () => {
    expect(mealPhotoPaths(DOC_PATH, { platePath: PRIMARY, plates: { path: EXTRA1 } })).toEqual([
      PRIMARY,
    ]);
  });

  it("tolerates a plates field that is a NUMBER — same as absent", () => {
    expect(mealPhotoPaths(DOC_PATH, { platePath: PRIMARY, plates: 42 })).toEqual([PRIMARY]);
  });

  it("skips raw string/number/null/boolean entries inside plates[] (not just malformed objects)", () => {
    const doc = {
      platePath: PRIMARY,
      plates: ["raw-string", 42, null, true, { path: EXTRA1 }],
    };
    expect(mealPhotoPaths(DOC_PATH, doc)).toEqual([PRIMARY, EXTRA1]);
  });

  it("keeps BOTH when plates[0].path differs from the legacy platePath (never assumes one supersedes the other)", () => {
    // A doc where the legacy field and the ordered list disagree on the primary — e.g. a stale
    // platePath that predates a later plates[] write. The union keeps both rather than silently
    // dropping either.
    const legacyOnly = "crews/c1/meals/legacy-primary.jpg";
    const doc = { platePath: legacyOnly, plates: [{ path: PRIMARY }, { path: EXTRA1 }] };
    expect(mealPhotoPaths(DOC_PATH, doc)).toEqual([legacyOnly, PRIMARY, EXTRA1]);
  });

  it("resolves all 10 photos at the MAX_PHOTOS_PER_MEAL cap, in order, without truncating", () => {
    // 1 primary + 9 extras = MealPublishPolicy.MAX_PHOTOS_PER_MEAL (10). mealPhotoPaths itself
    // has no cap logic — the cap is enforced upstream (client + firestore.rules); this locks that
    // the union path doesn't silently drop entries at realistic scale.
    const extras = Array.from({ length: 9 }, (_, i) => `crews/${CREW}/meals/${MEAL}_p${i + 1}.jpg`);
    const paths = [PRIMARY, ...extras];
    const doc = { platePath: PRIMARY, plates: paths.map((path) => ({ path })) };
    expect(mealPhotoPaths(DOC_PATH, doc)).toEqual(paths);
  });

  it("drops a traversal-ish entry path — it doesn't start with the meal's own crews/{c}/meals/ prefix", () => {
    // GCS object names are flat strings (no real directory hierarchy), so "../foo.jpg" was never a
    // directory-escape risk — but it also isn't a well-formed object under this meal's own
    // namespace, so the namespace guard (added alongside the foreign-crew-path tests below) drops
    // it just like any other out-of-namespace string.
    const doc = { platePath: PRIMARY, plates: [{ path: "../foo.jpg" }] };
    expect(mealPhotoPaths(DOC_PATH, doc)).toEqual([PRIMARY]);
  });

  it("an empty-string legacy platePath still falls back to the deterministic primary WITH plates[] unioned in", () => {
    const doc = { platePath: "", plates: [{ path: EXTRA1 }] };
    expect(mealPhotoPaths(DOC_PATH, doc)).toEqual([PRIMARY, EXTRA1]);
  });
});

describe("mealPhotoPaths — namespace-constrained to the meal's own crews/{crewId}/meals/ prefix (security)", () => {
  // firestore.rules caps plates[] at size<=10 but does NOT constrain plates[].path's string content
  // ("same trust level as the single-photo platePath/plateSource fields above" per the rule's own
  // comment), and the server-side callers (onMealDeleted / deleteAccount delete; exportMyData signs)
  // all run via the Admin SDK, which bypasses storage.rules entirely. Without this guard, a meal's
  // author could stash another crew's (or an account's) real object path into their OWN meal's
  // plates[] and have it deleted or signed just by acting on their own meal. mealDocPath is the
  // TRUSTED source every caller derives the crewId from (a Firestore document's own path, never
  // doc-provided data) — see each caller's own call site for which trusted source it uses.

  it("drops a plates[].path entry that names a DIFFERENT crew's meal object", () => {
    const foreign = "crews/OTHER-CREW/meals/some-meal.jpg";
    const doc = { platePath: PRIMARY, plates: [{ path: PRIMARY }, { path: foreign }, { path: EXTRA1 }] };
    expect(mealPhotoPaths(DOC_PATH, doc)).toEqual([PRIMARY, EXTRA1]);
  });

  it("drops a plates[].path entry that names an unrelated object (e.g. a guessable avatar path)", () => {
    const foreignAvatar = "avatars/some-other-uid/token.jpg";
    const doc = { platePath: PRIMARY, plates: [{ path: PRIMARY }, { path: foreignAvatar }] };
    expect(mealPhotoPaths(DOC_PATH, doc)).toEqual([PRIMARY]);
  });

  it("a foreign-crew platePath (not just plates[]) falls back to the deterministic primary instead of being honored", () => {
    const foreign = "crews/OTHER-CREW/meals/some-meal.jpg";
    expect(mealPhotoPaths(DOC_PATH, { platePath: foreign })).toEqual([PRIMARY]);
  });

  it("a prefix-confusable path (crews/{crewId}MORE/meals/...) is rejected — the own-prefix check requires the trailing slash", () => {
    // Guards against a naive `startsWith(mealsPrefix)` (no trailing slash) being fooled by a
    // sibling crew id that happens to share this crew id as a string prefix.
    const confusable = `crews/${CREW}-evil/meals/x.jpg`;
    const doc = { platePath: PRIMARY, plates: [{ path: confusable }] };
    expect(mealPhotoPaths(DOC_PATH, doc)).toEqual([PRIMARY]);
  });

  it("keeps every LEGIT entry (legacy primary + all _p{n} extras) when a foreign path rides along in the same plates[]", () => {
    const foreign = "crews/OTHER-CREW/meals/steal-me.jpg";
    const doc = {
      platePath: PRIMARY,
      plates: [{ path: PRIMARY }, { path: EXTRA1 }, { path: foreign }, { path: EXTRA2 }],
    };
    expect(mealPhotoPaths(DOC_PATH, doc)).toEqual([PRIMARY, EXTRA1, EXTRA2]);
  });

  it("legit paths at MAX_PHOTOS_PER_MEAL scale (10) are entirely unaffected by the namespace guard", () => {
    const extras = Array.from({ length: 9 }, (_, i) => `crews/${CREW}/meals/${MEAL}_p${i + 1}.jpg`);
    const paths = [PRIMARY, ...extras];
    const doc = { platePath: PRIMARY, plates: paths.map((path) => ({ path })) };
    expect(mealPhotoPaths(DOC_PATH, doc)).toEqual(paths);
  });
});
