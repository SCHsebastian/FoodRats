package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Read + write contract for lightweight meal reactions, declared in `:core:domain` so
 * `:feature:feed` can observe and toggle a meal's reactions WITHOUT depending on the meal
 * feature — mirroring [MealReadPort] (read) and [MealRatingPort] / [MealCommentPort] (write).
 *
 * The implementation (Firestore subcollection `crews/{crewId}/meals/{mealId}/reactions/{uid}`
 * + repository) lives in `:feature:feed`'s adapter layer; this port is the only thing the
 * domain/use-case layer sees.
 *
 * ## Invariants the adapter MUST enforce (authoritatively, in the transaction + rules)
 * - One reaction per member per meal (doc id == reactor uid).
 * - [toggle] is idempotent-by-intent: if the member already reacted with the same [kind],
 *   it REMOVES the reaction; otherwise it ADDS it. The returned [ReactionToggle] reports
 *   which happened so the call site can fire the right analytics / animation.
 * - Member-only; a member can only toggle their OWN reaction.
 * - No push notification is sent on react (reactions are ambient — roadmap §1.3).
 *
 * [reactorId] is carried explicitly (not implied by ambient auth inside the adapter) to keep
 * the reaction identity part of the domain contract, consistent with [MealRatingPort.rate].
 */
interface MealReactionPort {
    /**
     * Live reactions for one meal. Emits [MealReactions.empty] for a meal with none.
     * Re-emits whenever any crew member adds/removes a reaction.
     */
    fun observe(crewId: CrewId, mealId: MealId): Flow<Result<MealReactions, ReactionError.Read>>

    /**
     * Toggles [reactorId]'s reaction of [kind] on [mealId]: adds it if absent, removes it if
     * already present. The [ReactionToggle] outcome says which way it went.
     */
    suspend fun toggle(
        crewId: CrewId,
        mealId: MealId,
        reactorId: AccountId,
        kind: ReactionKind,
    ): Result<ReactionToggle, ReactionError.Toggle>
}

/** The direction a [MealReactionPort.toggle] resolved to. */
sealed interface ReactionToggle {
    /** The reaction was created (member had not reacted with this kind). */
    data object Added : ReactionToggle

    /** The reaction was removed (member had already reacted with this kind). */
    data object Removed : ReactionToggle
}

sealed interface ReactionError {
    sealed interface Read : ReactionError {
        /** The caller is not a member of the crew. */
        data object Unauthorized : Read
        data object Unavailable  : Read
    }

    sealed interface Toggle : ReactionError {
        /** The caller is not a member of the crew. */
        data object Unauthorized : Toggle
        /** The referenced meal no longer exists. */
        data object MealNotFound : Toggle
        /** No connectivity to persist the toggle. */
        data object Offline : Toggle
        data object Unavailable : Toggle
    }
}
