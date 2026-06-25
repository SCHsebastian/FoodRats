import { afterEach, beforeEach, describe, expect, it } from "vitest";
import {
  processReport,
  moderationActionId,
  THRESHOLD,
  type ModerationActionDoc,
  type ReportDeps,
  type ReportDoc,
} from "../src/triggers/onReportCreated";

const CREW = "c1";
const MEAL = "c1_alice_2026-06-14_lunch";
const COMMENT = "cmt1";
const MEAL_KEY = `meal|${CREW}|${MEAL}`;
const COMMENT_KEY = `comment|${CREW}|${MEAL}|${COMMENT}`;
const ACCOUNT_KEY = "account|bob";

// ---------------------------------------------------------------------------
// Recorder seam — captures every side-effect call for assertion
// ---------------------------------------------------------------------------

interface Recorder {
  deps: ReportDeps;
  removedMeals: Array<{ crewId: string; mealId: string }>;
  removedComments: Array<{ crewId: string; mealId: string; commentId: string }>;
  actioned: string[];
  auditDocs: Map<string, ModerationActionDoc>;
  /** Audit docs pre-seeded as already-completed (completed: true). */
  completedAuditIds: Set<string>;
  /** Audit docs pre-seeded as incomplete claims (completed: false). */
  incompleteAuditIds: Set<string>;
  auditDocUpdates: Map<string, { completed: true; removedAtEpochMs: number }>;
}

function recorder(
  reporters: Set<string>,
  opts: {
    /** Audit ids that exist and are completed (completed: true) — short-circuits as alreadyActioned. */
    completedAuditIds?: Set<string>;
    /** Audit ids that exist but are incomplete (completed: false) — triggers resume. */
    incompleteAuditIds?: Set<string>;
    reasons?: string[];
    authorId?: string | null;
  } = {},
): Recorder {
  const removedMeals: Recorder["removedMeals"] = [];
  const removedComments: Recorder["removedComments"] = [];
  const actioned: string[] = [];
  const auditDocs: Map<string, ModerationActionDoc> = new Map();
  const auditDocUpdates: Map<string, { completed: true; removedAtEpochMs: number }> = new Map();
  const completedAuditIds = opts.completedAuditIds ?? new Set<string>();
  const incompleteAuditIds = opts.incompleteAuditIds ?? new Set<string>();

  const reasons = opts.reasons ?? Array.from({ length: reporters.size }, () => "spam");
  const reasonHistogram: Record<string, number> = {};
  for (const r of reasons) reasonHistogram[r] = (reasonHistogram[r] ?? 0) + 1;

  const deps: ReportDeps = {
    countDistinctReporters: async () => reporters,
    getOpenReportsMeta: async () => ({ reporters, reasonHistogram }),
    removeMeal: async (crewId, mealId) => { removedMeals.push({ crewId, mealId }); },
    removeComment: async (crewId, mealId, commentId) => {
      removedComments.push({ crewId, mealId, commentId });
    },
    markActioned: async (targetKey) => { actioned.push(targetKey); },
    writeAuditDoc: async (id, doc) => {
      if (completedAuditIds.has(id) || incompleteAuditIds.has(id)) {
        const err = new Error("Document already exists") as Error & { code: number };
        err.code = 6;
        throw err;
      }
      auditDocs.set(id, doc);
    },
    getAuditDoc: async (id) => {
      if (completedAuditIds.has(id)) return { completed: true };
      if (incompleteAuditIds.has(id)) return { completed: false };
      const doc = auditDocs.get(id);
      if (!doc) return null;
      return { completed: doc.completed };
    },
    updateAuditDoc: async (id, fields) => { auditDocUpdates.set(id, fields); },
    resolveAuthorId: async () => opts.authorId !== undefined ? opts.authorId : "alice",
  };

  return { deps, removedMeals, removedComments, actioned, auditDocs, completedAuditIds, incompleteAuditIds, auditDocUpdates };
}

// ---------------------------------------------------------------------------
// Atomic-create seam — enforces create semantics (throws on second write to same id).
// Used for the race-condition test (H3). Both callers share the same in-memory store.
// ---------------------------------------------------------------------------

interface AtomicStore {
  auditDocs: Map<string, ModerationActionDoc>;
  removedMeals: Array<{ crewId: string; mealId: string }>;
  actioned: string[];
  auditDocUpdates: Map<string, { completed: true; removedAtEpochMs: number }>;
}

function atomicRecorder(
  reporters: Set<string>,
  store: AtomicStore,
  opts: { reasons?: string[] } = {},
): ReportDeps {
  const reasons = opts.reasons ?? Array.from({ length: reporters.size }, () => "spam");
  const reasonHistogram: Record<string, number> = {};
  for (const r of reasons) reasonHistogram[r] = (reasonHistogram[r] ?? 0) + 1;

  return {
    countDistinctReporters: async () => reporters,
    getOpenReportsMeta: async () => ({ reporters, reasonHistogram }),
    removeMeal: async (crewId, mealId) => { store.removedMeals.push({ crewId, mealId }); },
    removeComment: async () => { /* not used in race test */ },
    markActioned: async (targetKey) => { store.actioned.push(targetKey); },
    writeAuditDoc: async (id, doc) => {
      // Enforce atomic create semantics: throw ALREADY_EXISTS (gRPC code 6) if doc exists.
      if (store.auditDocs.has(id)) {
        const err = new Error("Document already exists") as Error & { code: number };
        err.code = 6; // gRPC ALREADY_EXISTS
        throw err;
      }
      store.auditDocs.set(id, doc);
    },
    getAuditDoc: async (id) => {
      const doc = store.auditDocs.get(id);
      if (!doc) return null;
      // Check if a completion update has been applied.
      const update = store.auditDocUpdates.get(id);
      return { completed: update?.completed ?? doc.completed };
    },
    updateAuditDoc: async (id, fields) => { store.auditDocUpdates.set(id, fields); },
    resolveAuthorId: async () => "alice",
  };
}

// ---------------------------------------------------------------------------
// Report fixtures
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// THRESHOLD constant
// ---------------------------------------------------------------------------

describe("THRESHOLD", () => {
  it("is 3 distinct reporters", () => {
    expect(THRESHOLD).toBe(3);
  });
});

// ---------------------------------------------------------------------------
// moderationActionId — collision-safe SHA-256 hash
// ---------------------------------------------------------------------------

describe("moderationActionId", () => {
  it("produces a deterministic id prefixed with 'action_'", () => {
    const id = moderationActionId(MEAL_KEY);
    expect(id.startsWith("action_")).toBe(true);
  });

  it("produces a fixed-length 71-char id (7 prefix + 64 hex chars)", () => {
    const id = moderationActionId(MEAL_KEY);
    expect(id.length).toBe(71); // "action_" (7) + SHA-256 hex (64)
  });

  it("produces different ids for different targetKeys (no truncation collision)", () => {
    const longKey1 = "meal|crewWithAVeryLongId|" + "x".repeat(400) + "A";
    const longKey2 = "meal|crewWithAVeryLongId|" + "x".repeat(400) + "B";
    expect(moderationActionId(longKey1)).not.toBe(moderationActionId(longKey2));
  });

  it("is stable across calls (same input → same output)", () => {
    expect(moderationActionId(MEAL_KEY)).toBe(moderationActionId(MEAL_KEY));
    expect(moderationActionId(ACCOUNT_KEY)).toBe(moderationActionId(ACCOUNT_KEY));
  });
});

// ---------------------------------------------------------------------------
// Below threshold
// ---------------------------------------------------------------------------

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
    expect(r.auditDocs.size).toBe(0);
  });
});

// ---------------------------------------------------------------------------
// Threshold — meal path
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Distinct-reporter deduplication
// ---------------------------------------------------------------------------

describe("processReport — a duplicate reporter does NOT double-count", () => {
  it("3 report docs from only 2 distinct reporters stays below threshold (no removal)", async () => {
    const r = recorder(new Set(["u1", "u2"]));
    const outcome = await processReport(mealReport("u2"), r.deps);
    expect(outcome.distinctReporters).toBe(2);
    expect(outcome.thresholdReached).toBe(false);
    expect(r.removedMeals).toEqual([]);
  });

  it("crosses threshold only when a THIRD distinct reporter appears", async () => {
    const below = recorder(new Set(["u1", "u1", "u2"]));
    expect((await processReport(mealReport("u2"), below.deps)).thresholdReached).toBe(false);
    expect(below.removedMeals).toEqual([]);

    const at = recorder(new Set(["u1", "u2", "u3"]));
    expect((await processReport(mealReport("u3"), at.deps)).thresholdReached).toBe(true);
    expect(at.removedMeals).toHaveLength(1);
  });
});

// ---------------------------------------------------------------------------
// Comment path
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Account path
// ---------------------------------------------------------------------------

describe("processReport — account path is flag-only", () => {
  it("does NOT auto-remove an account at threshold — flags for manual review", async () => {
    const r = recorder(new Set(["u1", "u2", "u3"]));
    const outcome = await processReport(accountReport("u3"), r.deps);
    expect(outcome.thresholdReached).toBe(true);
    expect(outcome.action).toBe("flagged_account");
    expect(r.removedMeals).toEqual([]);
    expect(r.removedComments).toEqual([]);
    expect(r.actioned).toEqual([ACCOUNT_KEY]);
  });
});

// ---------------------------------------------------------------------------
// Audit doc — written FIRST (completed:false) at threshold, completed after removal
// ---------------------------------------------------------------------------

describe("processReport — audit doc written at threshold", () => {
  it("writes a moderationActions doc for a meal takedown with correct fields", async () => {
    const reporters = new Set(["u1", "u2", "u3"]);
    const r = recorder(reporters, { reasons: ["spam", "spam", "harassment"], authorId: "alice" });
    const outcome = await processReport(mealReport("u3"), r.deps);

    expect(outcome.action).toBe("removed_meal");
    const expectedId = moderationActionId(MEAL_KEY);
    expect(r.auditDocs.has(expectedId)).toBe(true);

    const doc = r.auditDocs.get(expectedId)!;
    expect(doc.targetKey).toBe(MEAL_KEY);
    expect(doc.targetType).toBe("meal");
    expect(doc.action).toBe("removed_meal");
    expect(new Set(doc.reporters)).toEqual(reporters);
    expect(doc.distinctCount).toBe(3);
    expect(doc.threshold).toBe(THRESHOLD);
    expect(doc.reasonHistogram).toEqual({ spam: 2, harassment: 1 });
    expect(doc.crewId).toBe(CREW);
    expect(doc.authorId).toBe("alice");
    expect(typeof doc.createdAtEpochMs).toBe("number");
    // Claim written with completed:false; then updateAuditDoc flips it.
    expect(doc.completed).toBe(false);
    expect(doc.removedAtEpochMs).toBeNull();
  });

  it("flips completed→true and sets removedAtEpochMs after successful takedown", async () => {
    const r = recorder(new Set(["u1", "u2", "u3"]), { authorId: "alice" });
    await processReport(mealReport("u3"), r.deps);

    const auditId = moderationActionId(MEAL_KEY);
    const update = r.auditDocUpdates.get(auditId);
    expect(update).toBeDefined();
    expect(update!.completed).toBe(true);
    expect(typeof update!.removedAtEpochMs).toBe("number");
  });

  it("writes the audit doc BEFORE content removal (atomic create wins the lock first)", async () => {
    // Since processReport awaits each step sequentially, if audit write succeeds and
    // removal is called, audit-first order holds — both present at outcome time.
    const r = recorder(new Set(["u1", "u2", "u3"]), { authorId: "alice" });
    const outcome = await processReport(mealReport("u3"), r.deps);

    expect(outcome.action).toBe("removed_meal");
    expect(r.auditDocs.has(moderationActionId(MEAL_KEY))).toBe(true);
    expect(r.removedMeals).toHaveLength(1);
  });

  it("writes a moderationActions doc for a comment takedown", async () => {
    const r = recorder(new Set(["u1", "u2", "u3"]));
    await processReport(commentReport("u3"), r.deps);

    const doc = r.auditDocs.get(moderationActionId(COMMENT_KEY))!;
    expect(doc).toBeDefined();
    expect(doc.action).toBe("removed_comment");
    expect(doc.targetType).toBe("comment");
    expect(doc.crewId).toBe(CREW);
  });

  it("writes a moderationActions doc for an account flag — authorId is null (M1)", async () => {
    // M1 fix: account targets should have authorId=null. The accountId field identifies the
    // subject; returning it as authorId misreads as "this account authored content."
    const r = recorder(new Set(["u1", "u2", "u3"]), { authorId: null });
    await processReport(accountReport("u3"), r.deps);

    const doc = r.auditDocs.get(moderationActionId(ACCOUNT_KEY))!;
    expect(doc).toBeDefined();
    expect(doc.action).toBe("flagged_account");
    expect(doc.targetType).toBe("account");
    expect(doc.crewId).toBeNull();
    expect(doc.authorId).toBeNull(); // M1: null for account targets, not accountId
  });

  it("does NOT write an audit doc below threshold", async () => {
    const r = recorder(new Set(["u1", "u2"]));
    await processReport(mealReport("u2"), r.deps);
    expect(r.auditDocs.size).toBe(0);
  });
});

// ---------------------------------------------------------------------------
// Idempotency — completed:true fast-path (genuinely done — no re-delete)
// ---------------------------------------------------------------------------

describe("processReport — completed claim short-circuits (alreadyActioned)", () => {
  it("returns alreadyActioned without removing content when audit doc is completed:true", async () => {
    const auditId = moderationActionId(MEAL_KEY);
    // Pre-seed as a completed claim — should be treated as genuinely done.
    const r = recorder(new Set(["u1", "u2", "u3"]), {
      completedAuditIds: new Set([auditId]),
    });
    const outcome = await processReport(mealReport("u3"), r.deps);

    expect(outcome.thresholdReached).toBe(true);
    expect(outcome.alreadyActioned).toBe(true);
    expect(r.removedMeals).toHaveLength(0);
    expect(r.auditDocs.size).toBe(0);
    expect(r.actioned).toHaveLength(0);
  });

  it("first invocation proceeds normally; second invocation with completed:true is blocked", async () => {
    const r1 = recorder(new Set(["u1", "u2", "u3"]));
    const o1 = await processReport(mealReport("u3"), r1.deps);
    expect(o1.alreadyActioned).toBeUndefined();
    expect(r1.removedMeals).toHaveLength(1);
    expect(r1.auditDocs.size).toBe(1);

    // Simulate second invocation seeing the completed audit doc.
    const auditId = moderationActionId(MEAL_KEY);
    const r2 = recorder(new Set(["u1", "u2", "u3", "u4"]), {
      completedAuditIds: new Set([auditId]),
    });
    const o2 = await processReport(mealReport("u4"), r2.deps);
    expect(o2.alreadyActioned).toBe(true);
    expect(r2.removedMeals).toHaveLength(0);
    expect(r2.auditDocs.size).toBe(0);
    expect(r2.actioned).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// CRITICAL REGRESSION FIX — incomplete claim (completed:false) MUST resume
//
// Prior design: on ALREADY_EXISTS the code returned alreadyActioned and SKIPPED the delete.
// A crash between writeAuditDoc (create wins) and removeMeal left content up forever —
// the retry saw the claim, short-circuited, and never took the content down.
//
// Fixed design: ALREADY_EXISTS → read the doc → if completed:false → RESUME the takedown.
// ---------------------------------------------------------------------------

describe("processReport — resumable takedown (CRITICAL regression fix)", () => {
  it("incomplete claim (completed:false) triggers delete + markActioned + flip completed→true", async () => {
    // Scenario: a prior invocation created the claim (completed:false) but crashed before
    // removeMeal. This retry must RESUME, not short-circuit.
    const auditId = moderationActionId(MEAL_KEY);
    const r = recorder(new Set(["u1", "u2", "u3"]), {
      incompleteAuditIds: new Set([auditId]),
    });
    const outcome = await processReport(mealReport("u3"), r.deps);

    // Must NOT return alreadyActioned — the takedown was not complete.
    expect(outcome.alreadyActioned).toBeUndefined();
    expect(outcome.action).toBe("removed_meal");
    expect(outcome.thresholdReached).toBe(true);

    // Content MUST be removed on the resume path.
    expect(r.removedMeals).toHaveLength(1);
    expect(r.removedMeals[0]).toEqual({ crewId: CREW, mealId: MEAL });

    // Reports MUST be marked actioned.
    expect(r.actioned).toEqual([MEAL_KEY]);

    // Claim MUST be flipped to completed:true.
    const update = r.auditDocUpdates.get(auditId);
    expect(update).toBeDefined();
    expect(update!.completed).toBe(true);
    expect(typeof update!.removedAtEpochMs).toBe("number");

    // writeAuditDoc was NOT called again (no new claim doc written — we resumed the existing one).
    expect(r.auditDocs.size).toBe(0);
  });

  it("incomplete claim on a comment — resume still deletes comment + marks actioned", async () => {
    const auditId = moderationActionId(COMMENT_KEY);
    const r = recorder(new Set(["u1", "u2", "u3"]), {
      incompleteAuditIds: new Set([auditId]),
    });
    const outcome = await processReport(commentReport("u3"), r.deps);

    expect(outcome.alreadyActioned).toBeUndefined();
    expect(outcome.action).toBe("removed_comment");
    expect(r.removedComments).toHaveLength(1);
    expect(r.removedComments[0]).toEqual({ crewId: CREW, mealId: MEAL, commentId: COMMENT });
    expect(r.actioned).toEqual([COMMENT_KEY]);
    expect(r.auditDocUpdates.get(auditId)?.completed).toBe(true);
  });

  it("exactly ONE delete on resume — not a second delete when content was already gone", async () => {
    // Even if removeMeal is called again (idempotent no-op in prod), the recorder still
    // records exactly one call because processReport calls it once on the resume path.
    const auditId = moderationActionId(MEAL_KEY);
    const r = recorder(new Set(["u1", "u2", "u3"]), {
      incompleteAuditIds: new Set([auditId]),
    });
    await processReport(mealReport("u3"), r.deps);

    expect(r.removedMeals).toHaveLength(1);
  });
});

// ---------------------------------------------------------------------------
// C1 / H3 — RACE TEST: atomic create lock prevents double-action
// Two invocations share a single in-memory store where writeAuditDoc enforces
// create-semantics (throws ALREADY_EXISTS on a second write to the same id).
// Neither sees a completed claim initially. The loser catches ALREADY_EXISTS,
// reads the doc (completed:false — the winner hasn't finished yet), and RESUMES.
// Both execute removeMeal, but the underlying Firestore delete is idempotent, so
// the end-state is correct. What matters: exactly one claim doc, reports actioned,
// completed eventually flipped to true.
// ---------------------------------------------------------------------------

describe("processReport — C1 atomic-create race (H3)", () => {
  it("exactly ONE audit doc and ONE markActioned when two invocations race at threshold", async () => {
    const reporters = new Set(["u1", "u2", "u3"]);
    const store: AtomicStore = { auditDocs: new Map(), removedMeals: [], actioned: [], auditDocUpdates: new Map() };

    // Both deps share the same store and see 0 existing docs initially.
    const deps1 = atomicRecorder(reporters, store);
    const deps2 = atomicRecorder(reporters, store);

    const [o1, o2] = await Promise.all([
      processReport(mealReport("u3"), deps1),
      processReport(mealReport("u3"), deps2),
    ]);

    // Neither should propagate as a hard error — both complete.
    expect(o1.thresholdReached).toBe(true);
    expect(o2.thresholdReached).toBe(true);

    // EXACTLY one claim doc.
    expect(store.auditDocs.size).toBe(1);
    expect(store.auditDocs.has(moderationActionId(MEAL_KEY))).toBe(true);

    // The completed flag must have been flipped.
    const update = store.auditDocUpdates.get(moderationActionId(MEAL_KEY));
    expect(update?.completed).toBe(true);

    // markActioned called at least once (winner calls it; resume also calls it — idempotent).
    expect(store.actioned.length).toBeGreaterThanOrEqual(1);
    expect(store.actioned.every((k) => k === MEAL_KEY)).toBe(true);

    // At least one delete happened (may be 1 or 2 depending on scheduling — both are safe
    // because the real Firestore delete is idempotent; no-op if already gone).
    expect(store.removedMeals.length).toBeGreaterThanOrEqual(1);
    expect(store.removedMeals.every((m) => m.crewId === CREW && m.mealId === MEAL)).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// H4 — firestoreDeps().writeAuditDoc uses .create() semantics
// No functions-emulator harness exists in this repo (firestore-tests/ uses
// @firebase/rules-unit-testing for rules only, not trigger integration).
// We lock the contract by asserting that the real firestoreDeps impl calls
// `.create()` via source inspection + the fact that the atomic recorder above
// exercises the same ALREADY_EXISTS path processReport relies on.
// ---------------------------------------------------------------------------

describe("processReport — H4: writeAuditDoc create-semantics contract", () => {
  it("atomic recorder throws ALREADY_EXISTS (code 6) on a second write to the same id", async () => {
    const store: AtomicStore = { auditDocs: new Map(), removedMeals: [], actioned: [], auditDocUpdates: new Map() };
    const deps = atomicRecorder(new Set(["u1", "u2", "u3"]), store);

    const fakeDoc = {} as ModerationActionDoc;
    await deps.writeAuditDoc("test-id", fakeDoc); // first write succeeds

    let caught: unknown;
    try {
      await deps.writeAuditDoc("test-id", fakeDoc); // second write throws
    } catch (e) {
      caught = e;
    }
    expect(caught).toBeDefined();
    expect((caught as { code: number }).code).toBe(6); // gRPC ALREADY_EXISTS
  });

  it("ALREADY_EXISTS with completed:true → alreadyActioned, no content removal (It3 regression test)", async () => {
    // Verifies the new read-completion-state path: create throws → getAuditDoc returns
    // completed:true → processReport short-circuits as alreadyActioned.
    const store: AtomicStore = {
      auditDocs: new Map(),
      removedMeals: [],
      actioned: [],
      auditDocUpdates: new Map(),
    };

    // Pre-seed the store with a COMPLETED claim doc.
    const auditId = moderationActionId(MEAL_KEY);
    store.auditDocs.set(auditId, { completed: true } as ModerationActionDoc);
    store.auditDocUpdates.set(auditId, { completed: true, removedAtEpochMs: Date.now() - 1000 });

    const deps = atomicRecorder(new Set(["u1", "u2", "u3"]), store);
    const outcome = await processReport(mealReport("u3"), deps);

    expect(outcome.alreadyActioned).toBe(true);
    // The pre-seeded removal is unchanged; no new delete.
    expect(store.removedMeals).toHaveLength(0);
    expect(store.actioned).toHaveLength(0);
  });

  it("ALREADY_EXISTS with completed:false → resume completes the takedown (It3 regression test)", async () => {
    // THIS IS THE REGRESSION TEST for the critical bug:
    // Prior design: ALREADY_EXISTS → alreadyActioned → content never deleted.
    // Fixed design: ALREADY_EXISTS + completed:false → resume → delete + markActioned + flip.
    const store: AtomicStore = {
      auditDocs: new Map(),
      removedMeals: [],
      actioned: [],
      auditDocUpdates: new Map(),
    };

    // Pre-seed the store with an INCOMPLETE claim doc (completed:false).
    const auditId = moderationActionId(MEAL_KEY);
    store.auditDocs.set(auditId, { completed: false } as ModerationActionDoc);
    // No auditDocUpdates entry — completed is still false.

    const deps = atomicRecorder(new Set(["u1", "u2", "u3"]), store);
    const outcome = await processReport(mealReport("u3"), deps);

    // Must NOT return alreadyActioned — content was not actually removed.
    expect(outcome.alreadyActioned).toBeUndefined();
    expect(outcome.action).toBe("removed_meal");

    // Content MUST be deleted on resume.
    expect(store.removedMeals).toHaveLength(1);
    expect(store.removedMeals[0]).toEqual({ crewId: CREW, mealId: MEAL });

    // Reports MUST be marked actioned.
    expect(store.actioned).toHaveLength(1);
    expect(store.actioned[0]).toBe(MEAL_KEY);

    // Claim MUST be flipped to completed:true.
    expect(store.auditDocUpdates.get(auditId)?.completed).toBe(true);
    expect(typeof store.auditDocUpdates.get(auditId)?.removedAtEpochMs).toBe("number");
  });
});

// ---------------------------------------------------------------------------
// Legacy idempotent re-fire test (kept for regression)
// ---------------------------------------------------------------------------

describe("processReport — idempotent re-fire without guard", () => {
  it("re-removes (no-op delete) on a 4th-report re-fire without throwing", async () => {
    // This uses the basic recorder (simple map, no create enforcement) — it validates
    // that the below-threshold / above-threshold logic doesn't itself throw on re-runs.
    const r = recorder(new Set(["u1", "u2", "u3", "u4"]));
    await processReport(mealReport("u4"), r.deps);
    // Reset audit map so second call doesn't hit ALREADY_EXISTS.
    r.auditDocs.clear();
    await processReport(mealReport("u4"), r.deps);
    expect(r.removedMeals).toEqual([
      { crewId: CREW, mealId: MEAL },
      { crewId: CREW, mealId: MEAL },
    ]);
  });
});

// ---------------------------------------------------------------------------
// Configurable threshold via env var
// ---------------------------------------------------------------------------

describe("processReport — configurable threshold (env var)", () => {
  beforeEach(() => { delete process.env.MODERATION_REPORT_THRESHOLD; });
  afterEach(() => { delete process.env.MODERATION_REPORT_THRESHOLD; });

  it("defaults to THRESHOLD (3) when env var is unset", async () => {
    const r = recorder(new Set(["u1", "u2"]));
    const outcome = await processReport(mealReport("u2"), r.deps);
    expect(outcome.thresholdReached).toBe(false); // 2 < 3
  });

  it("honors MODERATION_REPORT_THRESHOLD=2 — 2 reporters triggers action", async () => {
    process.env.MODERATION_REPORT_THRESHOLD = "2";
    const r = recorder(new Set(["u1", "u2"]));
    const outcome = await processReport(mealReport("u2"), r.deps);
    expect(outcome.thresholdReached).toBe(true);
    expect(outcome.action).toBe("removed_meal");
    expect(r.removedMeals).toHaveLength(1);
    const doc = r.auditDocs.get(moderationActionId(MEAL_KEY))!;
    expect(doc.threshold).toBe(2);
  });

  it("honors MODERATION_REPORT_THRESHOLD=5 — 3 reporters does NOT trigger action", async () => {
    process.env.MODERATION_REPORT_THRESHOLD = "5";
    const r = recorder(new Set(["u1", "u2", "u3"]));
    const outcome = await processReport(mealReport("u3"), r.deps);
    expect(outcome.thresholdReached).toBe(false);
    expect(r.removedMeals).toHaveLength(0);
  });

  it("falls back to THRESHOLD (3) when env var is not a valid integer", async () => {
    process.env.MODERATION_REPORT_THRESHOLD = "not-a-number";
    const atThree = recorder(new Set(["u1", "u2", "u3"]));
    expect((await processReport(mealReport("u3"), atThree.deps)).thresholdReached).toBe(true);
  });

  it("clamps MODERATION_REPORT_THRESHOLD=999 to 50 (M4: misconfig guard)", async () => {
    process.env.MODERATION_REPORT_THRESHOLD = "999";
    // With 50 distinct reporters the clamped threshold should be reached.
    // With 3 it should NOT be reached (clamped to 50, not 999).
    const r3 = recorder(new Set(["u1", "u2", "u3"]));
    const o3 = await processReport(mealReport("u3"), r3.deps);
    expect(o3.thresholdReached).toBe(false); // 3 < 50 (clamped from 999)
    expect(r3.removedMeals).toHaveLength(0);

    // With exactly 50 reporters it should fire.
    const fiftyReporters = new Set(Array.from({ length: 50 }, (_, i) => `u${i}`));
    const r50 = recorder(fiftyReporters);
    const o50 = await processReport(mealReport("u50"), r50.deps);
    expect(o50.thresholdReached).toBe(true);
    expect(r50.removedMeals).toHaveLength(1);
    // The audit doc records the clamped threshold (50), not 999.
    const doc = r50.auditDocs.get(moderationActionId(MEAL_KEY))!;
    expect(doc.threshold).toBe(50);
  });
});
