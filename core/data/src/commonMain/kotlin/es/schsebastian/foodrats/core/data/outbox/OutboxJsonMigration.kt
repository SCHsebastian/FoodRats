package es.schsebastian.foodrats.core.data.outbox

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.CommentText
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntry
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryId
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryStatus
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One-shot migration of the P2 DataStore-JSON outbox ([Keys.OutboxJson]) into the SQLDelight `outbox`
 * table (P3b-T6). Runs exactly once at app boot (eager `single` in [outboxModule]):
 *
 *  1. read [Keys.OutboxJson]; if absent/blank, do nothing,
 *  2. decode the legacy JSON list (the old [OutboxLocalStore] shape, frozen here),
 *  3. [OutboxLocalStore.add] each surviving entry (coalescing on idempotency key for free), then
 *  4. clear [Keys.OutboxJson] so the migration is idempotent across launches.
 *
 * A malformed/unknown JSON entry is dropped (same tolerance as the P2 store). The single
 * `withContext(dispatchers.io)` here is the IO boundary for this read-decode-write-clear unit;
 * the table writes it issues are pure in-memory delegations to [OutboxLocalStore].
 */
class OutboxJsonMigration(
    private val prefs: AppPreferences,
    private val store: OutboxLocalStore,
    private val dispatchers: DispatcherProvider,
    private val json: Json = Json,
) {

    suspend fun run() = withContext(dispatchers.io) {
        val raw = prefs.observe(Keys.OutboxJson).first()
        if (raw.isNullOrBlank()) return@withContext
        val entries = runCatching { json.decodeFromString<List<OutboxEntryJson>>(raw) }
            .getOrNull()
            ?.mapNotNull { it.toDomain() }
            .orEmpty()
        FrLog.d("Outbox") { "migrating ${entries.size} legacy outbox entry(ies) → SQLDelight table" }
        entries.forEach { store.add(it) }
        prefs.clear(Keys.OutboxJson)
    }

    // ── legacy serialization shape (frozen copy of the P2 OutboxLocalStore DTOs) ────────────────

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
        val kind: String,
        val errorKey: String? = null,
        val retryable: Boolean = false,
    )

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

    private fun OutboxEntryJson.toDomain(): OutboxEntry? {
        val entryId = id.takeIf { it.isNotBlank() }?.let { OutboxEntryId(it) } ?: return null
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
        else -> null
    }
}
