import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { createHash } from "node:crypto";

// Reports auto-hide (UGC compliance §4.6). When a report doc lands in the top-level
// `reports` collection, count the DISTINCT reporters for that target and, once the
// count reaches THRESHOLD, auto-remove the reported content via the Admin SDK:
//   - Meal    → delete `crews/{crewId}/meals/{mealId}` (fires the EXISTING onMealDeleted
//               cascade — subcollections + Storage plate/thumbnail; no new blob logic here).
//   - Comment → delete `crews/{crewId}/meals/{mealId}/comments/{commentId}` (no subcollections
//               or Storage of its own).
//   - Account → DO NOT auto-disable. Write a `moderationActions` audit doc and flag for
//               manual review only — an account takedown is always a human decision.
// Below threshold, the report is logged for the manual review queue and nothing is removed.
//
// Idempotency (two levels):
//   1. Report-level: doc ids are deterministic (`${reporterUid}|${targetKey}`) so there is
//      exactly one report doc per reporter per target. The distinct-reporter count == doc count,
//      but we de-dupe the reporter set defensively.
//   2. Action-level: correctness is guaranteed by a RESUMABLE CLAIM pattern built on an ATOMIC
//      CREATE of the `moderationActions` doc via `.create()`, which fails with ALREADY_EXISTS
//      (gRPC code 6) if another invocation has already won the race.
//      The claim doc is written with `completed: false` first. On ALREADY_EXISTS the existing
//      doc is READ:
//        - `completed === true`  → genuinely done → return `alreadyActioned`.
//        - `completed === false` → a prior invocation claimed but crashed before finishing →
//                                  RESUME: re-run removeMeal/removeComment + markActioned,
//                                  then flip completed → true.
//      This makes retry SAFE and LIVE: a crash between writeAuditDoc and the content deletion
//      no longer strands flagged content forever.
//
// Ordering: claim doc is WRITTEN FIRST (completed=false), then content removal, then
// markActioned, then the claim is COMPLETED (completed=true, removedAtEpochMs=<now>).
// A crash anywhere after the create re-fires as a resume on the next retry, re-running all
// idempotent downstream ops (delete-on-missing is a no-op; batch over 0 open reports is a no-op).
// The fast-path `auditDocExists` is REMOVED — a fast-path that ignores completion state is the
// root cause of the regression; correctness must come from reading `completed`.
//
// Threshold config: THRESHOLD (exported default = 3) is the value unit tests pin. At runtime
// `process.env.MODERATION_REPORT_THRESHOLD` overrides it if set to a valid integer in [1, 50].
// Values above 50 are clamped to 50 to prevent misconfig from making auto-takedown unreachable.
// Any override that differs from the default is logged as a warning for operator visibility.
//
// Trust model: the security rules PIN `crewId`/`mealId`/`commentId`/`accountId` back into the
// validated `targetKey` and gate the create on crew membership + target existence, so the
// reporter-supplied fields this trigger deletes by provably match the reported target. We also
// only count reports whose `status == "open"` (actioned/closed reports are excluded).
//
// The pure core (`processReport`) takes an injected `ReportDeps` seam so it is unit-testable
// without firebase-admin, mirroring the `NudgeDeps` / `MealBlobStore` pattern in the other
// triggers.

/** Distinct reporters required before a target is auto-removed. Exported so the test pins it. */
export const THRESHOLD = 3;

/** Maximum value a MODERATION_REPORT_THRESHOLD override may take (values above are clamped). */
const MAX_THRESHOLD = 50;

/**
 * Runtime threshold: honours `MODERATION_REPORT_THRESHOLD` env var when set to a valid integer
 * in [1, MAX_THRESHOLD]. Values > MAX_THRESHOLD are clamped so a stray high value (e.g. "999")
 * cannot silently disable moderation. Any non-default value is logged as a warning.
 */
function resolvedThreshold(): number {
  const env = process.env.MODERATION_REPORT_THRESHOLD;
  if (env !== undefined && env !== "") {
    const parsed = parseInt(env, 10);
    if (!isNaN(parsed) && parsed > 0) {
      const clamped = Math.min(parsed, MAX_THRESHOLD);
      if (clamped !== THRESHOLD) {
        logger.warn(
          `onReportCreated: MODERATION_REPORT_THRESHOLD overridden to ${clamped}` +
          (parsed > MAX_THRESHOLD ? ` (clamped from ${parsed})` : "") +
          ` (default is ${THRESHOLD}) — verify this is intentional`,
        );
      }
      return clamped;
    }
  }
  return THRESHOLD;
}

/** The kinds of target a report can name. */
export type ReportTargetType = "meal" | "comment" | "account";

export interface ReportDoc {
  reporterId: string;
  targetType: ReportTargetType;
  targetKey: string;
  crewId?: string;
  mealId?: string;
  commentId?: string;
  accountId?: string;
}

export interface ReportOutcome {
  targetKey: string;
  targetType: ReportTargetType;
  distinctReporters: number;
  thresholdReached: boolean;
  action: "below_threshold" | "removed_meal" | "removed_comment" | "flagged_account";
  /** Present when the idempotency guard detected an already-actioned target. */
  alreadyActioned?: true;
}

/**
 * The schema written to `moderationActions/{id}` (id = moderationActionId(targetKey)).
 * Server-only (rules deny all client access). Forms the durable audit trail and the resumable
 * claim lock for the idempotency protocol.
 *
 * CLAIM PROTOCOL: the doc is created with `completed: false` as a distributed lock. After
 * content removal + markActioned succeed, the doc is updated to `completed: true` and
 * `removedAtEpochMs` is set. A retry that sees `completed: false` RESUMES the takedown;
 * a retry that sees `completed: true` short-circuits as `alreadyActioned`.
 */
export interface ModerationActionDoc {
  /** The compound key identifying the reported target (stable across reports). */
  targetKey: string;
  targetType: ReportTargetType;
  /** What was done: "removed_meal" | "removed_comment" | "flagged_account". */
  action: "removed_meal" | "removed_comment" | "flagged_account";
  /** Deduplicated set of reporter ids whose open reports triggered this action. */
  reporters: string[];
  /** How many distinct reporters were counted. */
  distinctCount: number;
  /** The threshold that was in force at the time of action. */
  threshold: number;
  /** reason → count over the open report docs at the moment of action. */
  reasonHistogram: Record<string, number>;
  /** crewId if this target is meal- or comment-scoped (null for account targets). */
  crewId: string | null;
  /** authorId of the reported content for meal/comment targets; null for account targets. */
  authorId: string | null;
  /** Unix epoch milliseconds when the claim doc was first created. */
  createdAtEpochMs: number;
  /**
   * Whether the takedown is fully complete (content removed + reports marked actioned).
   * Starts as `false` (claim only); flipped to `true` after all downstream ops succeed.
   * A retry seeing `false` means a prior invocation crashed mid-takedown → resume.
   */
  completed: boolean;
  /**
   * Unix epoch milliseconds when the content was confirmed removed.
   * `null` when `completed === false` (incomplete claim / account flag-only).
   */
  removedAtEpochMs: number | null;
}

/**
 * Deterministic `moderationActions` doc id derived from targetKey.
 * Uses SHA-256 to produce a fixed-length, collision-safe id regardless of targetKey length
 * or content. The hex digest is 64 chars; the "action_" prefix keeps it distinct from report
 * doc ids and makes the collection queryable by prefix in logs.
 */
export function moderationActionId(targetKey: string): string {
  const hash = createHash("sha256").update(targetKey).digest("hex");
  return `action_${hash}`;
}

/**
 * Defense-in-depth: rebuild the canonical `targetKey` from the report's TYPED fields and confirm it
 * matches the stored `targetKey` BEFORE any admin-privileged takedown. The `reports` create rule
 * already pins `crewId`/`mealId`/`commentId`/`accountId` into `targetKey` and gates on membership +
 * target existence — but this trigger DELETES by those reporter-supplied fields, so if a rules
 * regression ever let them desync from `targetKey`, a forged report could aim a takedown at unrelated
 * content. Reconstructing here makes the trigger self-validating regardless of the rules.
 *
 * Returns the reconstructed key, or `null` when the required fields are absent / inconsistent.
 */
export function reconstructTargetKey(report: ReportDoc): string | null {
  switch (report.targetType) {
    case "meal":
      return report.crewId && report.mealId ? `meal|${report.crewId}|${report.mealId}` : null;
    case "comment":
      return report.crewId && report.mealId && report.commentId
        ? `comment|${report.crewId}|${report.mealId}|${report.commentId}`
        : null;
    case "account":
      return report.accountId ? `account|${report.accountId}` : null;
    default:
      return null;
  }
}

/**
 * Injectable collaborators — lets the auto-hide decision be unit-tested without firebase-admin.
 */
export interface ReportDeps {
  countDistinctReporters(targetKey: string): Promise<Set<string>>;
  /**
   * Returns the reason histogram and reporter ids for all open reports for this targetKey.
   * The trigger uses these to populate the audit doc.
   */
  getOpenReportsMeta(targetKey: string): Promise<{ reporters: Set<string>; reasonHistogram: Record<string, number> }>;
  removeMeal(crewId: string, mealId: string): Promise<void>;
  removeComment(crewId: string, mealId: string, commentId: string): Promise<void>;
  markActioned(targetKey: string): Promise<void>;
  /**
   * Atomically creates a `moderationActions/{id}` doc using `.create()` semantics.
   * THROWS if the doc already exists (gRPC ALREADY_EXISTS / Firestore error code 6).
   * The doc is written with `completed: false` as the claim lock.
   * Callers MUST catch ALREADY_EXISTS, then call `getAuditDoc` to check `completed` and
   * either resume (false) or short-circuit (true).
   * Correctness depends on `.create()` atomicity — NOT on a prior read.
   */
  writeAuditDoc(id: string, doc: ModerationActionDoc): Promise<void>;
  /**
   * Reads the existing `moderationActions/{id}` doc.
   * Returns the doc (with at least `completed`) if it exists, or `null` if not found.
   * Called AFTER catching ALREADY_EXISTS from `writeAuditDoc` to determine whether to
   * resume an incomplete takedown or short-circuit as `alreadyActioned`.
   */
  getAuditDoc(id: string): Promise<Pick<ModerationActionDoc, "completed"> | null>;
  /**
   * Updates an existing `moderationActions/{id}` doc with the completion fields.
   * Called after all downstream ops (removeMeal/removeComment + markActioned) succeed,
   * to flip `completed: true` and record `removedAtEpochMs`.
   */
  updateAuditDoc(id: string, fields: { completed: true; removedAtEpochMs: number }): Promise<void>;
  /**
   * Resolves the authorId from the target doc.
   * Returns null for account targets (the accountId field on the report identifies the
   * subject; returning it here would misread as "authored content").
   * Returns null if the content is missing or the lookup fails (best-effort).
   */
  resolveAuthorId(report: ReportDoc): Promise<string | null>;
}

export async function processReport(report: ReportDoc, deps: ReportDeps): Promise<ReportOutcome> {
  // Defense-in-depth (security #6): refuse to act on any report whose typed fields don't
  // reconstruct its stored targetKey. A mismatch means the report is internally inconsistent
  // (only reachable via a rules regression / Admin-SDK-written doc) — never auto-take-down by it.
  const reconstructed = reconstructTargetKey(report);
  if (reconstructed === null || reconstructed !== report.targetKey) {
    logger.error(
      `onReportCreated: targetKey mismatch — refusing takedown ` +
        `(stored=${report.targetKey}, reconstructed=${reconstructed ?? "null"}, type=${report.targetType})`,
    );
    return {
      targetKey: report.targetKey,
      targetType: report.targetType,
      distinctReporters: 0,
      thresholdReached: false,
      action: "below_threshold",
    };
  }

  const threshold = resolvedThreshold();
  const distinct = await deps.countDistinctReporters(report.targetKey);
  const distinctReporters = distinct.size;
  const thresholdReached = distinctReporters >= threshold;

  if (!thresholdReached) {
    const outcome: ReportOutcome = {
      targetKey: report.targetKey,
      targetType: report.targetType,
      distinctReporters,
      thresholdReached: false,
      action: "below_threshold",
    };
    auditLog(outcome, report, threshold);
    return outcome;
  }

  const auditId = moderationActionId(report.targetKey);

  // Gather meta for the audit doc BEFORE the atomic create (target doc still exists at this point).
  // This must happen here, not inside the resume branch, because on a crash-resume the target
  // doc may already be deleted — the meta we read now is the best snapshot we have.
  const { reporters, reasonHistogram } = await deps.getOpenReportsMeta(report.targetKey);
  const authorId = await deps.resolveAuthorId(report);

  const action: "removed_meal" | "removed_comment" | "flagged_account" =
    report.targetType === "meal"
      ? "removed_meal"
      : report.targetType === "comment"
        ? "removed_comment"
        : "flagged_account";

  // STEP 1 — Attempt the atomic claim-create (completed: false).
  // This is the distributed lock. The winner proceeds; the loser catches ALREADY_EXISTS
  // and reads the existing doc to decide whether to resume or short-circuit.
  let resuming = false;
  try {
    const auditDoc: ModerationActionDoc = {
      targetKey: report.targetKey,
      targetType: report.targetType,
      action,
      reporters: Array.from(reporters),
      distinctCount: reporters.size,
      threshold,
      reasonHistogram,
      crewId: report.crewId ?? null,
      authorId,
      createdAtEpochMs: Date.now(),
      completed: false,
      removedAtEpochMs: null,
    };
    await deps.writeAuditDoc(auditId, auditDoc);
    // We won the create-lock. Fall through to content removal.
  } catch (err: unknown) {
    if (!isAlreadyExistsError(err)) {
      throw err; // unexpected error — propagate so Cloud Functions can retry
    }
    // ALREADY_EXISTS: another invocation created the claim doc (or we're retrying after a crash).
    // Read the existing doc to determine whether it is complete or needs resuming.
    const existing = await deps.getAuditDoc(auditId);
    if (existing === null || existing.completed === true) {
      // Genuinely done — the content was already removed in a prior complete run.
      logger.info(
        `onReportCreated: ${report.targetKey} already fully actioned (completed=true) — skipping`,
      );
      return alreadyActionedOutcome(report, distinctReporters);
    }
    // completed === false: a prior invocation claimed but crashed before finishing.
    // Resume: re-run the idempotent downstream ops (delete-on-missing + batch-over-0 are no-ops).
    logger.info(
      `onReportCreated: ${report.targetKey} claim exists but completed=false — resuming takedown`,
    );
    resuming = true;
  }

  // STEP 2 — Content removal (idempotent: delete-on-missing is a Firestore no-op).
  switch (report.targetType) {
    case "meal": {
      if (report.crewId && report.mealId) {
        await deps.removeMeal(report.crewId, report.mealId);
      } else {
        logger.warn(`onReportCreated: meal report missing crewId/mealId for ${report.targetKey}`);
      }
      break;
    }
    case "comment": {
      if (report.crewId && report.mealId && report.commentId) {
        await deps.removeComment(report.crewId, report.mealId, report.commentId);
      } else {
        logger.warn(
          `onReportCreated: comment report missing crewId/mealId/commentId for ${report.targetKey}`,
        );
      }
      break;
    }
    case "account": {
      // Account targets are flagged for manual review only — no auto-removal.
      break;
    }
  }

  // STEP 3 — Mark all open reports as actioned (idempotent: batch over 0 docs is a no-op).
  await deps.markActioned(report.targetKey);

  // STEP 4 — Flip the claim doc to completed. This is the durable signal that steps 2+3 ran.
  await deps.updateAuditDoc(auditId, { completed: true, removedAtEpochMs: Date.now() });

  if (resuming) {
    logger.info(`onReportCreated: ${report.targetKey} resume complete`);
  }

  const outcome: ReportOutcome = {
    targetKey: report.targetKey,
    targetType: report.targetType,
    distinctReporters,
    thresholdReached: true,
    action,
  };
  auditLog(outcome, report, threshold);
  return outcome;
}

function alreadyActionedOutcome(report: ReportDoc, distinctReporters: number): ReportOutcome {
  return {
    targetKey: report.targetKey,
    targetType: report.targetType,
    distinctReporters,
    thresholdReached: true,
    action: report.targetType === "meal"
      ? "removed_meal"
      : report.targetType === "comment"
        ? "removed_comment"
        : "flagged_account",
    alreadyActioned: true,
  };
}

/** Returns true for the Firestore / gRPC ALREADY_EXISTS error thrown by `.create()`. */
function isAlreadyExistsError(err: unknown): boolean {
  if (typeof err !== "object" || err === null) return false;
  // firebase-admin surfaces ALREADY_EXISTS as code 6 (gRPC) or HTTP status 409.
  const e = err as Record<string, unknown>;
  return e["code"] === 6 || e["code"] === "already-exists" || e["status"] === 409;
}

function auditLog(outcome: ReportOutcome, report: ReportDoc, threshold: number): void {
  logger.info("onReportCreated: moderation audit", {
    event: "report_processed",
    targetKey: outcome.targetKey,
    targetType: outcome.targetType,
    distinctReporters: outcome.distinctReporters,
    threshold,
    thresholdReached: outcome.thresholdReached,
    action: outcome.action,
    reporterId: report.reporterId,
    crewId: report.crewId ?? null,
    mealId: report.mealId ?? null,
    commentId: report.commentId ?? null,
    accountId: report.accountId ?? null,
  });
}

function firestoreDeps(): ReportDeps {
  const db = getFirestore();
  return {
    countDistinctReporters: async (targetKey) => {
      const snap = await db
        .collection("reports")
        .where("targetKey", "==", targetKey)
        .where("status", "==", "open")
        .get();
      const reporters = new Set<string>();
      for (const d of snap.docs) {
        const reporterId = d.data().reporterId as string | undefined;
        if (reporterId) reporters.add(reporterId);
      }
      return reporters;
    },

    getOpenReportsMeta: async (targetKey) => {
      const snap = await db
        .collection("reports")
        .where("targetKey", "==", targetKey)
        .where("status", "==", "open")
        .get();
      const reporters = new Set<string>();
      const reasonHistogram: Record<string, number> = {};
      for (const d of snap.docs) {
        const data = d.data();
        const reporterId = data.reporterId as string | undefined;
        if (reporterId) reporters.add(reporterId);
        const reason = data.reason as string | undefined;
        if (reason) reasonHistogram[reason] = (reasonHistogram[reason] ?? 0) + 1;
      }
      return { reporters, reasonHistogram };
    },

    removeMeal: async (crewId, mealId) => {
      await db.doc(`crews/${crewId}/meals/${mealId}`).delete();
    },

    removeComment: async (crewId, mealId, commentId) => {
      await db.doc(`crews/${crewId}/meals/${mealId}/comments/${commentId}`).delete();
    },

    markActioned: async (targetKey) => {
      const snap = await db
        .collection("reports")
        .where("targetKey", "==", targetKey)
        .where("status", "==", "open")
        .get();
      const batch = db.batch();
      for (const d of snap.docs) {
        batch.update(d.ref, { status: "actioned" });
      }
      await batch.commit();
    },

    /**
     * Atomically creates the `moderationActions/{id}` doc with `completed: false`.
     * Throws with code 6 (gRPC ALREADY_EXISTS) if the doc already exists — that error
     * is the distributed lock signal; callers must catch it, call `getAuditDoc`, and
     * resume (completed=false) or short-circuit (completed=true).
     */
    writeAuditDoc: async (id, auditDoc) => {
      await db.collection("moderationActions").doc(id).create(auditDoc);
    },

    getAuditDoc: async (id) => {
      const snap = await db.collection("moderationActions").doc(id).get();
      if (!snap.exists) return null;
      const data = snap.data();
      return { completed: (data?.completed as boolean | undefined) ?? false };
    },

    updateAuditDoc: async (id, fields) => {
      await db.collection("moderationActions").doc(id).update(fields);
    },

    resolveAuthorId: async (report) => {
      try {
        if (report.targetType === "meal" && report.crewId && report.mealId) {
          const snap = await db.doc(`crews/${report.crewId}/meals/${report.mealId}`).get();
          return (snap.data()?.authorId as string | undefined) ?? null;
        }
        if (report.targetType === "comment" && report.crewId && report.mealId && report.commentId) {
          const snap = await db
            .doc(`crews/${report.crewId}/meals/${report.mealId}/comments/${report.commentId}`)
            .get();
          return (snap.data()?.authorId as string | undefined) ?? null;
        }
        // Account targets: return null — `accountId` identifies the subject, not an author.
        return null;
      } catch {
        // Best-effort — the content may already be deleted. Return null, not a failure.
        return null;
      }
    },
  };
}

export const onReportCreated = onDocumentCreated(
  {
    document: "reports/{reportId}",
    region: "europe-west3",
    retry: true,
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const data = snap.data() as Partial<ReportDoc>;

    const targetType = data.targetType;
    const targetKey = data.targetKey;
    if (
      !targetKey ||
      (targetType !== "meal" && targetType !== "comment" && targetType !== "account")
    ) {
      logger.warn(
        `onReportCreated: malformed report ${event.params.reportId} (targetType=${targetType}, targetKey=${targetKey})`,
      );
      return;
    }

    const report: ReportDoc = {
      reporterId: (data.reporterId as string) ?? "",
      targetType,
      targetKey,
      crewId: data.crewId,
      mealId: data.mealId,
      commentId: data.commentId,
      accountId: data.accountId,
    };

    await processReport(report, firestoreDeps());
  },
);
