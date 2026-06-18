package es.schsebastian.foodrats.feature.meal.data.queue

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.model.QueueEntryId
import es.schsebastian.foodrats.feature.meal.domain.model.QueuedDraft
import es.schsebastian.foodrats.feature.meal.domain.model.QueuedDraftStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DraftQueueLocalStoreTest {

    /**
     * Backing store whose read deliberately yields before emitting, opening the
     * read-modify-write interleave window. Without [DraftQueueLocalStore]'s
     * serializing mutex, two concurrent `add`s would both read the same snapshot
     * and the second write would clobber the first (lost update).
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

    private fun draft() = MealDraft(
        audienceCrewIds = setOf((CrewId.of("crew-1") as Result.Ok).value),
        authorId = (AccountId.of("acc-1") as Result.Ok).value,
        day = MealDay(LocalDate(2026, 6, 14), TimeZone.UTC),
        plate = null,
        dish = null,
        description = Description.EMPTY,
    )

    private fun entry(idx: Int) = QueuedDraft(
        id = QueueEntryId("entry-$idx"),
        draft = draft(),
        status = QueuedDraftStatus.Pending,
        attemptCount = 0,
        createdAt = Instant.fromEpochMilliseconds(1_000L + idx),
    )

    @Test
    fun concurrent_adds_do_not_lose_updates() = runTest(StandardTestDispatcher()) {
        val store = DraftQueueLocalStore(AppPreferences(YieldingDataStore()))

        coroutineScope {
            repeat(8) { idx -> launch { store.add(entry(idx)) } }
        }

        val survivors = store.read().map { it.id.value }.toSet()
        assertEquals((0 until 8).map { "entry-$it" }.toSet(), survivors)
    }
}
