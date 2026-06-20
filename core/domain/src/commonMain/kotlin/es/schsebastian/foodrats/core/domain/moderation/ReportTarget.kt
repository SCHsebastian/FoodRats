package es.schsebastian.foodrats.core.domain.moderation

import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId

/**
 * What is being reported (UGC compliance §4). Sealed so the security rules + the `onReportCreated`
 * function can resolve the underlying document deterministically, and so the client can build a stable
 * dedupe/count key per target.
 *
 * The security rules authorize a report by crew membership ([Meal]/[Comment]: the reporter must be a
 * member of the crew that holds the content) and target existence, and they block account self-reports
 * with `accountId != reporter` — so no reported-author id needs to ride on the wire.
 */
sealed interface ReportTarget {
    /** A meal copy in a specific crew (matches the per-crew meal doc path `crews/{crewId}/meals/{mealId}`). */
    data class Meal(val mealId: MealId, val crewId: CrewId) : ReportTarget

    /** A comment under a meal copy in a specific crew. */
    data class Comment(
        val mealId: MealId,
        val crewId: CrewId,
        val commentId: MealCommentId,
    ) : ReportTarget

    /** A user/profile (no crew scope). Account-level reports are human-reviewed, never auto-actioned. */
    data class Account(val accountId: AccountId) : ReportTarget
}
