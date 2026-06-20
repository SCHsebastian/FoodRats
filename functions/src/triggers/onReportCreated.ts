import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";

// Reports auto-hide (UGC compliance §4.6). When a report doc lands in the top-level
// `reports` collection, count the DISTINCT reporters for that target and, once the
// count reaches THRESHOLD, auto-remove the reported content via the Admin SDK:
//   - Meal    → delete `crews/{crewId}/meals/{mealId}` (fires the EXISTING onMealDeleted
//               cascade — subcollections + Storage plate/thumbnail; no new blob logic here).
//   - Comment → delete `crews/{crewId}/meals/{mealId}/comments/{commentId}` (no subcollections
//               or Storage of its own).
//   - Account → DO NOT auto-disable. Log + flag for manual review only — an account takedown
//               is always a human decision (it may be a false dogpile).
// Below threshold, the report is logged for the manual review queue and nothing is removed.
//
// Idempotency: doc ids are deterministic (`${reporterUid}|${targetKey}`) so there is exactly
// one report doc per reporter per target — the distinct-reporter count == doc count, but we
// de-dupe the reporter set defensively. Deleting an already-removed meal/comment is a no-op
// (the Admin SDK delete on a missing doc is harmless, onMealDeleted is idempotent). A re-fire
// (e.g. a 4th report after action) recomputes, sees the content gone, and just re-logs.
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

/** The kinds of target a report can name. */
export type ReportTargetType = "meal" | "comment" | "account";

/**
 * The fields of a `reports/{reportId}` document this trigger reads. Mirrors `ReportDto` written by
 * the client (`feature/moderation`): `targetKey` is the stable dedupe/count key, `reporterId` is the
 * reporting account, and the optional id fields locate the content to remove.
 */
export interface ReportDoc {
  reporterId: string;
  targetType: ReportTargetType;
  targetKey: string;
  /** Meal + comment targets. Pinned into `targetKey` + membership/existence-gated by the rules. */
  crewId?: string;
  mealId?: string;
  /** Comment target only. */
  commentId?: string;
  /** Account target only. */
  accountId?: string;
}

/** The outcome of processing one report — returned for logging + assertable in tests. */
export interface ReportOutcome {
  targetKey: string;
  targetType: ReportTargetType;
  /** Distinct reporters counted for this target. */
  distinctReporters: number;
  /** True once the distinct-reporter count reached THRESHOLD. */
  thresholdReached: boolean;
  /** What the function did about it. */
  action: "below_threshold" | "removed_meal" | "removed_comment" | "flagged_account";
}

/**
 * Injectable collaborators — lets the auto-hide decision be unit-tested without firebase-admin.
 * Each is a thin, side-effecting seam over one Admin SDK op.
 */
export interface ReportDeps {
  /**
   * Returns the set of DISTINCT reporter ids that have an OPEN report for this `targetKey`.
   * Implementations query `reports where targetKey == targetKey and status == "open"` and collect
   * `reporterId`s into a Set.
   */
  countDistinctReporters(targetKey: string): Promise<Set<string>>;
  /** Deletes a meal doc (`crews/{crewId}/meals/{mealId}`), firing the onMealDeleted cascade. No-op if absent. */
  removeMeal(crewId: string, mealId: string): Promise<void>;
  /** Deletes a comment doc (`crews/{crewId}/meals/{mealId}/comments/{commentId}`). No-op if absent. */
  removeComment(crewId: string, mealId: string, commentId: string): Promise<void>;
  /** Marks every report doc for `targetKey` as actioned (Admin SDK), so the queue reflects the takedown. */
  markActioned(targetKey: string): Promise<void>;
}

/**
 * Pure-ish core: given a newly-created report and the deps seam, count distinct reporters and,
 * at/above THRESHOLD, auto-remove the target (or flag, for accounts). Idempotent and safe to re-run.
 *
 * No firebase-admin import — all I/O goes through `deps`, so this is fully unit-testable.
 */
export async function processReport(report: ReportDoc, deps: ReportDeps): Promise<ReportOutcome> {
  const distinct = await deps.countDistinctReporters(report.targetKey);
  const distinctReporters = distinct.size;
  const thresholdReached = distinctReporters >= THRESHOLD;

  if (!thresholdReached) {
    // Manual-review queue: structured audit line below; no removal.
    const outcome: ReportOutcome = {
      targetKey: report.targetKey,
      targetType: report.targetType,
      distinctReporters,
      thresholdReached: false,
      action: "below_threshold",
    };
    auditLog(outcome, report);
    return outcome;
  }

  let action: ReportOutcome["action"];
  switch (report.targetType) {
    case "meal": {
      if (report.crewId && report.mealId) {
        // Fires the EXISTING onMealDeleted cascade (subcollections + Storage). No blob logic here.
        await deps.removeMeal(report.crewId, report.mealId);
      } else {
        logger.warn(
          `onReportCreated: meal report missing crewId/mealId for ${report.targetKey}`,
        );
      }
      action = "removed_meal";
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
      action = "removed_comment";
      break;
    }
    case "account": {
      // NEVER auto-disable an account — always a human decision (possible false dogpile).
      action = "flagged_account";
      break;
    }
  }

  await deps.markActioned(report.targetKey);

  const outcome: ReportOutcome = {
    targetKey: report.targetKey,
    targetType: report.targetType,
    distinctReporters,
    thresholdReached: true,
    action,
  };
  auditLog(outcome, report);
  return outcome;
}

/** Structured audit line the moderation runbook greps for. Always emitted (below + at threshold). */
function auditLog(outcome: ReportOutcome, report: ReportDoc): void {
  logger.info("onReportCreated: moderation audit", {
    event: "report_processed",
    targetKey: outcome.targetKey,
    targetType: outcome.targetType,
    distinctReporters: outcome.distinctReporters,
    threshold: THRESHOLD,
    thresholdReached: outcome.thresholdReached,
    action: outcome.action,
    reporterId: report.reporterId,
    crewId: report.crewId ?? null,
    mealId: report.mealId ?? null,
    commentId: report.commentId ?? null,
    accountId: report.accountId ?? null,
  });
}

/** Firestore-backed deps (the real, side-effecting implementations over the Admin SDK). */
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
    removeMeal: async (crewId, mealId) => {
      // Fires onMealDeleted (cascade + Storage reclaim). A missing meal is a harmless no-op.
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
  };
}

export const onReportCreated = onDocumentCreated(
  {
    document: "reports/{reportId}",
    region: "europe-west3",
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
