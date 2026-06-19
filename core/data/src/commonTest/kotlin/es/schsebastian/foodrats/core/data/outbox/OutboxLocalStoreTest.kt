package es.schsebastian.foodrats.core.data.outbox

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.domain.meal.CommentText
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.ReactionKind
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntry
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryId
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryStatus
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class OutboxLocalStoreTest {

    /**
     * Backing store whose read deliberately yields before emitting, opening the
     * read-modify-write interleave window. Without [OutboxLocalStore]'s serializing
     * mutex, two concurrent `add`s would both read the same snapshot and the second
     * write would clobber the first (lost update).
     */
    private class YieldingDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(mutablePreferencesOf())
        override val data: Flow<Preferences> = flow {
            yield()
            emit(state.value)
        }
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val newValue = transform(state.value)
            yield()
            state.value = newValue
            return newValue
        }
    }

    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val meal = (MealId.of("meal-1") as Result.Ok).value
    private val acc = (AccountId.of("acc-1") as Result.Ok).value

    private fun rateCommand(rater: String) = PendingCommand.RateMeal(
        crewId = crew,
        mealId = meal,
        raterId = (AccountId.of(rater) as Result.Ok).value,
        score = Score.of(4).getOrNull()!!,
    )

    private fun entry(idx: Int, cmd: PendingCommand) = OutboxEntry(
        id = OutboxEntryId("entry-$idx"),
        command = cmd,
        status = OutboxEntryStatus.Pending,
        attemptCount = 0,
        createdAt = Instant.fromEpochMilliseconds(1_000L + idx),
    )

    @Test
    fun concurrent_adds_with_distinct_keys_do_not_lose_updates() = runTest(StandardTestDispatcher()) {
        val store = OutboxLocalStore(AppPreferences(YieldingDataStore()))

        coroutineScope {
            // Distinct rater per entry → distinct idempotency keys → all 8 survive.
            repeat(8) { idx -> launch { store.add(entry(idx, rateCommand("rater-$idx"))) } }
        }

        val survivors = store.read().map { it.id.value }.toSet()
        assertEquals((0 until 8).map { "entry-$it" }.toSet(), survivors)
    }

    @Test
    fun add_coalesces_on_idempotency_key_last_write_wins() = runTest {
        val store = OutboxLocalStore(AppPreferences(YieldingDataStore()))
        // Same crew/meal/rater → same idempotency key for both commands.
        store.add(entry(1, rateCommand("rater-X")))
        store.add(entry(2, rateCommand("rater-X")))

        val all = store.read()
        assertEquals(1, all.size)
        assertEquals("entry-2", all.single().id.value) // the later add replaced the earlier
    }

    @Test
    fun round_trips_every_command_leaf_through_json() = runTest {
        val store = OutboxLocalStore(AppPreferences(YieldingDataStore()))
        val commands: List<PendingCommand> = listOf(
            rateCommand("rater-1"),
            PendingCommand.PostComment(
                crewId = crew, mealId = meal,
                commentId = MealCommentId("c-1"),
                text = CommentText.of("hello").getOrNull()!!,
                authorId = acc,
            ),
            PendingCommand.DeleteComment(crewId = crew, mealId = meal, commentId = MealCommentId("c-2")),
            PendingCommand.ToggleReaction(
                crewId = crew, mealId = meal, reactorId = acc,
                reactionKindKey = ReactionKind.DailyGlyph.key, desiredPresent = true,
            ),
            PendingCommand.RenameCrew(crewId = crew, requestedBy = acc, newName = "New Crew"),
            PendingCommand.SetBlindVoting(crewId = crew, requestedBy = acc, enabled = true),
            PendingCommand.RemoveMember(
                crewId = crew, requestedBy = acc,
                target = (AccountId.of("acc-2") as Result.Ok).value,
            ),
            PendingCommand.LeaveCrew(crewId = crew, leaver = acc),
        )
        commands.forEachIndexed { idx, cmd -> store.add(entry(idx, cmd)) }

        val restored = store.read().map { it.command }
        assertEquals(commands.toSet(), restored.toSet())
    }

    @Test
    fun status_failed_round_trips_with_error_key_and_retryable() = runTest {
        val store = OutboxLocalStore(AppPreferences(YieldingDataStore()))
        store.add(entry(1, rateCommand("rater-1")))
        store.update(OutboxEntryId("entry-1")) {
            it.copy(status = OutboxEntryStatus.Failed(errorKey = "react.offline", retryable = true), attemptCount = 2)
        }

        val r = store.read().single()
        assertTrue(r.status is OutboxEntryStatus.Failed)
        assertEquals("react.offline", (r.status as OutboxEntryStatus.Failed).errorKey)
        assertTrue((r.status as OutboxEntryStatus.Failed).retryable)
        assertEquals(2, r.attemptCount)
    }

    @Test
    fun unparseable_blob_decodes_to_empty() = runTest {
        val store = OutboxLocalStore(AppPreferences(YieldingDataStore()))
        // Nothing written yet → empty.
        assertTrue(store.read().isEmpty())
        assertTrue(store.observe().first().isEmpty())
    }
}
