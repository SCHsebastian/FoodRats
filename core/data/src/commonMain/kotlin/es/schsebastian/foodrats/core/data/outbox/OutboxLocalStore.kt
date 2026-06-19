package es.schsebastian.foodrats.core.data.outbox

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Durable, multi-entry local store for the write outbox (offline-first P2 §1 T2).
 *
 * The direct sibling of `:feature:meal`'s `DraftQueueLocalStore` (kept
 * byte-for-byte untouched): the offline-first write outbox COEXISTS with the
 * meal-publish queue rather than folding into it. This store holds the full list
 * of [OutboxEntry]s — each with its [PendingCommand], lifecycle status, and
 * attempt bookkeeping — as one JSON blob in DataStore Preferences
 * ([Keys.OutboxJson]), the same proven mechanism that survives process death
 * without adding a DB dependency.
 *
 * This is a pure (de)serialization + read/modify/write helper. It holds NO
 * `withContext` — the IO boundary lives in [OutboxRepository], which owns the one
 * `withContext(dispatchers.io)` per public method (CLAUDE.md rule).
 *
 * SERIALIZATION SHAPE. The domain [PendingCommand] is NOT `@Serializable` (vendor/
 * serialization concerns stay out of `:core:domain`). It is persisted here as a
 * FLAT discriminator DTO ([CommandJson], a `type` tag + nullable fields) rather
 * than a `@Serializable sealed` hierarchy, so an unknown discriminator persisted
 * by a newer build degrades to "skip the entry" instead of failing to deserialize
 * the whole list. [toDomain] is null-tolerant: any malformed/partial entry is
 * dropped rather than crashing the queue.
 *
 * A single DataStore `set` is atomic, but the read-modify-write in [mutate] spans
 * a `read()` and a separate `set()`: two concurrent mutations (e.g. an enqueue
 * racing a retry status flip) could both read the same `current` and the later
 * write would clobber the earlier one (lost update). A per-instance [Mutex]
 * serializes the whole read-modify-write so the writes compose instead of racing.
 */
class OutboxLocalStore(
    private val prefs: AppPreferences,
    private val json: Json = Json,
) {

    /** Serializes the read-modify-write in [mutate] against itself. */
    private val mutateLock = Mutex()

    /** Observe the full outbox, ordered by `createdAt`. Empty when nothing queued or unparseable. */
    fun observe(): Flow<List<OutboxEntry>> = prefs.observe(Keys.OutboxJson).map { raw ->
        decode(raw)
    }

    /** Read the current outbox once. */
    suspend fun read(): List<OutboxEntry> = decode(prefs.observe(Keys.OutboxJson).first())

    /**
     * Append [entry] to the outbox, coalescing on the command's idempotency key:
     * any existing entry whose command shares the same [PendingCommand.idempotencyKey]
     * is replaced (last-write-wins), and a same-[OutboxEntryId] duplicate is also
     * replaced. So an offline user rating the same meal twice ends with one entry.
     */
    suspend fun add(entry: OutboxEntry) = mutate { current ->
        val coalesceKey = entry.command.idempotencyKey
        current.filterNot { it.id == entry.id || it.command.idempotencyKey == coalesceKey } + entry
    }

    /** Replace the entry with [OutboxEntry.id] == [id] via [transform]; no-op if absent. */
    suspend fun update(id: OutboxEntryId, transform: (OutboxEntry) -> OutboxEntry) = mutate { current ->
        current.map { if (it.id == id) transform(it) else it }
    }

    /** Remove the entry [id]; no-op if absent. */
    suspend fun remove(id: OutboxEntryId) = mutate { current ->
        current.filterNot { it.id == id }
    }

    /**
     * Atomic read-modify-write of the whole list through one DataStore string key.
     * Held under [mutateLock] so concurrent mutations serialize and compose rather
     * than racing (a later write clobbering an earlier one — lost update).
     */
    private suspend fun mutate(transform: (List<OutboxEntry>) -> List<OutboxEntry>) = mutateLock.withLock {
        val current = read()
        val next = transform(current).sortedBy { it.createdAt }
        prefs.set(Keys.OutboxJson, json.encodeToString(serializer<List<OutboxEntryJson>>(), next.map { it.toJson() }))
    }

    private fun decode(raw: String?): List<OutboxEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<OutboxEntryJson>>(raw) }
            .getOrNull()
            ?.mapNotNull { it.toDomain() }
            ?.sortedBy { it.createdAt }
            ?: emptyList()
    }

    // ── serialization shape ───────────────────────────────────────────────

    @Serializable
    private data class OutboxEntryJson(
        val id: String,
        val command: CommandJson,
        val status: StatusJson,
        val attemptCount: Int,
        val createdAtEpochMs: Long,
        val lastAttemptAtEpochMs: Long? = null,
    )

    @Serializable
    private data class StatusJson(
        val kind: String,                 // "pending" | "uploading" | "failed"
        val errorKey: String? = null,     // failed only
        val retryable: Boolean = false,   // failed only
    )

    /**
     * Flat, discriminated DTO for every [PendingCommand] leaf. The `type` tag
     * selects the leaf; the remaining fields are nullable and only the ones the
     * leaf uses are populated. Keeps the domain command vendor-free and tolerant
     * to an unknown `type` from a newer build (such an entry is dropped on read).
     */
    @Serializable
    private data class CommandJson(
        val type: String,
        val crewId: String? = null,
        val mealId: String? = null,
        val accountId: String? = null,
        val commentId: String? = null,
        val text: String? = null,
        val score: Int? = null,
        val reactionKindKey: String? = null,
        val desiredPresent: Boolean? = null,
        val enabled: Boolean? = null,
        val targetAccountId: String? = null,
        val newName: String? = null,
    ) {
        companion object {
            const val RATE_MEAL = "rate_meal"
            const val POST_COMMENT = "post_comment"
            const val DELETE_COMMENT = "delete_comment"
            const val TOGGLE_REACTION = "toggle_reaction"
            const val RENAME_CREW = "rename_crew"
            const val SET_BLIND_VOTING = "set_blind_voting"
            const val REMOVE_MEMBER = "remove_member"
            const val LEAVE_CREW = "leave_crew"
        }
    }

    private fun OutboxEntry.toJson() = OutboxEntryJson(
        id = id.value,
        command = command.toJson(),
        status = status.toJson(),
        attemptCount = attemptCount,
        createdAtEpochMs = createdAt.toEpochMilliseconds(),
        lastAttemptAtEpochMs = lastAttemptAt?.toEpochMilliseconds(),
    )

    private fun OutboxEntryStatus.toJson() = when (this) {
        OutboxEntryStatus.Pending   -> StatusJson(kind = "pending")
        OutboxEntryStatus.Uploading -> StatusJson(kind = "uploading")
        is OutboxEntryStatus.Failed -> StatusJson(kind = "failed", errorKey = errorKey, retryable = retryable)
    }

    private fun PendingCommand.toJson(): CommandJson = when (this) {
        is PendingCommand.RateMeal -> CommandJson(
            type = CommandJson.RATE_MEAL,
            crewId = crewId.value,
            mealId = mealId.value,
            accountId = raterId.value,
            score = score.value,
        )
        is PendingCommand.PostComment -> CommandJson(
            type = CommandJson.POST_COMMENT,
            crewId = crewId.value,
            mealId = mealId.value,
            commentId = commentId.value,
            text = text.value,
            accountId = authorId.value,
        )
        is PendingCommand.DeleteComment -> CommandJson(
            type = CommandJson.DELETE_COMMENT,
            crewId = crewId.value,
            mealId = mealId.value,
            commentId = commentId.value,
        )
        is PendingCommand.ToggleReaction -> CommandJson(
            type = CommandJson.TOGGLE_REACTION,
            crewId = crewId.value,
            mealId = mealId.value,
            accountId = reactorId.value,
            reactionKindKey = reactionKindKey,
            desiredPresent = desiredPresent,
        )
        is PendingCommand.RenameCrew -> CommandJson(
            type = CommandJson.RENAME_CREW,
            crewId = crewId.value,
            accountId = requestedBy.value,
            newName = newName,
        )
        is PendingCommand.SetBlindVoting -> CommandJson(
            type = CommandJson.SET_BLIND_VOTING,
            crewId = crewId.value,
            accountId = requestedBy.value,
            enabled = enabled,
        )
        is PendingCommand.RemoveMember -> CommandJson(
            type = CommandJson.REMOVE_MEMBER,
            crewId = crewId.value,
            accountId = requestedBy.value,
            targetAccountId = target.value,
        )
        is PendingCommand.LeaveCrew -> CommandJson(
            type = CommandJson.LEAVE_CREW,
            crewId = crewId.value,
            accountId = leaver.value,
        )
    }

    private fun OutboxEntryJson.toDomain(): OutboxEntry? {
        val entryId = runCatching { OutboxEntryId(id) }.getOrNull() ?: return null
        val domainCommand = command.toDomain() ?: return null
        val domainStatus = status.toDomain() ?: return null
        return OutboxEntry(
            id = entryId,
            command = domainCommand,
            status = domainStatus,
            attemptCount = attemptCount,
            createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
            lastAttemptAt = lastAttemptAtEpochMs?.let(Instant::fromEpochMilliseconds),
        )
    }

    private fun StatusJson.toDomain(): OutboxEntryStatus? = when (kind) {
        "pending"   -> OutboxEntryStatus.Pending
        "uploading" -> OutboxEntryStatus.Uploading
        "failed"    -> OutboxEntryStatus.Failed(errorKey = errorKey ?: "outbox.unknown", retryable = retryable)
        else        -> null
    }

    private fun CommandJson.toDomain(): PendingCommand? = when (type) {
        CommandJson.RATE_MEAL -> {
            val crew = crewId.toCrewId() ?: return null
            val meal = mealId.toMealId() ?: return null
            val rater = accountId.toAccountId() ?: return null
            val s = score?.let { Score.of(it).getOrNull() } ?: return null
            PendingCommand.RateMeal(crewId = crew, mealId = meal, raterId = rater, score = s)
        }
        CommandJson.POST_COMMENT -> {
            val crew = crewId.toCrewId() ?: return null
            val meal = mealId.toMealId() ?: return null
            val cId = commentId?.takeIf { it.isNotBlank() }?.let { MealCommentId(it) } ?: return null
            val body = text?.let { CommentText.of(it).getOrNull() } ?: return null
            val author = accountId.toAccountId() ?: return null
            PendingCommand.PostComment(crewId = crew, mealId = meal, commentId = cId, text = body, authorId = author)
        }
        CommandJson.DELETE_COMMENT -> {
            val crew = crewId.toCrewId() ?: return null
            val meal = mealId.toMealId() ?: return null
            val cId = commentId?.takeIf { it.isNotBlank() }?.let { MealCommentId(it) } ?: return null
            PendingCommand.DeleteComment(crewId = crew, mealId = meal, commentId = cId)
        }
        CommandJson.TOGGLE_REACTION -> {
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
                desiredPresent = present,
            )
        }
        CommandJson.RENAME_CREW -> {
            val crew = crewId.toCrewId() ?: return null
            val by = accountId.toAccountId() ?: return null
            val name = newName ?: return null
            PendingCommand.RenameCrew(crewId = crew, requestedBy = by, newName = name)
        }
        CommandJson.SET_BLIND_VOTING -> {
            val crew = crewId.toCrewId() ?: return null
            val by = accountId.toAccountId() ?: return null
            val on = enabled ?: return null
            PendingCommand.SetBlindVoting(crewId = crew, requestedBy = by, enabled = on)
        }
        CommandJson.REMOVE_MEMBER -> {
            val crew = crewId.toCrewId() ?: return null
            val by = accountId.toAccountId() ?: return null
            val tgt = targetAccountId.toAccountId() ?: return null
            PendingCommand.RemoveMember(crewId = crew, requestedBy = by, target = tgt)
        }
        CommandJson.LEAVE_CREW -> {
            val crew = crewId.toCrewId() ?: return null
            val leaver = accountId.toAccountId() ?: return null
            PendingCommand.LeaveCrew(crewId = crew, leaver = leaver)
        }
        else -> null // unknown discriminator from a newer build → drop the entry
    }

    private fun String?.toCrewId(): CrewId? = this?.let { CrewId.of(it).getOrNull() }
    private fun String?.toMealId(): MealId? = this?.let { MealId.of(it).getOrNull() }
    private fun String?.toAccountId(): AccountId? = this?.let { AccountId.of(it).getOrNull() }
}
