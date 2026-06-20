package es.schsebastian.foodrats.core.data.outbox

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import es.schsebastian.foodrats.core.database.FoodRatsDatabase
import es.schsebastian.foodrats.core.database.Outbox as OutboxRow
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.CommentText
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntry
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryId
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryStatus
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.getOrNull
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Durable, multi-entry local store for the write outbox (offline-first P3b §2.5 / P3b-T6).
 *
 * Migrated OFF the DataStore-JSON blob ([Keys.OutboxJson][es.schsebastian.foodrats.core.data.datastore.Keys.OutboxJson])
 * ONTO the SQLDelight `outbox` table (`:core:database`). Each [OutboxEntry] is one row — its
 * [PendingCommand] FLATTENED into the table's nullable payload columns, lifecycle status + attempt
 * bookkeeping in dedicated columns. The public surface is byte-for-byte the same as the P2 store
 * (`observe()` / `read()` / `add()` / `update()` / `remove()`), so [OutboxRepository] and
 * `OutboxRunner` are unchanged. The one-shot migration of any leftover JSON entries lives in
 * [OutboxJsonMigration].
 *
 * The direct sibling of `:feature:meal`'s `DraftQueueLocalStore` (kept untouched): the offline-first
 * write outbox COEXISTS with the meal-publish queue rather than folding into it.
 *
 * IO BOUNDARY. This store holds NO `withContext` — the suspend mutations ([add]/[update]/[remove])
 * run on the caller's context, and [OutboxRepository] owns the one `withContext(dispatchers.io)` per
 * public method (CLAUDE.md rule). The reactive [observe] flow dispatches its query on
 * `dispatchers.io` via `mapToList(io)`, mirroring `MealLocalStore`.
 *
 * COALESCING. Enqueue coalescing on [PendingCommand.idempotencyKey] is enforced by the table's
 * UNIQUE index + `INSERT OR REPLACE` (`upsertByIdem`): an offline user rating the same meal twice
 * ends with one row (last-write-wins). [toDomain] is null-tolerant: a malformed/partial row (e.g. an
 * unknown command `type` persisted by a newer build) is dropped rather than crashing the queue.
 */
class OutboxLocalStore(
    private val database: FoodRatsDatabase,
    private val dispatchers: DispatcherProvider,
) {

    private val queries get() = database.outboxQueries

    /** Observe the full outbox, ordered by `createdAt`. Empty when nothing queued. */
    fun observe(): Flow<List<OutboxEntry>> =
        queries.selectAll()
            .asFlow()
            .mapToList(dispatchers.io)
            .map { rows -> rows.mapNotNull { it.toDomain() } }

    /** Read the current outbox once, ordered by `createdAt`. */
    fun read(): List<OutboxEntry> = queries.selectAll().executeAsList().mapNotNull { it.toDomain() }

    /**
     * Append [entry] to the outbox with M1 identity-preserving coalesce:
     *  - If a row with the same [PendingCommand.idempotencyKey] already exists, only the payload
     *    columns are updated and the lifecycle is reset to Pending — the original `id`, `createdAt`,
     *    and `attemptCount` are preserved (a re-issued command does not reset its retry budget).
     *  - If no such row exists, a fresh row is inserted with `attemptCount = 0`.
     *
     * This replaces the old `INSERT OR REPLACE` (`upsertByIdem`) at the [add] level; `upsertByIdem`
     * is still used for the fresh-insert path.
     */
    fun add(entry: OutboxEntry) {
        val payload = entry.command.toPayload()
        val idem = entry.command.idempotencyKey
        queries.transaction {
            val existing = queries.selectByIdempotencyKey(idem).executeAsOneOrNull()
            if (existing != null) {
                // M1: preserve id, createdAt, attemptCount — only refresh payload + reset lifecycle.
                // attemptCount is intentionally preserved so a re-issued command does not reset its
                // retry budget (M1 spec).
                queries.updatePayloadByIdempotencyKey(
                    type = payload.type,
                    crewId = payload.crewId,
                    mealId = payload.mealId,
                    accountId = payload.accountId,
                    commentId = payload.commentId,
                    text = payload.text,
                    score = payload.score,
                    reactionKindKey = payload.reactionKindKey,
                    desiredPresent = payload.desiredPresent,
                    enabled = payload.enabled,
                    targetAccountId = payload.targetAccountId,
                    newName = payload.newName,
                    idempotencyKey = idem,
                )
            } else {
                val status = entry.status.toColumns()
                queries.upsertByIdem(
                    id = entry.id.value,
                    type = payload.type,
                    idempotencyKey = idem,
                    statusKind = status.kind,
                    errorKey = status.errorKey,
                    retryable = status.retryable,
                    attemptCount = entry.attemptCount.toLong(),
                    createdAtEpochMs = entry.createdAt.toEpochMilliseconds(),
                    lastAttemptAtEpochMs = entry.lastAttemptAt?.toEpochMilliseconds(),
                    crewId = payload.crewId,
                    mealId = payload.mealId,
                    accountId = payload.accountId,
                    commentId = payload.commentId,
                    text = payload.text,
                    score = payload.score,
                    reactionKindKey = payload.reactionKindKey,
                    desiredPresent = payload.desiredPresent,
                    enabled = payload.enabled,
                    targetAccountId = payload.targetAccountId,
                    newName = payload.newName,
                )
            }
        }
    }

    /**
     * Replace the entry with [OutboxEntry.id] == [id] via [transform]; no-op if absent. Only the
     * lifecycle/attempt columns (status / attemptCount / lastAttemptAt) are written back — the
     * command payload never changes after enqueue.
     */
    fun update(id: OutboxEntryId, transform: (OutboxEntry) -> OutboxEntry) {
        // Read-then-write in ONE transaction so a concurrent status flip can't read the same
        // snapshot and clobber the other's write (lost update) — the SQLDelight equivalent of the
        // P2 store's serializing mutex.
        queries.transaction {
            val current = queries.selectAll().executeAsList()
                .firstOrNull { it.id == id.value }
                ?.toDomain()
                ?: return@transaction
            val next = transform(current)
            val status = next.status.toColumns()
            queries.updateStatus(
                statusKind = status.kind,
                errorKey = status.errorKey,
                retryable = status.retryable,
                attemptCount = next.attemptCount.toLong(),
                lastAttemptAtEpochMs = next.lastAttemptAt?.toEpochMilliseconds(),
                id = id.value,
            )
        }
    }

    /** Remove the entry [id]; no-op if absent. */
    fun remove(id: OutboxEntryId) = queries.deleteById(id.value)

    /**
     * User-initiated retry: flip [id] back to Pending and reset `attemptCount` to 0, granting
     * a fresh retry budget. This is the ONLY path that resets `attemptCount` — the automatic
     * backoff re-arm ([update] → status change) must NOT reset it.
     */
    fun requeue(id: OutboxEntryId) = queries.requeueById(id.value)

    /**
     * H1: Conditional claim — atomically transitions the entry from Pending to Uploading.
     * Returns `true` if a row was actually claimed (was Pending and is now Uploading);
     * `false` if the row was already Uploading, Failed, or absent (another drain owns it
     * or it has been removed). Runs as a single transaction with [countChanges] to
     * detect the CAS hit vs. miss without a separate read.
     */
    fun claimForUpload(id: OutboxEntryId, nowMs: Long): Boolean {
        var claimed = false
        queries.transaction {
            queries.claimForUpload(now = nowMs, id = id.value)
            claimed = queries.countChanges().executeAsOne() > 0
        }
        return claimed
    }

    // ── command type discriminators (mirror the P2 CommandJson tags) ───────────

    private object CommandType {
        const val RATE_MEAL = "rate_meal"
        const val POST_COMMENT = "post_comment"
        const val DELETE_COMMENT = "delete_comment"
        const val TOGGLE_REACTION = "toggle_reaction"
        const val RENAME_CREW = "rename_crew"
        const val SET_BLIND_VOTING = "set_blind_voting"
        const val REMOVE_MEMBER = "remove_member"
        const val LEAVE_CREW = "leave_crew"
    }

    // ── domain → columns ──────────────────────────────────────────────────────

    /** Flattened payload of a [PendingCommand] leaf; only the columns the leaf uses are non-null. */
    private class CommandPayload(
        val type: String,
        val crewId: String? = null,
        val mealId: String? = null,
        val accountId: String? = null,
        val commentId: String? = null,
        val text: String? = null,
        val score: Long? = null,
        val reactionKindKey: String? = null,
        val desiredPresent: Long? = null,
        val enabled: Long? = null,
        val targetAccountId: String? = null,
        val newName: String? = null,
    )

    private class StatusColumns(val kind: String, val errorKey: String?, val retryable: Long)

    private fun OutboxEntryStatus.toColumns(): StatusColumns = when (this) {
        OutboxEntryStatus.Pending   -> StatusColumns(kind = "pending", errorKey = null, retryable = 0L)
        OutboxEntryStatus.Uploading -> StatusColumns(kind = "uploading", errorKey = null, retryable = 0L)
        is OutboxEntryStatus.Failed -> StatusColumns(
            kind = "failed",
            errorKey = errorKey,
            retryable = if (retryable) 1L else 0L,
        )
    }

    private fun PendingCommand.toPayload(): CommandPayload = when (this) {
        is PendingCommand.RateMeal -> CommandPayload(
            type = CommandType.RATE_MEAL,
            crewId = crewId.value,
            mealId = mealId.value,
            accountId = raterId.value,
            score = score.value.toLong(),
        )
        is PendingCommand.PostComment -> CommandPayload(
            type = CommandType.POST_COMMENT,
            crewId = crewId.value,
            mealId = mealId.value,
            commentId = commentId.value,
            text = text.value,
            accountId = authorId.value,
        )
        is PendingCommand.DeleteComment -> CommandPayload(
            type = CommandType.DELETE_COMMENT,
            crewId = crewId.value,
            mealId = mealId.value,
            commentId = commentId.value,
        )
        is PendingCommand.ToggleReaction -> CommandPayload(
            type = CommandType.TOGGLE_REACTION,
            crewId = crewId.value,
            mealId = mealId.value,
            accountId = reactorId.value,
            reactionKindKey = reactionKindKey,
            desiredPresent = if (desiredPresent) 1L else 0L,
        )
        is PendingCommand.RenameCrew -> CommandPayload(
            type = CommandType.RENAME_CREW,
            crewId = crewId.value,
            accountId = requestedBy.value,
            newName = newName,
        )
        is PendingCommand.SetBlindVoting -> CommandPayload(
            type = CommandType.SET_BLIND_VOTING,
            crewId = crewId.value,
            accountId = requestedBy.value,
            enabled = if (enabled) 1L else 0L,
        )
        is PendingCommand.RemoveMember -> CommandPayload(
            type = CommandType.REMOVE_MEMBER,
            crewId = crewId.value,
            accountId = requestedBy.value,
            targetAccountId = target.value,
        )
        is PendingCommand.LeaveCrew -> CommandPayload(
            type = CommandType.LEAVE_CREW,
            crewId = crewId.value,
            accountId = leaver.value,
        )
    }

    // ── columns → domain (null-tolerant: a malformed/unknown row is dropped) ────

    private fun OutboxRow.toDomain(): OutboxEntry? {
        val entryId = id.takeIf { it.isNotBlank() }?.let { OutboxEntryId(it) } ?: return null
        val command = toCommand() ?: return null
        val domainStatus = toStatus() ?: return null
        return OutboxEntry(
            id = entryId,
            command = command,
            status = domainStatus,
            attemptCount = attemptCount.toInt(),
            createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
            lastAttemptAt = lastAttemptAtEpochMs?.let(Instant::fromEpochMilliseconds),
        )
    }

    private fun OutboxRow.toStatus(): OutboxEntryStatus? = when (statusKind) {
        "pending"   -> OutboxEntryStatus.Pending
        "uploading" -> OutboxEntryStatus.Uploading
        "failed"    -> OutboxEntryStatus.Failed(
            errorKey = errorKey ?: "outbox.unknown",
            retryable = retryable != 0L,
        )
        else        -> null
    }

    private fun OutboxRow.toCommand(): PendingCommand? = when (type) {
        CommandType.RATE_MEAL -> {
            val crew = crewId.toCrewId() ?: return null
            val meal = mealId.toMealId() ?: return null
            val rater = accountId.toAccountId() ?: return null
            val s = score?.toInt()?.let { Score.of(it).getOrNull() } ?: return null
            PendingCommand.RateMeal(crewId = crew, mealId = meal, raterId = rater, score = s)
        }
        CommandType.POST_COMMENT -> {
            val crew = crewId.toCrewId() ?: return null
            val meal = mealId.toMealId() ?: return null
            val cId = commentId?.takeIf { it.isNotBlank() }?.let { MealCommentId(it) } ?: return null
            val body = text?.let { CommentText.of(it).getOrNull() } ?: return null
            val author = accountId.toAccountId() ?: return null
            PendingCommand.PostComment(crewId = crew, mealId = meal, commentId = cId, text = body, authorId = author)
        }
        CommandType.DELETE_COMMENT -> {
            val crew = crewId.toCrewId() ?: return null
            val meal = mealId.toMealId() ?: return null
            val cId = commentId?.takeIf { it.isNotBlank() }?.let { MealCommentId(it) } ?: return null
            PendingCommand.DeleteComment(crewId = crew, mealId = meal, commentId = cId)
        }
        CommandType.TOGGLE_REACTION -> {
            val crew = crewId.toCrewId() ?: return null
            val meal = mealId.toMealId() ?: return null
            val reactor = accountId.toAccountId() ?: return null
            val kindKey = reactionKindKey?.takeIf { it.isNotBlank() } ?: return null
            val present = desiredPresent ?: return null
            PendingCommand.ToggleReaction(
                crewId = crew,
                mealId = meal,
                reactorId = reactor,
                reactionKindKey = kindKey,
                desiredPresent = present != 0L,
            )
        }
        CommandType.RENAME_CREW -> {
            val crew = crewId.toCrewId() ?: return null
            val by = accountId.toAccountId() ?: return null
            val name = newName ?: return null
            PendingCommand.RenameCrew(crewId = crew, requestedBy = by, newName = name)
        }
        CommandType.SET_BLIND_VOTING -> {
            val crew = crewId.toCrewId() ?: return null
            val by = accountId.toAccountId() ?: return null
            val on = enabled ?: return null
            PendingCommand.SetBlindVoting(crewId = crew, requestedBy = by, enabled = on != 0L)
        }
        CommandType.REMOVE_MEMBER -> {
            val crew = crewId.toCrewId() ?: return null
            val by = accountId.toAccountId() ?: return null
            val tgt = targetAccountId.toAccountId() ?: return null
            PendingCommand.RemoveMember(crewId = crew, requestedBy = by, target = tgt)
        }
        CommandType.LEAVE_CREW -> {
            val crew = crewId.toCrewId() ?: return null
            val leaver = accountId.toAccountId() ?: return null
            PendingCommand.LeaveCrew(crewId = crew, leaver = leaver)
        }
        else -> null // unknown discriminator from a newer build → drop the row
    }

    private fun String?.toCrewId(): CrewId? = this?.let { CrewId.of(it).getOrNull() }
    private fun String?.toMealId(): MealId? = this?.let { MealId.of(it).getOrNull() }
    private fun String?.toAccountId(): AccountId? = this?.let { AccountId.of(it).getOrNull() }
}
