package es.schsebastian.foodrats.feature.meal.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealDeleteError
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.telemetry.NoopCrashReporter
import es.schsebastian.foodrats.core.domain.time.FixedClock
import es.schsebastian.foodrats.feature.meal.data.firebase.MealAuthorIdentity
import es.schsebastian.foodrats.feature.meal.data.firebase.MealErrorMapper
import es.schsebastian.foodrats.feature.meal.data.firebase.MealFirestore
import es.schsebastian.foodrats.feature.meal.data.local.MealDraftLocalStore
import es.schsebastian.foodrats.feature.meal.data.test.FakeMealAuthorIdentity
import es.schsebastian.foodrats.feature.meal.data.test.FakeMealFirestore
import es.schsebastian.foodrats.feature.meal.data.test.FakePlateStorage
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.model.Plate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Repository-impl tests for the vendor-translation + IO seam the planned Firebase→own-server
 * swap depends on. The repository talks only to the data-layer ports
 * ([MealFirestore]/[PlateStorage]/[MealAuthorIdentity]); the behavioral fakes raise
 * typed-fault `RuntimeException`s so `FirebaseFault`/`MealErrorMapper` classification is
 * verified end-to-end (datasource throws → repo returns the right error leaf).
 */
class FirebaseMealRepositoryTest {

    private val zone = TimeZone.UTC
    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val account = (AccountId.of("acc-1") as Result.Ok).value
    private val dish = (DishName.of("Pizza") as Result.Ok).value
    private val today = MealDay(LocalDate(2026, 5, 18), zone)
    private val mealId = (MealId.of("crew-1_acc-1_2026-05-18_lunch") as Result.Ok).value

    private fun slug(raw: String): IngredientSlug = IngredientSlug.of(raw).getOrNull()!!

    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(mutablePreferencesOf())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val v = transform(state.value); state.value = v; return v
        }
    }

    private val noAccounts = object : AccountReadPort {
        override fun observe(id: AccountId): Flow<Account?> = MutableStateFlow(null)
    }

    private class Fixture {
        val firestore = FakeMealFirestore()
        val storage = FakePlateStorage()
        val identity = FakeMealAuthorIdentity()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun repository(fixture: Fixture): FirebaseMealRepository {
        val testDispatcher = UnconfinedTestDispatcher()
        val dispatchers = object : DispatcherProvider {
            override val main: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val default: CoroutineDispatcher = testDispatcher
        }
        return FirebaseMealRepository(
            firestore = fixture.firestore,
            storage = fixture.storage,
            drafts = MealDraftLocalStore(AppPreferences(FakeDataStore())),
            dispatchers = dispatchers,
            errorMapper = MealErrorMapper(NoopCrashReporter),
            clock = FixedClock(Instant.parse("2026-05-18T12:00:00Z")),
            authorIdentity = fixture.identity,
            zone = zone,
            accountRead = noAccounts,
        )
    }

    private fun draft(
        ingredients: List<IngredientSlug> = emptyList(),
        detectedIngredients: List<IngredientSlug> = emptyList(),
        slot: MealSlot? = MealSlot.Lunch,
        plate: Plate? = Plate(photoBytes = byteArrayOf(1, 2, 3)),
    ) = MealDraft(
        crewId = crew,
        authorId = account,
        day = today,
        plate = plate,
        dish = dish,
        description = Description.EMPTY,
        slot = slot,
        ingredients = ingredients,
        detectedIngredients = detectedIngredients,
        classifierVersion = "food101-v1",
    )

    // ---------------------------------------------------------------------------------
    // publish
    // ---------------------------------------------------------------------------------

    /** #5 invariant: persists ONLY user-confirmed `ingredients`, never the AI detections. */
    @Test fun publish_persists_only_confirmed_ingredients_not_detected() = runTest {
        val f = Fixture()
        val repo = repository(f)

        val result = repo.publish(
            draft(
                ingredients = listOf(slug("egg")),
                detectedIngredients = listOf(slug("egg"), slug("bacon"), slug("cheese")),
            ),
        )

        assertTrue(result is Result.Ok)
        assertEquals(1, f.firestore.writes.size)
        // The written DTO carries exactly the confirmed subset — not the union with detections.
        assertEquals(listOf("egg"), f.firestore.writes.single().dto.ingredients)
    }

    /** #5 edge: AI detected ingredients but the user confirmed none → the meal records none. */
    @Test fun publish_persists_no_ingredients_when_user_confirms_none_despite_detections() = runTest {
        val f = Fixture()
        val repo = repository(f)

        val result = repo.publish(
            draft(ingredients = emptyList(), detectedIngredients = listOf(slug("bacon"), slug("cheese"))),
        )

        assertTrue(result is Result.Ok)
        assertEquals(emptyList(), f.firestore.writes.single().dto.ingredients)
    }

    /** A Storage upload failure maps to the photo-upload error, NOT a generic publish error,
     *  and the meal document is never written. */
    @Test fun publish_storage_failure_maps_to_photo_upload_failed_and_skips_write() = runTest {
        val f = Fixture().apply { storage.uploadFault = RuntimeException("Storage upload failed") }
        val repo = repository(f)

        val result = repo.publish(draft())

        assertEquals(Result.failure(MealError.Publish.PhotoUploadFailed), result)
        assertEquals(0, f.firestore.writes.size)
    }

    /** The already-posted-today uniqueness guard: an ALREADY_EXISTS write fault surfaces as
     *  AlreadyPostedToday (not a generic publish failure). */
    @Test fun publish_already_exists_write_fault_maps_to_already_posted_today() = runTest {
        val f = Fixture().apply { firestore.writeFault = RuntimeException("ALREADY_EXISTS: duplicate meal") }
        val repo = repository(f)

        val result = repo.publish(draft())

        assertEquals(Result.failure(MealError.Publish.AlreadyPostedToday), result)
    }

    /** #20: a write failure AFTER a successful upload best-effort deletes the orphaned blob —
     *  with the uploaded path — and a failing cleanup must NOT change the returned publish error. */
    @Test fun publish_write_failure_after_upload_cleans_up_orphan_blob_and_preserves_error() = runTest {
        val f = Fixture().apply {
            firestore.writeFault = RuntimeException("PERMISSION_DENIED: write rejected after upload")
            // The cleanup itself fails — it still must be attempted and must not mask the error.
            storage.deleteFault = RuntimeException("delete also failed")
        }
        val repo = repository(f)

        val result = repo.publish(draft())

        // The upload landed first, then the write failed → the original publish error is returned.
        assertEquals(1, f.storage.uploads.size)
        assertEquals(Result.failure(MealError.Publish.PublishUnavailable), result)
        // The orphan was cleaned up at the deterministic upload path (crew + meal id).
        assertEquals(crew to mealId.value, f.storage.deletes.single())
    }

    /** A PERMISSION_DENIED write fault is a publish failure, not a read failure (#8 fix). */
    @Test fun publish_permission_denied_write_fault_maps_to_publish_unavailable() = runTest {
        val f = Fixture().apply {
            firestore.writeFault = RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions.")
        }
        val repo = repository(f)

        val result = repo.publish(draft())

        assertEquals(Result.failure(MealError.Publish.PublishUnavailable), result)
    }

    /** No photo on the draft short-circuits before any upload/write. */
    @Test fun publish_without_photo_returns_no_photo_and_uploads_nothing() = runTest {
        val f = Fixture()
        val repo = repository(f)

        val result = repo.publish(draft(plate = null))

        assertEquals(Result.failure(MealError.Validation.NoPhoto), result)
        assertEquals(0, f.storage.uploads.size)
        assertEquals(0, f.firestore.writes.size)
    }

    /** No slot selected short-circuits after the photo check, before upload/write. */
    @Test fun publish_without_slot_returns_no_slot_selected() = runTest {
        val f = Fixture()
        val repo = repository(f)

        val result = repo.publish(draft(slot = null))

        assertEquals(Result.failure(MealError.Publish.NoSlotSelected), result)
        assertEquals(0, f.firestore.writes.size)
    }

    /** The happy path stamps the author identity onto the DTO and writes under the
     *  deterministic day/slot doc id. */
    @Test fun publish_writes_author_identity_and_deterministic_doc_id() = runTest {
        val f = Fixture().apply {
            identity.author = MealAuthorIdentity.Author("acc-1", "Chef Ada", "https://fake/ada.png")
        }
        val repo = repository(f)

        val result = repo.publish(draft())

        assertTrue(result is Result.Ok)
        val write = f.firestore.writes.single()
        assertEquals(mealId.value, write.docId)
        assertEquals("Chef Ada", write.dto.authorName)
        assertEquals("https://fake/ada.png", write.dto.authorAvatarUrl)
    }

    // ---------------------------------------------------------------------------------
    // rate
    // ---------------------------------------------------------------------------------

    /** #11: the explicit domain `raterId` is forwarded to the datasource verbatim. */
    @Test fun rate_forwards_rater_id_to_datasource() = runTest {
        val f = Fixture()
        val repo = repository(f)
        val rater = (AccountId.of("rater-9") as Result.Ok).value
        val score = (Score.of(4) as Result.Ok).value

        val result = repo.rate(crew, mealId, rater, score)

        assertEquals(Result.success(Unit), result)
        val call = f.firestore.rateCalls.single()
        assertEquals("rater-9", call.raterUid)
        assertEquals(4, call.score)
        assertEquals(mealId.value, call.mealId)
    }

    /** #8: a PERMISSION_DENIED fault is an authorization rejection → RateUnavailable,
     *  NOT RatingWindowClosed. */
    @Test fun rate_permission_denied_fault_maps_to_rate_unavailable_not_window_closed() = runTest {
        val f = Fixture().apply {
            firestore.rateFault = RuntimeException("PERMISSION_DENIED: rules rejected the vote")
        }
        val repo = repository(f)
        val rater = (AccountId.of("rater-9") as Result.Ok).value
        val score = (Score.of(3) as Result.Ok).value

        val result = repo.rate(crew, mealId, rater, score)

        assertEquals(Result.failure(RateError.RateUnavailable), result)
    }

    /** The transaction's AlreadyRated outcome maps to RateError.AlreadyRated. */
    @Test fun rate_already_rated_outcome_maps_to_already_rated() = runTest {
        val f = Fixture().apply { firestore.rateOutcome = MealFirestore.RateOutcome.AlreadyRated }
        val repo = repository(f)
        val rater = (AccountId.of("rater-9") as Result.Ok).value
        val score = (Score.of(5) as Result.Ok).value

        val result = repo.rate(crew, mealId, rater, score)

        assertEquals(Result.failure(RateError.AlreadyRated), result)
    }

    /** The transaction's SelfRating outcome maps to RateError.CannotRateOwnMeal. */
    @Test fun rate_self_rating_outcome_maps_to_cannot_rate_own_meal() = runTest {
        val f = Fixture().apply { firestore.rateOutcome = MealFirestore.RateOutcome.SelfRating }
        val repo = repository(f)
        val rater = (AccountId.of("acc-1") as Result.Ok).value
        val score = (Score.of(5) as Result.Ok).value

        val result = repo.rate(crew, mealId, rater, score)

        assertEquals(Result.failure(RateError.CannotRateOwnMeal), result)
    }

    /** No live auth token gates rate before any datasource call (defense in depth). */
    @Test fun rate_without_auth_returns_unauthorized_and_skips_datasource() = runTest {
        val f = Fixture().apply { identity.author = null }
        val repo = repository(f)
        val rater = (AccountId.of("rater-9") as Result.Ok).value
        val score = (Score.of(3) as Result.Ok).value

        val result = repo.rate(crew, mealId, rater, score)

        assertEquals(Result.failure(RateError.Unauthorized), result)
        assertEquals(0, f.firestore.rateCalls.size)
    }

    /** A connectivity fault from the datasource maps to RateError.Offline. */
    @Test fun rate_unavailable_fault_maps_to_offline() = runTest {
        val f = Fixture().apply { firestore.rateFault = RuntimeException("UNAVAILABLE: host unreachable") }
        val repo = repository(f)
        val rater = (AccountId.of("rater-9") as Result.Ok).value
        val score = (Score.of(2) as Result.Ok).value

        val result = repo.rate(crew, mealId, rater, score)

        assertEquals(Result.failure(RateError.Offline), result)
    }

    // ---------------------------------------------------------------------------------
    // delete
    // ---------------------------------------------------------------------------------

    /** Happy path forwards crew + meal id to the datasource. */
    @Test fun delete_forwards_to_datasource_and_succeeds() = runTest {
        val f = Fixture()
        val repo = repository(f)

        val result = repo.delete(crew, mealId)

        assertEquals(Result.success(Unit), result)
        assertEquals(crew to mealId.value, f.firestore.deleteCalls.single())
    }

    /** #8 classification: a PERMISSION_DENIED fault → NotAuthorOrOwner. */
    @Test fun delete_permission_denied_fault_maps_to_not_author_or_owner() = runTest {
        val f = Fixture().apply {
            firestore.deleteFault = RuntimeException("PERMISSION_DENIED: not the author or owner")
        }
        val repo = repository(f)

        val result = repo.delete(crew, mealId)

        assertEquals(Result.failure(MealDeleteError.NotAuthorOrOwner), result)
    }

    /** #8 classification: a NOT_FOUND fault → NotFound. */
    @Test fun delete_not_found_fault_maps_to_not_found() = runTest {
        val f = Fixture().apply { firestore.deleteFault = RuntimeException("NOT-FOUND: no such meal") }
        val repo = repository(f)

        val result = repo.delete(crew, mealId)

        assertEquals(Result.failure(MealDeleteError.NotFound), result)
    }

    /** #8 classification: a connectivity/unknown fault → Unavailable. */
    @Test fun delete_unavailable_fault_maps_to_unavailable() = runTest {
        val f = Fixture().apply { firestore.deleteFault = RuntimeException("UNAVAILABLE: network down") }
        val repo = repository(f)

        val result = repo.delete(crew, mealId)

        assertEquals(Result.failure(MealDeleteError.Unavailable), result)
    }
}
