package es.schsebastian.foodrats.core.domain.moderation

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result

/**
 * Submits user reports of objectionable content/users (UGC compliance §4 — "mechanism to report
 * offensive content"). Write-only from the client's perspective: the `reports` collection is a
 * server-only readable moderation queue, so there is no `observe`. Implemented in `:feature:moderation`
 * over Firestore; a server-side enforcement layer can later implement the same port without touching
 * call sites.
 */
interface ReportPort {
    /**
     * Records [reporter]'s report of [target] for [reason]. Idempotent: a second report of the same
     * target by the same reporter fails with [ReportError.Submit.AlreadyReported]. Self-reports fail
     * with [ReportError.Submit.SelfReport].
     */
    suspend fun report(
        reporter: AccountId,
        target: ReportTarget,
        reason: ReportReason,
    ): Result<Unit, ReportError>
}
