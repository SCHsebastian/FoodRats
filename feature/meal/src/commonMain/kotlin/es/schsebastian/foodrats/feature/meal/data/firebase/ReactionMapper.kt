package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealReaction
import es.schsebastian.foodrats.core.domain.meal.ReactionKind
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.time.Instant

/**
 * Maps a [ReactionDto] to a domain [MealReaction], or `null` when the doc cannot be turned into a
 * valid reaction:
 *  - a malformed/blank reactor uid, or
 *  - an UNKNOWN [ReactionKind] key (forward-compat: a kind a newer client wrote that this build
 *    doesn't know about is silently skipped rather than failing the whole read — see
 *    [ReactionKind.fromKey]).
 *
 * Returning `null` (rather than a typed error) keeps the aggregate read resilient: one unreadable
 * member doc must not blank the whole meal's reaction count.
 */
fun ReactionDto.toDomainOrNull(crewId: CrewId, mealId: MealId): MealReaction? {
    val accountId = (AccountId.of(reactorId.orEmpty()) as? Result.Ok)?.value ?: return null
    val resolvedKind = ReactionKind.fromKey(kind.orEmpty()) ?: return null
    return MealReaction(
        mealId = mealId,
        crewId = crewId,
        reactorId = accountId,
        kind = resolvedKind,
        reactedAt = Instant.fromEpochMilliseconds(reactedAtEpochMs ?: 0L),
    )
}
