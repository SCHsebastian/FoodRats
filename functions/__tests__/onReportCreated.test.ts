import { describe, expect, it } from "vitest";
import {
  processReport,
  THRESHOLD,
  type ReportDeps,
  type ReportDoc,
} from "../src/triggers/onReportCreated";

const CREW = "c1";
const MEAL = "c1_alice_2026-06-14_lunch";
const COMMENT = "cmt1";
const MEAL_KEY = `meal|${CREW}|${MEAL}`;
const COMMENT_KEY = `comment|${CREW}|${MEAL}|${COMMENT}`;
const ACCOUNT_KEY = "account|bob";

interface Recorder {
  deps: ReportDeps;
  removedMeals: Array<{ crewId: string; mealId: string }>;
  removedComments: Array<{ crewId: string; mealId: string; commentId: string }>;
  actioned: string[];
}

/**
 * A deps seam whose distinct-reporter count is fixed to `reporters` (a set we control), recording
 * every removal/markActioned call. Mirrors the `MealBlobStore`/`NudgeDeps` test-double style.
 */
function recorder(reporters: Set<string>): Recorder {
  const removedMeals: Recorder["removedMeals"] = [];
  const removedComments: Recorder["removedComments"] = [];
  const actioned: string[] = [];
  return {
    removedMeals,
    removedComments,
    actioned,
    deps: {
      countDistinctReporters: async () => reporters,
      removeMeal: async (crewId, mealId) => {
        removedMeals.push({ crewId, mealId });
      },
      removeComment: async (crewId, mealId, commentId) => {
        removedComments.push({ crewId, mealId, commentId });
      },
      markActioned: async (targetKey) => {
        actioned.push(targetKey);
      },
    },
  };
}

const mealReport = (reporterId: string): ReportDoc => ({
  reporterId,
  targetType: "meal",
  targetKey: MEAL_KEY,
  crewId: CREW,
  mealId: MEAL,
});

const commentReport = (reporterId: string): ReportDoc => ({
  reporterId,
  targetType: "comment",
  targetKey: COMMENT_KEY,
  crewId: CREW,
  mealId: MEAL,
  commentId: COMMENT,
});

const accountReport = (reporterId: string): ReportDoc => ({
  reporterId,
  targetType: "account",
  targetKey: ACCOUNT_KEY,
  accountId: "bob",
});

describe("THRESHOLD", () => {
  it("is 3 distinct reporters", () => {
    expect(THRESHOLD).toBe(3);
  });
});

describe("processReport — below threshold", () => {
  it("does NOT remove a meal with only 2 distinct reporters", async () => {
    const r = recorder(new Set(["u1", "u2"]));

    const outcome = await processReport(mealReport("u2"), r.deps);

    expect(outcome.thresholdReached).toBe(false);
    expect(outcome.action).toBe("below_threshold");
    expect(outcome.distinctReporters).toBe(2);
    expect(r.removedMeals).toEqual([]);
    expect(r.removedComments).toEqual([]);
    expect(r.actioned).toEqual([]);
  });
});

describe("processReport — reaching threshold removes the target ONCE", () => {
  it("removes the meal when 3 DISTINCT reporters are reached (delete called exactly once)", async () => {
    const r = recorder(new Set(["u1", "u2", "u3"]));

    const outcome = await processReport(mealReport("u3"), r.deps);

    expect(outcome.thresholdReached).toBe(true);
    expect(outcome.action).toBe("removed_meal");
    expect(outcome.distinctReporters).toBe(3);
    expect(r.removedMeals).toEqual([{ crewId: CREW, mealId: MEAL }]);
    expect(r.removedMeals).toHaveLength(1);
    expect(r.removedComments).toEqual([]);
    expect(r.actioned).toEqual([MEAL_KEY]);
  });

  it("removes above threshold too (4 distinct reporters still removes the meal once)", async () => {
    const r = recorder(new Set(["u1", "u2", "u3", "u4"]));

    const outcome = await processReport(mealReport("u4"), r.deps);

    expect(outcome.distinctReporters).toBe(4);
    expect(outcome.action).toBe("removed_meal");
    expect(r.removedMeals).toHaveLength(1);
  });
});

describe("processReport — a duplicate reporter does NOT double-count", () => {
  it("3 report docs from only 2 distinct reporters stays below threshold (no removal)", async () => {
    // Deterministic doc ids guarantee one doc per reporter, but the count de-dupes defensively:
    // a Set of {u1, u2} is size 2 even if u1 somehow appears twice.
    const r = recorder(new Set(["u1", "u2"])); // distinct = 2, not 3

    const outcome = await processReport(mealReport("u2"), r.deps);

    expect(outcome.distinctReporters).toBe(2);
    expect(outcome.thresholdReached).toBe(false);
    expect(r.removedMeals).toEqual([]);
  });

  it("crosses threshold only when a THIRD distinct reporter appears", async () => {
    const below = recorder(new Set(["u1", "u1", "u2"])); // a Set collapses the dup → size 2
    expect((await processReport(mealReport("u2"), below.deps)).thresholdReached).toBe(false);
    expect(below.removedMeals).toEqual([]);

    const at = recorder(new Set(["u1", "u2", "u3"]));
    expect((await processReport(mealReport("u3"), at.deps)).thresholdReached).toBe(true);
    expect(at.removedMeals).toHaveLength(1);
  });
});

describe("processReport — comment path", () => {
  it("removes the COMMENT (not a meal) when the comment hits threshold", async () => {
    const r = recorder(new Set(["u1", "u2", "u3"]));

    const outcome = await processReport(commentReport("u3"), r.deps);

    expect(outcome.action).toBe("removed_comment");
    expect(r.removedComments).toEqual([{ crewId: CREW, mealId: MEAL, commentId: COMMENT }]);
    expect(r.removedComments).toHaveLength(1);
    expect(r.removedMeals).toEqual([]);
    expect(r.actioned).toEqual([COMMENT_KEY]);
  });

  it("does not remove a comment below threshold", async () => {
    const r = recorder(new Set(["u1", "u2"]));

    const outcome = await processReport(commentReport("u2"), r.deps);

    expect(outcome.action).toBe("below_threshold");
    expect(r.removedComments).toEqual([]);
  });
});

describe("processReport — account path is flag-only", () => {
  it("does NOT auto-remove an account at threshold — flags for manual review", async () => {
    const r = recorder(new Set(["u1", "u2", "u3"]));

    const outcome = await processReport(accountReport("u3"), r.deps);

    expect(outcome.thresholdReached).toBe(true);
    expect(outcome.action).toBe("flagged_account");
    expect(r.removedMeals).toEqual([]);
    expect(r.removedComments).toEqual([]);
    // The reports are still marked actioned (the manual-review queue reflects the takedown decision).
    expect(r.actioned).toEqual([ACCOUNT_KEY]);
  });
});

describe("processReport — idempotent re-fire", () => {
  it("re-removes (no-op delete) on a 4th-report re-fire without throwing", async () => {
    const r = recorder(new Set(["u1", "u2", "u3", "u4"]));

    // A re-fire recomputes, sees threshold, and re-issues the (idempotent) delete.
    await processReport(mealReport("u4"), r.deps);
    await processReport(mealReport("u4"), r.deps);

    expect(r.removedMeals).toEqual([
      { crewId: CREW, mealId: MEAL },
      { crewId: CREW, mealId: MEAL },
    ]);
  });
});
