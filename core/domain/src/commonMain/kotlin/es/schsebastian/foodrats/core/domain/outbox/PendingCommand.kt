package es.schsebastian.foodrats.core.domain.outbox

import es.schsebastian.foodrats.core.domain.account.Bio
import es.schsebastian.foodrats.core.domain.account.DisplayName
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
 * Covers the rate / comment / reaction / crew-admin / crew-settings mutations only — the
 * meal-publish queue is a SEPARATE, untouched durable queue ([DraftQueuePort] in
 * `:feature:meal`); there is deliberately NO `PublishMeal` leaf here. Image-byte writes
 * (crew banner image, avatar) are likewise NOT here — bytes can't ride the flattened-column
 * store; they'd need a separate durable byte-queue (mirror [DraftQueuePort]).
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

    /**
     * Edit an already-posted comment's [text]. Idempotent (the server-side edit overwrites the
     * `text` field; a replay re-applies the same text). Shares the `comment:{id}` aggregate with
     * [PostComment]/[DeleteComment] so a post-then-edit on the same comment drains FIFO.
     */
    data class EditComment(
        val crewId: CrewId,
        val mealId: MealId,
        val commentId: MealCommentId,
        val text: CommentText,
    ) : PendingCommand {
        override val idempotencyKey: String =
            "comment.edit:${crewId.value}:${mealId.value}:${commentId.value}"
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

    /**
     * Set or clear ([tagline] == null) the crew tagline. Idempotent (sets the value).
     * Pre-validated by the use case before enqueue.
     */
    data class SetCrewTagline(
        val crewId: CrewId,
        val requestedBy: AccountId,
        val tagline: String?,
    ) : PendingCommand {
        override val idempotencyKey: String = "crew.tagline:${crewId.value}"
        override val aggregateKey: String = "crew:${crewId.value}"
    }

    /**
     * Set or clear ([message] == null) the crew's pinned welcome message. Idempotent.
     * Pre-validated by the use case before enqueue.
     */
    data class SetCrewWelcomeMessage(
        val crewId: CrewId,
        val requestedBy: AccountId,
        val message: String?,
    ) : PendingCommand {
        override val idempotencyKey: String = "crew.welcome:${crewId.value}"
        override val aggregateKey: String = "crew:${crewId.value}"
    }

    /**
     * Set, or clear (both `null`), the crew's weekly challenge + its set-at timestamp. Idempotent.
     * [setAtMillis] is stamped at ENQUEUE time so a deferred replay preserves when the challenge was
     * actually authored (rather than when it finally synced).
     */
    data class SetCrewWeeklyChallenge(
        val crewId: CrewId,
        val requestedBy: AccountId,
        val challenge: String?,
        val setAtMillis: Long?,
    ) : PendingCommand {
        override val idempotencyKey: String = "crew.challenge:${crewId.value}"
        override val aggregateKey: String = "crew:${crewId.value}"
    }

    /**
     * Set the crew's Score display vocabulary (C8). Idempotent. Carries the style as its persisted
     * [es.schsebastian.foodrats.core.domain.crew.CrewScoreStyle.key] string (like [ToggleReaction]'s
     * [reactionKindKey]); the handler re-parses via `CrewScoreStyle.fromKey`, so a style key a newer
     * build wrote degrades gracefully rather than failing to deserialize.
     */
    data class SetCrewScoreStyle(
        val crewId: CrewId,
        val requestedBy: AccountId,
        val styleKey: String,
    ) : PendingCommand {
        override val idempotencyKey: String = "crew.scoreStyle:${crewId.value}"
        override val aggregateKey: String = "crew:${crewId.value}"
    }

    /**
     * Set the crew banner's vertical focal point (C9), pre-clamped to `0f..1f` by the use case.
     * Idempotent (sets the value). The banner IMAGE itself can't be queued here (bytes); only its
     * focal point.
     */
    data class SetCrewBannerFocalY(
        val crewId: CrewId,
        val requestedBy: AccountId,
        val focalY: Float,
    ) : PendingCommand {
        override val idempotencyKey: String = "crew.bannerFocal:${crewId.value}"
        override val aggregateKey: String = "crew:${crewId.value}"
    }

    /**
     * Set the caller's own display name (offline-first). Idempotent (sets the value). Pre-validated
     * by the use case via [DisplayName.of]. Shares the `account:{uid}` aggregate with [SetBio] so a
     * name-then-bio edit drains FIFO. (Avatar BYTES can't ride this flattened-column store — see the
     * class header — so only the text fields are offline-first.)
     */
    data class SetDisplayName(
        val accountId: AccountId,
        val displayName: DisplayName,
    ) : PendingCommand {
        override val idempotencyKey: String = "account.displayName:${accountId.value}"
        override val aggregateKey: String = "account:${accountId.value}"
    }

    /**
     * Set or clear ([bio] == null) the caller's own bio (offline-first). Idempotent. Pre-validated
     * by the use case via [Bio.of]. Shares the `account:{uid}` aggregate with [SetDisplayName].
     */
    data class SetBio(
        val accountId: AccountId,
        val bio: Bio?,
    ) : PendingCommand {
        override val idempotencyKey: String = "account.bio:${accountId.value}"
        override val aggregateKey: String = "account:${accountId.value}"
    }
}
