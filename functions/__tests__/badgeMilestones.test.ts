import { describe, expect, it } from "vitest";
import {
  badgeIdForCount,
  processBadgeMilestone,
  BADGE_TIERS,
  type BadgeDeps,
} from "../src/triggers/badgeMilestones";

// ── badgeIdForCount ────────────────────────────────────────────────────────────

describe("badgeIdForCount — tier assignment", () => {
  it("returns null below the first threshold", () => {
    expect(badgeIdForCount(0)).toBeNull();
  });

  it("awards 'first' at exactly 1 publish", () => {
    expect(badgeIdForCount(1)).toBe("first");
  });

  it("awards 'first' for counts 1–9", () => {
    for (let n = 1; n < 10; n++) expect(badgeIdForCount(n)).toBe("first");
  });

  it("awards 'ten' at exactly 10 publishes", () => {
    expect(badgeIdForCount(10)).toBe("ten");
  });

  it("awards 'ten' for counts 10–49", () => {
    expect(badgeIdForCount(10)).toBe("ten");
    expect(badgeIdForCount(49)).toBe("ten");
  });

  it("awards 'fifty' at exactly 50 publishes", () => {
    expect(badgeIdForCount(50)).toBe("fifty");
  });

  it("awards 'fifty' for counts 50–99", () => {
    expect(badgeIdForCount(50)).toBe("fifty");
    expect(badgeIdForCount(99)).toBe("fifty");
  });

  it("awards 'hundred' at exactly 100 publishes", () => {
    expect(badgeIdForCount(100)).toBe("hundred");
  });

  it("awards 'hundred' for counts above 100", () => {
    expect(badgeIdForCount(200)).toBe("hundred");
    expect(badgeIdForCount(9999)).toBe("hundred");
  });

  it("tiers are ordered highest-threshold-first", () => {
    // Invariant: BADGE_TIERS is descending by threshold so the first match wins.
    for (let i = 0; i < BADGE_TIERS.length - 1; i++) {
      expect(BADGE_TIERS[i].threshold).toBeGreaterThan(BADGE_TIERS[i + 1].threshold);
    }
  });
});

// ── processBadgeMilestone ─────────────────────────────────────────────────────

const CREW = "crew20charAutoIdXXXX";
const UID = "alice";
// mealId format: {crewId}_{uid}_{dayKey}_{slot}
const MEAL_ID = `${CREW}_${UID}_2026-06-20_lunch`;
const CANONICAL_KEY = `${UID}_2026-06-20_lunch`;

/** Minimal recording fake; each param has a sensible default. */
function fakeDeps(
  overrides: Partial<{
    mealCount: number;
    alreadyCounted: boolean;
    newCount: number;
  }> = {},
): {
  deps: BadgeDeps;
  marked: string[];
  badges: Array<{ uid: string; badgeId: string }>;
} {
  const marked: string[] = [];
  const badges: Array<{ uid: string; badgeId: string }> = [];
  let incrementCalls = 0;

  const mealCount = overrides.mealCount ?? 0;
  const alreadyCounted = overrides.alreadyCounted ?? false;
  // By default, the increment returns mealCount + 1.
  const newCount = overrides.newCount ?? mealCount + 1;

  const deps: BadgeDeps = {
    readMealCount: async () => mealCount,
    isAlreadyCounted: async () => alreadyCounted,
    markCounted: async (uid, key) => {
      marked.push(`${uid}/${key}`);
    },
    incrementMealCount: async () => {
      incrementCalls++;
      return newCount;
    },
    writeBadge: async (uid, badgeId) => {
      badges.push({ uid, badgeId });
    },
  };

  return { deps, marked, badges };
}

describe("processBadgeMilestone — dedup", () => {
  it("returns null and writes nothing when the canonical key was already counted", async () => {
    const { deps, marked, badges } = fakeDeps({ alreadyCounted: true });
    const result = await processBadgeMilestone(UID, CREW, MEAL_ID, deps);

    expect(result).toBeNull();
    expect(marked).toHaveLength(0);
    expect(badges).toHaveLength(0);
  });

  it("marks the canonical key after the first count", async () => {
    const { deps, marked } = fakeDeps({ mealCount: 0 });
    await processBadgeMilestone(UID, CREW, MEAL_ID, deps);

    expect(marked).toContain(`${UID}/${CANONICAL_KEY}`);
  });
});

describe("processBadgeMilestone — threshold crossing", () => {
  it("writes 'first' badge when crossing 1 (0 → 1)", async () => {
    const { deps, badges } = fakeDeps({ mealCount: 0, newCount: 1 });
    const result = await processBadgeMilestone(UID, CREW, MEAL_ID, deps);

    expect(result).toBe("first");
    expect(badges).toEqual([{ uid: UID, badgeId: "first" }]);
  });

  it("writes 'ten' badge when crossing 10 (9 → 10)", async () => {
    const { deps, badges } = fakeDeps({ mealCount: 9, newCount: 10 });
    const result = await processBadgeMilestone(UID, CREW, MEAL_ID, deps);

    expect(result).toBe("ten");
    expect(badges).toEqual([{ uid: UID, badgeId: "ten" }]);
  });

  it("writes 'fifty' badge when crossing 50 (49 → 50)", async () => {
    const { deps, badges } = fakeDeps({ mealCount: 49, newCount: 50 });
    const result = await processBadgeMilestone(UID, CREW, MEAL_ID, deps);

    expect(result).toBe("fifty");
    expect(badges).toEqual([{ uid: UID, badgeId: "fifty" }]);
  });

  it("writes 'hundred' badge when crossing 100 (99 → 100)", async () => {
    const { deps, badges } = fakeDeps({ mealCount: 99, newCount: 100 });
    const result = await processBadgeMilestone(UID, CREW, MEAL_ID, deps);

    expect(result).toBe("hundred");
    expect(badges).toEqual([{ uid: UID, badgeId: "hundred" }]);
  });

  it("does NOT write a badge when staying within the same tier (5 → 6)", async () => {
    const { deps, badges } = fakeDeps({ mealCount: 5, newCount: 6 });
    const result = await processBadgeMilestone(UID, CREW, MEAL_ID, deps);

    expect(result).toBeNull();
    expect(badges).toHaveLength(0);
  });

  it("does NOT write a badge when count is still at 0 → stays below threshold", async () => {
    // Edge case: increment returns 0 (shouldn't happen in practice, but defensive).
    const { deps, badges } = fakeDeps({ mealCount: 0, newCount: 0 });
    const result = await processBadgeMilestone(UID, CREW, MEAL_ID, deps);

    expect(result).toBeNull();
    expect(badges).toHaveLength(0);
  });
});

describe("processBadgeMilestone — canonical key extraction", () => {
  it("strips the crewId prefix to get the canonical key", async () => {
    const { deps, marked } = fakeDeps({ mealCount: 0, newCount: 1 });
    // mealId = crewId + "_" + uid + "_" + dayKey + "_" + slot
    await processBadgeMilestone(UID, CREW, MEAL_ID, deps);

    // The canonical key should NOT include the crewId prefix.
    expect(marked[0]).toBe(`${UID}/${CANONICAL_KEY}`);
  });

  it("deduplicates the same canonical publish from a different crew copy", async () => {
    const CREW2 = "anotherCrewId20charX";
    const MEAL_ID_CREW2 = `${CREW2}_${UID}_2026-06-20_lunch`;
    // CANONICAL_KEY for CREW2 would also be UID + "_2026-06-20_lunch"

    // Simulate: first copy was already counted.
    const { deps, badges } = fakeDeps({ alreadyCounted: true, mealCount: 0, newCount: 1 });
    const result = await processBadgeMilestone(UID, CREW2, MEAL_ID_CREW2, deps);

    expect(result).toBeNull();
    expect(badges).toHaveLength(0);
  });
});

describe("processBadgeMilestone — idempotency", () => {
  it("is idempotent: the same canonical key counted twice awards the badge only once", async () => {
    let callCount = 0;
    const badges: Array<{ uid: string; badgeId: string }> = [];
    const marked: string[] = [];

    const deps: BadgeDeps = {
      readMealCount: async () => 0,
      // First call: not counted. Second call: already counted.
      isAlreadyCounted: async () => callCount > 0,
      markCounted: async (uid, key) => {
        callCount++;
        marked.push(`${uid}/${key}`);
      },
      incrementMealCount: async () => 1,
      writeBadge: async (uid, badgeId) => { badges.push({ uid, badgeId }); },
    };

    await processBadgeMilestone(UID, CREW, MEAL_ID, deps);
    await processBadgeMilestone(UID, CREW, MEAL_ID, deps);

    // Badge written exactly once.
    expect(badges).toHaveLength(1);
    expect(badges[0].badgeId).toBe("first");
  });
});
