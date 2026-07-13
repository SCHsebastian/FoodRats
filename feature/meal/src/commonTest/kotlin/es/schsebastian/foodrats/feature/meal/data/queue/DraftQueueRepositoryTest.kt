package es.schsebastian.foodrats.feature.meal.data.queue

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.PlateSource
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.time.FixedClock
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.model.Plate
import es.schsebastian.foodrats.feature.meal.domain.model.QueuedDraftStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class DraftQueueRepositoryTest {

    /** Backing store shared across "process restarts" — a fresh store instance over the SAME data. */
    private class SharedDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(mutablePreferencesOf())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val newValue = transform(state.value)
            state.value = newValue
            return newValue
        }
    }

    private fun dispatchers(): DispatcherProvider {
        val d = UnconfinedTestDispatcher()
        return object : DispatcherProvider {
            override val main: CoroutineDispatcher = d
            override val io: CoroutineDispatcher = d
            override val default: CoroutineDispatcher = d
        }
    }

    private fun draft() = MealDraft(
        audienceCrewIds = setOf(
            (CrewId.of("crew-1") as Result.Ok).value,
            (CrewId.of("crew-2") as Result.Ok).value,
        ),
        authorId = (AccountId.of("acc-1") as Result.Ok).value,
        day = MealDay(LocalDate(2026, 6, 14), TimeZone.UTC),
        plates = listOf(Plate(byteArrayOf(1, 2, 3, 4, 5), overlayApplied = true)),
        dish = null,
        description = Description.EMPTY,
        slot = MealSlot.Lunch,
        ingredients = listOf(IngredientSlug.of("tomato").getOrNull()!!),
    )

    @Test
    fun enqueue_persists_and_survives_a_fresh_store_instance() = runTest {
        val backing = SharedDataStore()
        val clock = FixedClock(Instant.parse("2026-06-14T10:00:00Z"))

        // Session 1: enqueue a draft.
        val repo1 = DraftQueueRepository(DraftQueueLocalStore(AppPreferences(backing)), clock, dispatchers())
        val enqueued = repo1.enqueue(draft())
        assertTrue(enqueued is Result.Ok)
        val entry = enqueued.value

        // Session 2 (process-death proxy): a brand-new store + repo over the SAME backing data.
        val repo2 = DraftQueueRepository(DraftQueueLocalStore(AppPreferences(backing)), clock, dispatchers())
        val restored = repo2.observe().first()
        assertEquals(1, restored.size)
        val r = restored.single()
        assertEquals(entry.id, r.id)
        assertEquals(QueuedDraftStatus.Pending, r.status)
        assertEquals(0, r.attemptCount)
        // The durable plate bytes + audience survive the restart.
        assertEquals(listOf<Byte>(1, 2, 3, 4, 5), r.draft.plate?.photoBytes?.toList())
        assertEquals(true, r.draft.plate?.overlayApplied)
        assertEquals(entry.draft.audienceCrewIds, r.draft.audienceCrewIds)
        assertEquals(MealSlot.Lunch, r.draft.slot)
    }

    @Test
    fun gallery_plate_source_survives_a_fresh_store_instance() = runTest {
        // Provenance is permanent: a gallery-sourced plate queued offline must still stamp
        // "gallery" when the durable queue drains after a process restart.
        val backing = SharedDataStore()
        val clock = FixedClock(Instant.parse("2026-06-14T10:00:00Z"))
        val repo1 = DraftQueueRepository(DraftQueueLocalStore(AppPreferences(backing)), clock, dispatchers())
        val galleryDraft = draft().copy(
            plates = listOf(Plate(byteArrayOf(1, 2, 3, 4, 5), overlayApplied = true, source = PlateSource.Gallery)),
        )
        assertTrue(repo1.enqueue(galleryDraft) is Result.Ok)

        val repo2 = DraftQueueRepository(DraftQueueLocalStore(AppPreferences(backing)), clock, dispatchers())
        val restored = repo2.observe().first().single()
        assertEquals(PlateSource.Gallery, restored.draft.plate?.source)
    }

    @Test
    fun markUploading_then_markFailed_increments_attempt_and_sets_failed() = runTest {
        val backing = SharedDataStore()
        val repo = DraftQueueRepository(
            DraftQueueLocalStore(AppPreferences(backing)),
            FixedClock(Instant.parse("2026-06-14T10:00:00Z")),
            dispatchers(),
        )
        val id = (repo.enqueue(draft()) as Result.Ok).value.id

        repo.markUploading(id)
        assertEquals(QueuedDraftStatus.Uploading, repo.observe().first().single().status)

        repo.markFailed(id, errorKey = "meal.upload.unknown", retryable = true)
        val failed = repo.observe().first().single()
        assertEquals(1, failed.attemptCount)
        assertTrue(failed.status is QueuedDraftStatus.Failed)
        assertEquals("meal.upload.unknown", (failed.status as QueuedDraftStatus.Failed).errorKey)
        assertTrue((failed.status as QueuedDraftStatus.Failed).retryable)
    }

    @Test
    fun remove_dequeues_the_entry() = runTest {
        val backing = SharedDataStore()
        val repo = DraftQueueRepository(
            DraftQueueLocalStore(AppPreferences(backing)),
            FixedClock(Instant.parse("2026-06-14T10:00:00Z")),
            dispatchers(),
        )
        val id = (repo.enqueue(draft()) as Result.Ok).value.id
        repo.remove(id)
        assertTrue(repo.observe().first().isEmpty())
        // No-op-safe on an already-removed id.
        assertTrue(repo.remove(id) is Result.Ok)
    }

    @Test
    fun observe_orders_by_createdAt() = runTest {
        val backing = SharedDataStore()
        val clock = FixedClock(Instant.parse("2026-06-14T10:00:00Z"))
        val repo = DraftQueueRepository(DraftQueueLocalStore(AppPreferences(backing)), clock, dispatchers())
        val first = (repo.enqueue(draft()) as Result.Ok).value
        clock.set(Instant.parse("2026-06-14T11:00:00Z"))
        val second = (repo.enqueue(draft()) as Result.Ok).value

        val ordered = repo.observe().first()
        assertEquals(listOf(first.id, second.id), ordered.map { it.id })
        assertNull(ordered.first().lastAttemptAt)
    }
}
