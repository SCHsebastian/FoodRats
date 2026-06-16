import { describe, expect, it } from "vitest";
import { computeAwards, ratingAggregatesFrom } from "../src/stats/computeWindow";

describe("ratingAggregatesFrom — award integrity (P1)", () => {
  it("sums the per-rater ratings map, ignoring any denormalized fields", () => {
    expect(ratingAggregatesFrom({ a: { score: 5 }, b: { score: 3 } })).toEqual({
      ratingSum: 8,
      voterCount: 2,
    });
  });

  it("returns zero for empty / missing / non-object maps", () => {
    expect(ratingAggregatesFrom({})).toEqual({ ratingSum: 0, voterCount: 0 });
    expect(ratingAggregatesFrom(undefined)).toEqual({ ratingSum: 0, voterCount: 0 });
    expect(ratingAggregatesFrom(null)).toEqual({ ratingSum: 0, voterCount: 0 });
    expect(ratingAggregatesFrom("nope")).toEqual({ ratingSum: 0, voterCount: 0 });
  });

  it("ignores out-of-range, non-integer and malformed scores (defense in depth)", () => {
    expect(
      ratingAggregatesFrom({
        good: { score: 4 },
        tooHigh: { score: 9999 },
        zero: { score: 0 },
        float: { score: 3.5 },
        nan: { score: "5" },
        empty: {},
      }),
    ).toEqual({ ratingSum: 4, voterCount: 1 });
  });

  it("a self-stuffed ratings map cannot exceed real votes — a lone voter is worth at most 5", () => {
    // Even if an attacker stamps ratingSum:9999 on the doc, the award math runs on
    // this map: one entry, score clamped to <=5.
    const agg = ratingAggregatesFrom({ attacker: { score: 5 } });
    const awards = computeAwards([
      { authorId: "x", authorName: "X", dishName: "d", publishedAtEpochMs: 1, ...agg },
    ]);
    expect(awards.bestMeal?.avgScore).toBe(5);
    expect(awards.mostVoted?.voterCount).toBe(1);
  });
});
