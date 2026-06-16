package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlin.time.Instant

/**
 * A lightweight, expressive react a crew member left on a meal — the affirmation
 * counterpart to a numeric [Score], NOT a vote and NOT a like-counter.
 *
 * Invariant: at most ONE reaction per [reactorId] per meal (Firestore key is the uid;
 * see [MealReactions.reactionBy] / [MealReactions.hasReacted]). Reacting again toggles the
 * existing reaction off — see [MealReactionPort.toggle].
 *
 * The displayed glyph is NOT stored here: for the parked [ReactionKind.DailyGlyph] kind it
 * is derived at render time from the meal's day via `DailyEmote.forDay(meal.day)`. Only the
 * fact of the reaction (who, which meal, which kind, when) is persisted.
 */
data class MealReaction(
    val mealId: MealId,
    val crewId: CrewId,
    val reactorId: AccountId,
    val kind: ReactionKind,
    val reactedAt: Instant,
)

/**
 * The reactions on a single meal, as a read model. Mirrors [MealWithRatings]: gives the
 * feed/presentation a count and a "did the viewer react?" answer without re-deriving them
 * at each call site.
 */
data class MealReactions(
    val mealId: MealId,
    val reactions: List<MealReaction>,
) {
    val count: Int get() = reactions.size

    /** This member's reaction, or `null` if they haven't reacted. Enforces the one-per-member view. */
    fun reactionBy(reactorId: AccountId): MealReaction? =
        reactions.firstOrNull { it.reactorId == reactorId }

    /** Whether [reactorId] has reacted to this meal. */
    fun hasReacted(reactorId: AccountId): Boolean = reactionBy(reactorId) != null

    companion object {
        fun empty(mealId: MealId): MealReactions = MealReactions(mealId, emptyList())
    }
}
