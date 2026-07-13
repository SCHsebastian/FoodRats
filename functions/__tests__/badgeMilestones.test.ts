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

  const mealCount = overrides.mealCount ?? 0;
  const alreadyCounted = overrides.alreadyCounted ?? false;
  // By default, counting yields mealCount + 1.
  const newCount = overrides.newCount ?? mealCount + 1;

  const deps: BadgeDeps = {
    countCanonicalPublish: async (uid, key) => {
      if (alreadyCounted) return null;
      marked.push(`${uid}/${key}`);
      return { prevCount: mealCount, newCount };
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

describe("processBadgeMilestone — edge cases", () => {
  it("falls back to the FULL mealId as canonical key when the crewId prefix is absent", async () => {
    const { deps, marked } = fakeDeps({ mealCount: 0, newCount: 1 });
    const malformedId = "no-prefix-meal-id";
    const result = await processBadgeMilestone(UID, CREW, malformedId, deps);

    expect(result).toBe("first");
    expect(marked).toEqual([`${UID}/${malformedId}`]);
  });

  it("does NOT strip a crewId that merely prefixes without the '_' separator", async () => {
    const { deps, marked } = fakeDeps({ mealCount: 0, newCount: 1 });
    // Starts with CREW but not CREW + "_" → treated as a full canonical key.
    const oddId = `${CREW}x_rest`;
    await processBadgeMilestone(UID, CREW, oddId, deps);
    expect(marked).toEqual([`${UID}/${oddId}`]);
  });

  it("a multi-tier jump (0 → 10, e.g. after a counter repair) awards the HIGHEST earned tier", async () => {
    const { deps, badges } = fakeDeps({ mealCount: 0, newCount: 10 });
    const result = await processBadgeMilestone(UID, CREW, MEAL_ID, deps);

    expect(result).toBe("ten");
    expect(badges).toEqual([{ uid: UID, badgeId: "ten" }]);
  });

  it("does NOT rewrite the badge when the count moves within the 'ten' tier (10 → 11)", async () => {
    const { deps, badges } = fakeDeps({ mealCount: 10, newCount: 11 });
    expect(await processBadgeMilestone(UID, CREW, MEAL_ID, deps)).toBeNull();
    expect(badges).toHaveLength(0);
  });

  it("does NOT write when the increment anomalously returns the same count (no tier change)", async () => {
    const { deps, badges } = fakeDeps({ mealCount: 5, newCount: 5 });
    expect(await processBadgeMilestone(UID, CREW, MEAL_ID, deps)).toBeNull();
    expect(badges).toHaveLength(0);
  });

  it("dedup marker and count increment are ONE atomic operation — no marker-only state can exist", async () => {
    // Regression (fixed 2026-07-13): marker and increment used to be two separate writes,
    // marker first — a crash in between left the key marked but never counted, and the
    // retry short-circuited on the marker, losing that publish's count forever. The
    // contract is now a single transactional dep; the pipeline makes exactly one call
    // to it, so no partial marker/count state is reachable by construction.
    const calls: string[] = [];
    const deps: BadgeDeps = {
      countCanonicalPublish: async () => {
        calls.push("countCanonicalPublish");
        return { prevCount: 0, newCount: 1 };
      },
      writeBadge: async () => {
        calls.push("writeBadge");
      },
    };
    await processBadgeMilestone(UID, CREW, MEAL_ID, deps);
    expect(calls).toEqual(["countCanonicalPublish", "writeBadge"]);
  });

  it("a crash inside the atomic count propagates — the retry re-runs it with nothing partially committed", async () => {
    const deps: BadgeDeps = {
      countCanonicalPublish: async () => {
        throw new Error("tx aborted");
      },
      writeBadge: async () => undefined,
    };
    await expect(processBadgeMilestone(UID, CREW, MEAL_ID, deps)).rejects.toThrow("tx aborted");
  });

  it("propagates a writeBadge failure to the caller (onMealCreated swallows it there)", async () => {
    const { deps } = fakeDeps({ mealCount: 0, newCount: 1 });
    const failing: BadgeDeps = {
      ...deps,
      writeBadge: async () => {
        throw new Error("firestore down");
      },
    };
    await expect(processBadgeMilestone(UID, CREW, MEAL_ID, failing)).rejects.toThrow(
      "firestore down",
    );
  });
});

describe("processBadgeMilestone — idempotency", () => {
  it("is idempotent: the same canonical key counted twice awards the badge only once", async () => {
    const badges: Array<{ uid: string; badgeId: string }> = [];
    const countedKeys = new Set<string>();

    const deps: BadgeDeps = {
      // Stateful fake mirroring the real transaction: first call counts, replay returns null.
      countCanonicalPublish: async (uid, key) => {
        const k = `${uid}/${key}`;
        if (countedKeys.has(k)) return null;
        countedKeys.add(k);
        return { prevCount: 0, newCount: 1 };
      },
      writeBadge: async (uid, badgeId) => { badges.push({ uid, badgeId }); },
    };

    await processBadgeMilestone(UID, CREW, MEAL_ID, deps);
    await processBadgeMilestone(UID, CREW, MEAL_ID, deps);

    // Badge written exactly once.
    expect(badges).toHaveLength(1);
    expect(badges[0].badgeId).toBe("first");
  });
});
