package es.schsebastian.foodrats.core.domain.outbox

import es.schsebastian.foodrats.core.domain.meal.CommentText
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.ReactionKind
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId

/**
 * A user mutation durably parked in the write outbox (P2 §1 T1) so it can be
 * replayed idempotently when connectivity returns.
 *
 * Covers the eight rate / comment / reaction / crew-admin mutations only — the
 * meal-publish queue is a SEPARATE, untouched durable queue ([DraftQueuePort] in
 * `:feature:meal`); there is deliberately NO `PublishMeal` leaf here.
 *
 * A `sealed interface` with `data class` leaves (not an enum) so a leaf can grow
 * a payload later without a breaking change. Every leaf references the shared
 * `:core:domain` value objects directly — keeping the command typed end-to-end —
 * EXCEPT [ToggleReaction], which carries the reaction kind as its persisted
 * [ReactionKind.key] string ([ToggleReaction.reactionKindKey]): the handler
 * re-parses it via `ReactionKind.fromKey`, so an unknown discriminator persisted
 * by a newer build degrades gracefully rather than failing to deserialize.
 *
 * IDEMPOTENCY. Each leaf computes a deterministic [idempotencyKey] (see P2 §2).
 * Enqueue coalesces on this key (last-write-wins, replacing an existing
 * Pending/Failed entry) and the handler uses it to dedupe replays, so a retried
 * command never applies twice. NOT `@Serializable` — the serialization DTO lives
 * in `:core:data`'s `OutboxLocalStore`, keeping vendor/serialization concerns out
 * of the domain.
 */
sealed interface PendingCommand {

    /**
     * Deterministic per-command idempotency token (P2 §2). Stable across retries
     * and identical for two logically-equal commands, so enqueue can coalesce and
     * the handler can dedupe replays.
     */
    val idempotencyKey: String

    /**
     * Per-aggregate ordering key (H2). Commands sharing the same [aggregateKey]
     * must be drained FIFO — the runner halts a group on the first retryable failure
     * so a later command in the same group never executes before an earlier one
     * succeeds. Commands in different groups are independent.
     *
     * Shape: `"<kind>:<id>"` where `<kind>` is the aggregate type and `<id>` is the
     * narrowest stable discriminator that prevents two logically-independent
     * mutations from sharing a key (e.g. `commentId`, not just `mealId`).
     */
    val aggregateKey: String

    /** Set the rater's [score] on a meal. Idempotent (overwrites `ratings[uid]`). */
    data class RateMeal(
        val crewId: CrewId,
        val mealId: MealId,
        val raterId: AccountId,
        val score: Score,
    ) : PendingCommand {
        override val idempotencyKey: String =
            "rate:${crewId.value}:${mealId.value}:${raterId.value}"
        override val aggregateKey: String =
            "rate:${mealId.value}:${raterId.value}"
    }

    /**
     * Post a comment with a client-minted [commentId] (so a replayed `.set()`
     * targets the same doc id rather than creating a duplicate).
     */
    data class PostComment(
        val crewId: CrewId,
        val mealId: MealId,
        val commentId: MealCommentId,
        val text: CommentText,
        val authorId: AccountId,
    ) : PendingCommand {
        override val idempotencyKey: String =
            "comment:${crewId.value}:${mealId.value}:${commentId.value}"
        override val aggregateKey: String =
            "comment:${commentId.value}"
    }

    /** Delete a comment by id. Idempotent (deleting an absent doc is a success). */
    data class DeleteComment(
        val crewId: CrewId,
        val mealId: MealId,
        val commentId: MealCommentId,
    ) : PendingCommand {
        override val idempotencyKey: String =
            "comment.del:${crewId.value}:${mealId.value}:${commentId.value}"
        override val aggregateKey: String =
            "comment:${commentId.value}"
    }

    /**
     * Converge a reaction to an absolute target ([desiredPresent]) rather than a
     * relative flip, so a replay is idempotent regardless of the server's current
     * state. [reactionKindKey] is the persisted [ReactionKind.key]; the handler
     * re-parses it via `ReactionKind.fromKey`.
     */
    data class ToggleReaction(
        val crewId: CrewId,
        val mealId: MealId,
        val reactorId: AccountId,
        val reactionKindKey: String,
        val desiredPresent: Boolean,
    ) : PendingCommand {
        override val idempotencyKey: String =
            "react:${crewId.value}:${mealId.value}:${reactorId.value}"
        override val aggregateKey: String =
            "reaction:${mealId.value}:${reactorId.value}:${reactionKindKey}"
    }

    /** Rename a crew. Idempotent (sets the name). */
    data class RenameCrew(
        val crewId: CrewId,
        val requestedBy: AccountId,
        val newName: String,
    ) : PendingCommand {
        override val idempotencyKey: String = "crew.rename:${crewId.value}"
        override val aggregateKey: String = "crew:${crewId.value}"
    }

    /** Toggle a crew's blind-voting flag. Idempotent (sets the flag). */
    data class SetBlindVoting(
        val crewId: CrewId,
        val requestedBy: AccountId,
        val enabled: Boolean,
    ) : PendingCommand {
        override val idempotencyKey: String = "crew.blind:${crewId.value}"
        override val aggregateKey: String = "crew:${crewId.value}"
    }

    /** Remove a member from a crew. Idempotent (removing an absent member is a success). */
    data class RemoveMember(
        val crewId: CrewId,
        val requestedBy: AccountId,
        val target: AccountId,
    ) : PendingCommand {
        override val idempotencyKey: String =
            "crew.remove:${crewId.value}:${target.value}"
        override val aggregateKey: String = "crew:${crewId.value}"
    }

    /** Leave a crew. Idempotent (leaving a crew you're no longer in is a success). */
    data class LeaveCrew(
        val crewId: CrewId,
        val leaver: AccountId,
    ) : PendingCommand {
        override val idempotencyKey: String =
            "crew.leave:${crewId.value}:${leaver.value}"
        override val aggregateKey: String = "crew:${crewId.value}"
    }
}
