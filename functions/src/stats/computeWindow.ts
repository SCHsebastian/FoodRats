export interface MealInput {
  authorId: string;
  authorName: string;
  dishName: string;
  ratingSum: number;
  voterCount: number;
  publishedAtEpochMs: number;
}

export interface Awards {
  bestMeal: { dishName: string; avgScore: number } | null;
  bestCook: { authorName: string; avgScore: number; postCount: number } | null;
  mostProlific: { authorName: string; postCount: number } | null;
  mostVoted: { dishName: string; voterCount: number } | null;
  mostCriticized: { authorName: string; avgScore: number; postCount: number } | null;
}

const MIN_PLATES_FOR_COOK = 3;

/**
 * Derive `{ ratingSum, voterCount }` from the AUTHORITATIVE per-rater `ratings`
 * map rather than trusting the denormalized `ratingSum`/`voterCount` fields on the
 * meal doc. Security rules can bound those fields on create (must be zero) but
 * CANNOT verify them on a vote `update` — rules can't sum a map — so a voter could
 * otherwise stamp `ratingSum: 9999` and win the server-attested weekly awards.
 * Recomputing here makes the denormalized fields irrelevant to award outcomes.
 * Out-of-range / malformed entries are ignored (rules already cap scores 1..5).
 */
export function ratingAggregatesFrom(ratings: unknown): {
  ratingSum: number;
  voterCount: number;
} {
  if (!ratings || typeof ratings !== "object") return { ratingSum: 0, voterCount: 0 };
  let ratingSum = 0;
  let voterCount = 0;
  for (const entry of Object.values(ratings as Record<string, { score?: unknown }>)) {
    const score = entry?.score;
    if (typeof score === "number" && Number.isInteger(score) && score >= 1 && score <= 5) {
      ratingSum += score;
      voterCount += 1;
    }
  }
  return { ratingSum, voterCount };
}

export function computeAwards(meals: MealInput[]): Awards {
  if (meals.length === 0) {
    return {
      bestMeal: null,
      bestCook: null,
      mostProlific: null,
      mostVoted: null,
      mostCriticized: null,
    };
  }
  const rated = meals.filter((m) => m.voterCount > 0);

  const bestMeal = rated
    .slice()
    .sort((a, b) => {
      const aa = a.ratingSum / a.voterCount;
      const ba = b.ratingSum / b.voterCount;
      if (ba !== aa) return ba - aa;
      if (b.voterCount !== a.voterCount) return b.voterCount - a.voterCount;
      return a.publishedAtEpochMs - b.publishedAtEpochMs;
    })[0];

  const mostVoted = rated
    .slice()
    .sort((a, b) => {
      if (b.voterCount !== a.voterCount) return b.voterCount - a.voterCount;
      return b.ratingSum / b.voterCount - a.ratingSum / a.voterCount;
    })[0];

  const byAuthor = new Map<string, MealInput[]>();
  for (const m of meals) {
    const arr = byAuthor.get(m.authorId) ?? [];
    arr.push(m);
    byAuthor.set(m.authorId, arr);
  }

  const prolificEntries = Array.from(byAuthor.entries()).map(([authorId, ms]) => ({
    authorId,
    authorName: ms[0].authorName,
    postCount: ms.length,
  }));
  prolificEntries.sort((a, b) => {
    if (b.postCount !== a.postCount) return b.postCount - a.postCount;
    return a.authorName.localeCompare(b.authorName);
  });
  const mostProlific = prolificEntries[0] ?? null;

  const cookEntries = Array.from(byAuthor.entries())
    .map(([authorId, ms]) => {
      const r = ms.filter((m) => m.voterCount > 0);
      const totalSum = r.reduce((s, m) => s + m.ratingSum, 0);
      const totalCount = r.reduce((s, m) => s + m.voterCount, 0);
      return {
        authorId,
        authorName: ms[0].authorName,
        postCount: ms.length,
        ratedCount: r.length,
        avgScore: totalCount > 0 ? totalSum / totalCount : 0,
      };
    })
    .filter((c) => c.ratedCount >= MIN_PLATES_FOR_COOK);

  const bestCook = cookEntries
    .slice()
    .sort((a, b) => {
      if (b.avgScore !== a.avgScore) return b.avgScore - a.avgScore;
      if (b.postCount !== a.postCount) return b.postCount - a.postCount;
      return a.authorName.localeCompare(b.authorName);
    })[0];

  const worstCook = cookEntries
    .slice()
    .sort((a, b) => {
      if (a.avgScore !== b.avgScore) return a.avgScore - b.avgScore;
      if (b.postCount !== a.postCount) return b.postCount - a.postCount;
      return a.authorName.localeCompare(b.authorName);
    })[0];

  const mostCriticized =
    worstCook &&
    bestCook &&
    worstCook.authorId !== bestCook.authorId &&
    cookEntries.length >= 2
      ? worstCook
      : null;

  return {
    bestMeal: bestMeal
      ? { dishName: bestMeal.dishName, avgScore: bestMeal.ratingSum / bestMeal.voterCount }
      : null,
    bestCook: bestCook
      ? {
          authorName: bestCook.authorName,
          avgScore: bestCook.avgScore,
          postCount: bestCook.postCount,
        }
      : null,
    mostProlific: mostProlific
      ? { authorName: mostProlific.authorName, postCount: mostProlific.postCount }
      : null,
    mostVoted: mostVoted
      ? { dishName: mostVoted.dishName, voterCount: mostVoted.voterCount }
      : null,
    mostCriticized: mostCriticized
      ? {
          authorName: mostCriticized.authorName,
          avgScore: mostCriticized.avgScore,
          postCount: mostCriticized.postCount,
        }
      : null,
  };
}
