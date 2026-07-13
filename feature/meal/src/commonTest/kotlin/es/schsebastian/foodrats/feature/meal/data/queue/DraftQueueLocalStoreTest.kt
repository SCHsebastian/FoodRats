package es.schsebastian.foodrats.feature.meal.data.queue

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.PlateSource
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

    @Test
    fun legacy_queue_entry_without_plate_source_field_replays_as_camera() = runTest {
        // A durable-queue entry persisted by a pre-marker build: the raw JSON blob has no
        // `plateSource` key at all on the nested draft object. The decoder must fill the null
        // default and the replayed Plate must read back as camera-sourced, never crash/drop.
        val prefs = AppPreferences(YieldingDataStore())
        prefs.set(
            Keys.DraftQueueJson,
            """
            [
              {
                "id": "entry-legacy",
                "draft": {
                  "audienceCrewIds": ["crew-1"],
                  "authorId": "acc-1",
                  "dayIso": "2026-07-13",
                  "zoneId": "UTC",
                  "photoBase64": "AQID"
                },
                "status": {"kind": "pending"},
                "attemptCount": 0,
                "createdAtEpochMs": 1000
              }
            ]
            """.trimIndent(),
        )
        val store = DraftQueueLocalStore(prefs)

        val restored = store.read()

        assertEquals(1, restored.size)
        assertEquals(PlateSource.Camera, restored.single().draft.plate?.source)
    }

    @Test
    fun round_trips_multiple_queued_photos_in_order() = runTest {
        val store = DraftQueueLocalStore(AppPreferences(YieldingDataStore()))
        val multiPhotoDraft = draft().copy(
            plates = listOf(
                es.schsebastian.foodrats.feature.meal.domain.model.Plate(byteArrayOf(1, 2, 3), source = PlateSource.Camera),
                es.schsebastian.foodrats.feature.meal.domain.model.Plate(byteArrayOf(4, 5, 6), source = PlateSource.Gallery),
            ),
        )
        store.add(entry(0).copy(draft = multiPhotoDraft))

        val restored = store.read().single().draft.plates

        assertEquals(2, restored.size)
        assertEquals(listOf(PlateSource.Camera, PlateSource.Gallery), restored.map { it.source })
        assertEquals(listOf<Byte>(1, 2, 3), restored[0].photoBytes.toList())
        assertEquals(listOf<Byte>(4, 5, 6), restored[1].photoBytes.toList())
    }

    @Test
    fun round_trips_ten_queued_photos_in_order() = runTest {
        // Boundary/volume case: the 2-photo round trip above doesn't reach MAX_PHOTOS_PER_MEAL (10)
        // — lock that a FULL queued draft round-trips every entry, in order, with per-entry source.
        val store = DraftQueueLocalStore(AppPreferences(YieldingDataStore()))
        val plates = (1..10).map { i ->
            es.schsebastian.foodrats.feature.meal.domain.model.Plate(
                byteArrayOf(i.toByte()),
                source = if (i % 2 == 0) PlateSource.Gallery else PlateSource.Camera,
            )
        }
        store.add(entry(0).copy(draft = draft().copy(plates = plates)))

        val restored = store.read().single().draft.plates

        assertEquals(10, restored.size)
        plates.indices.forEach { i ->
            assertEquals(plates[i].photoBytes.toList(), restored[i].photoBytes.toList(), "photo bytes at index $i")
            assertEquals(plates[i].source, restored[i].source, "source at index $i")
        }
    }
}
