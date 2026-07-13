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

  it("ignores a null entry inside the ratings map without crashing", () => {
    expect(ratingAggregatesFrom({ a: null, b: { score: 3 } })).toEqual({
      ratingSum: 3,
      voterCount: 1,
    });
  });

  it("accepts the score boundaries 1 and 5, rejects 0 and 6", () => {
    expect(ratingAggregatesFrom({ a: { score: 1 }, b: { score: 5 } })).toEqual({
      ratingSum: 6,
      voterCount: 2,
    });
    expect(ratingAggregatesFrom({ a: { score: 0 }, b: { score: 6 } })).toEqual({
      ratingSum: 0,
      voterCount: 0,
    });
  });
});

// ---------------------------------------------------------------------------
// computeAwards — the digest award engine
// ---------------------------------------------------------------------------

function meal(over: Partial<import("../src/stats/computeWindow").MealInput> = {}) {
  return {
    authorId: "a1",
    authorName: "Ana",
    dishName: "paella",
    ratingSum: 0,
    voterCount: 0,
    publishedAtEpochMs: 100,
    ...over,
  };
}

describe("computeAwards — empty / unrated windows", () => {
  it("returns all-null awards for an empty week", () => {
    expect(computeAwards([])).toEqual({
      bestMeal: null,
      bestCook: null,
      mostProlific: null,
      mostVoted: null,
      mostCriticized: null,
    });
  });

  it("with only unrated meals: no bestMeal/mostVoted/bestCook, but mostProlific still awarded", () => {
    const awards = computeAwards([
      meal({ authorId: "a1", authorName: "Ana" }),
      meal({ authorId: "a1", authorName: "Ana" }),
      meal({ authorId: "b1", authorName: "Beto" }),
    ]);
    expect(awards.bestMeal).toBeNull();
    expect(awards.mostVoted).toBeNull();
    expect(awards.bestCook).toBeNull();
    expect(awards.mostCriticized).toBeNull();
    expect(awards.mostProlific).toEqual({ authorName: "Ana", postCount: 2 });
  });
});

describe("computeAwards — bestMeal tie-breaks", () => {
  it("higher average wins", () => {
    const awards = computeAwards([
      meal({ dishName: "good", ratingSum: 8, voterCount: 2 }), // 4.0
      meal({ dishName: "great", ratingSum: 10, voterCount: 2 }), // 5.0
    ]);
    expect(awards.bestMeal).toEqual({ dishName: "great", avgScore: 5 });
  });

  it("equal average → more voters wins", () => {
    const awards = computeAwards([
      meal({ dishName: "few", ratingSum: 4, voterCount: 1 }), // 4.0 x1
      meal({ dishName: "many", ratingSum: 12, voterCount: 3 }), // 4.0 x3
    ]);
    expect(awards.bestMeal?.dishName).toBe("many");
  });

  it("equal average AND voters → earliest published wins", () => {
    const awards = computeAwards([
      meal({ dishName: "later", ratingSum: 4, voterCount: 1, publishedAtEpochMs: 200 }),
      meal({ dishName: "earlier", ratingSum: 4, voterCount: 1, publishedAtEpochMs: 50 }),
    ]);
    expect(awards.bestMeal?.dishName).toBe("earlier");
  });
});

describe("computeAwards — mostVoted tie-breaks", () => {
  it("more voters wins regardless of average", () => {
    const awards = computeAwards([
      meal({ dishName: "loved", ratingSum: 5, voterCount: 1 }), // 5.0 x1
      meal({ dishName: "debated", ratingSum: 6, voterCount: 3 }), // 2.0 x3
    ]);
    expect(awards.mostVoted).toEqual({ dishName: "debated", voterCount: 3 });
  });

  it("equal voters → higher average wins", () => {
    const awards = computeAwards([
      meal({ dishName: "meh", ratingSum: 4, voterCount: 2 }), // 2.0
      meal({ dishName: "yum", ratingSum: 9, voterCount: 2 }), // 4.5
    ]);
    expect(awards.mostVoted?.dishName).toBe("yum");
  });
});

describe("computeAwards — mostProlific tie-breaks", () => {
  it("more posts wins; a tie breaks alphabetically by authorName", () => {
    const tied = computeAwards([
      meal({ authorId: "z", authorName: "Zoe" }),
      meal({ authorId: "a", authorName: "Amy" }),
    ]);
    expect(tied.mostProlific).toEqual({ authorName: "Amy", postCount: 1 });

    const counted = computeAwards([
      meal({ authorId: "z", authorName: "Zoe" }),
      meal({ authorId: "z", authorName: "Zoe" }),
      meal({ authorId: "a", authorName: "Amy" }),
    ]);
    expect(counted.mostProlific).toEqual({ authorName: "Zoe", postCount: 2 });
  });
});

describe("computeAwards — bestCook gate (MIN_PLATES_FOR_COOK = 3 rated plates)", () => {
  const ratedMeal = (authorId: string, authorName: string, score: number, n: number) =>
    meal({ authorId, authorName, ratingSum: score * n, voterCount: n });

  it("a cook with only 2 RATED plates does not qualify (unrated plates don't count)", () => {
    const awards = computeAwards([
      ratedMeal("a1", "Ana", 5, 1),
      ratedMeal("a1", "Ana", 5, 1),
      meal({ authorId: "a1", authorName: "Ana" }), // unrated — postCount yes, rated gate no
      meal({ authorId: "a1", authorName: "Ana" }),
    ]);
    expect(awards.bestCook).toBeNull();
  });

  it("exactly 3 rated plates qualifies, with the vote-weighted average", () => {
    const awards = computeAwards([
      meal({ authorId: "a1", authorName: "Ana", ratingSum: 5, voterCount: 1 }), // 5
      meal({ authorId: "a1", authorName: "Ana", ratingSum: 4, voterCount: 1 }), // 4
      meal({ authorId: "a1", authorName: "Ana", ratingSum: 6, voterCount: 2 }), // 3,3
      meal({ authorId: "a1", authorName: "Ana" }), // unrated — counts toward postCount only
    ]);
    // Weighted: (5+4+6)/(1+1+2) = 15/4 = 3.75, over ALL votes, not per-meal averages.
    expect(awards.bestCook).toEqual({ authorName: "Ana", avgScore: 3.75, postCount: 4 });
  });

  it("equal avg between qualifying cooks → more posts wins, then name ascending", () => {
    const cook = (id: string, name: string, extraPosts: number) => [
      meal({ authorId: id, authorName: name, ratingSum: 4, voterCount: 1 }),
      meal({ authorId: id, authorName: name, ratingSum: 4, voterCount: 1 }),
      meal({ authorId: id, authorName: name, ratingSum: 4, voterCount: 1 }),
      ...Array.from({ length: extraPosts }, () => meal({ authorId: id, authorName: name })),
    ];
    const awards = computeAwards([...cook("z", "Zoe", 1), ...cook("a", "Amy", 0)]);
    expect(awards.bestCook?.authorName).toBe("Zoe"); // 4 posts beats 3 at equal avg
  });
});

describe("computeAwards — mostCriticized guard rails", () => {
  const threeRated = (id: string, name: string, score: number) =>
    Array.from({ length: 3 }, () =>
      meal({ authorId: id, authorName: name, ratingSum: score, voterCount: 1 }),
    );

  it("awards the worst qualifying cook when two cooks qualify with different averages", () => {
    const awards = computeAwards([...threeRated("a", "Ana", 5), ...threeRated("b", "Beto", 2)]);
    expect(awards.bestCook?.authorName).toBe("Ana");
    expect(awards.mostCriticized).toEqual({ authorName: "Beto", avgScore: 2, postCount: 3 });
  });

  it("is null when only ONE cook qualifies (never criticize the only cook)", () => {
    const awards = computeAwards([...threeRated("a", "Ana", 2)]);
    expect(awards.bestCook?.authorName).toBe("Ana");
    expect(awards.mostCriticized).toBeNull();
  });

  it("is null when best and worst collapse to the same cook (identical averages + tie-breaks)", () => {
    const awards = computeAwards([...threeRated("a", "Amy", 4), ...threeRated("z", "Zoe", 4)]);
    // Both sorts tie-break to Amy (equal avg, equal posts, name asc) → worst === best → null.
    expect(awards.bestCook?.authorName).toBe("Amy");
    expect(awards.mostCriticized).toBeNull();
  });
});
