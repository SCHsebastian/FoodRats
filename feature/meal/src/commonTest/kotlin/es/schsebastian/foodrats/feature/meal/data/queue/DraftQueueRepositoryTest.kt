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

        val claimed = repo.markUploading(id)
        assertEquals(Result.Ok(true), claimed, "the entry was Pending, so the CAS claim must succeed")
        assertEquals(QueuedDraftStatus.Uploading, repo.observe().first().single().status)

        repo.markFailed(id, errorKey = "meal.upload.unknown", retryable = true)
        val failed = repo.observe().first().single()
        assertEquals(1, failed.attemptCount)
        assertTrue(failed.status is QueuedDraftStatus.Failed)
        assertEquals("meal.upload.unknown", (failed.status as QueuedDraftStatus.Failed).errorKey)
        assertTrue((failed.status as QueuedDraftStatus.Failed).retryable)
    }

    // ── CAS claim (BUG FIX: orphaned Uploading entries) ───────────────────────

    @Test
    fun markUploading_claims_a_pending_entry_and_persists_a_replacement_draft() = runTest {
        val backing = SharedDataStore()
        val repo = DraftQueueRepository(
            DraftQueueLocalStore(AppPreferences(backing)),
            FixedClock(Instant.parse("2026-06-14T10:00:00Z")),
            dispatchers(),
        )
        val entry = (repo.enqueue(draft()) as Result.Ok).value
        val restamped = entry.draft.copy(day = MealDay(LocalDate(2026, 6, 15), TimeZone.UTC))

        val claimed = repo.markUploading(entry.id, restamped)

        assertEquals(Result.Ok(true), claimed, "a Pending entry must be claimable")
        val row = repo.observe().first().single()
        assertEquals(QueuedDraftStatus.Uploading, row.status)
        assertEquals(MealDay(LocalDate(2026, 6, 15), TimeZone.UTC), row.draft.day, "restamped draft must be persisted by the claim")
    }

    @Test
    fun markUploading_on_an_already_uploading_entry_is_a_cas_miss_and_leaves_it_untouched() = runTest {
        val backing = SharedDataStore()
        val repo = DraftQueueRepository(
            DraftQueueLocalStore(AppPreferences(backing)),
            FixedClock(Instant.parse("2026-06-14T10:00:00Z")),
            dispatchers(),
        )
        val entry = (repo.enqueue(draft()) as Result.Ok).value
        assertEquals(Result.Ok(true), repo.markUploading(entry.id), "first claim succeeds")

        // A second claim attempt (e.g. a concurrent drain) must be denied — the entry is no
        // longer Pending.
        val secondAttempt = repo.markUploading(entry.id, entry.draft.copy(day = MealDay(LocalDate(2026, 6, 20), TimeZone.UTC)))

        assertEquals(Result.Ok(false), secondAttempt, "an Uploading entry must not be re-claimable")
        val row = repo.observe().first().single()
        assertEquals(QueuedDraftStatus.Uploading, row.status)
        assertEquals(entry.draft.day, row.draft.day, "a CAS-missed claim must NOT overwrite the persisted draft")
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

    /** FIFO ordering survives when EVERY queued entry itself carries multiple photos — the single
     *  multi-photo draft cases elsewhere don't exercise several such entries coexisting in the
     *  queue, ordered, each with its own distinct photo count/order intact. */
    @Test
    fun observe_orders_multiple_multi_photo_drafts_by_createdAt_preserving_each_ones_photo_order() = runTest {
        val backing = SharedDataStore()
        val clock = FixedClock(Instant.parse("2026-06-14T10:00:00Z"))
        val repo = DraftQueueRepository(DraftQueueLocalStore(AppPreferences(backing)), clock, dispatchers())

        val draftA = draft().copy(
            plates = listOf(Plate(byteArrayOf(1, 2)), Plate(byteArrayOf(3, 4)), Plate(byteArrayOf(5, 6))),
        )
        val first = (repo.enqueue(draftA) as Result.Ok).value
        clock.set(Instant.parse("2026-06-14T11:00:00Z"))
        val draftB = draft().copy(plates = listOf(Plate(byteArrayOf(7, 8)), Plate(byteArrayOf(9, 10))))
        val second = (repo.enqueue(draftB) as Result.Ok).value
        clock.set(Instant.parse("2026-06-14T12:00:00Z"))
        val draftC = draft().copy(plates = listOf(Plate(byteArrayOf(11, 12))))
        val third = (repo.enqueue(draftC) as Result.Ok).value

        val ordered = repo.observe().first()

        assertEquals(listOf(first.id, second.id, third.id), ordered.map { it.id }, "FIFO order by createdAt")
        assertEquals(3, ordered[0].draft.plates.size)
        assertEquals(2, ordered[1].draft.plates.size)
        assertEquals(1, ordered[2].draft.plates.size)
        assertEquals(listOf<Byte>(1, 2), ordered[0].draft.plates[0].photoBytes.toList())
        assertEquals(listOf<Byte>(5, 6), ordered[0].draft.plates[2].photoBytes.toList())
        assertEquals(listOf<Byte>(9, 10), ordered[1].draft.plates[1].photoBytes.toList())
    }
}
