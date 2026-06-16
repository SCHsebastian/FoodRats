package es.schsebastian.foodrats.feature.meal.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.cuisine.Cuisine
import es.schsebastian.foodrats.core.domain.cuisine.CuisineReadPort
import es.schsebastian.foodrats.core.domain.cuisine.CuisineSlug
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealDeleteError
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealKind
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
import es.schsebastian.foodrats.feature.meal.data.firebase.MealDto
import es.schsebastian.foodrats.feature.meal.data.firebase.MealErrorMapper
import es.schsebastian.foodrats.feature.meal.data.firebase.MealFirestore
import es.schsebastian.foodrats.feature.meal.data.firebase.toDomain
import es.schsebastian.foodrats.feature.meal.data.local.MealDraftLocalStore
import es.schsebastian.foodrats.feature.meal.data.test.FakeImageUrlPort
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

    /** Resolves a fixed dish→cuisine map; [fault] true makes every lookup throw (advisory path). */
    private class FakeCuisineReadPort(
        private val map: Map<String, String> = emptyMap(),
        private val fault: Boolean = false,
    ) : CuisineReadPort {
        override fun observeCatalog(): Flow<Map<CuisineSlug, Cuisine>> = MutableStateFlow(emptyMap())
        override suspend fun loadDishCuisine(dishSlug: String): CuisineSlug? {
            if (fault) throw RuntimeException("cuisine lookup failed")
            return map[dishSlug]?.let { CuisineSlug.of(it).getOrNull() }
        }
    }

    private class Fixture {
        val firestore = FakeMealFirestore()
        val storage = FakePlateStorage()
        val identity = FakeMealAuthorIdentity()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun repository(
        fixture: Fixture,
        cuisineRead: CuisineReadPort = FakeCuisineReadPort(),
    ): FirebaseMealRepository {
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
            imageUrls = FakeImageUrlPort(),
            cuisineRead = cuisineRead,
        )
    }

    private fun draft(
        ingredients: List<IngredientSlug> = emptyList(),
        detectedIngredients: List<IngredientSlug> = emptyList(),
        detectedDishSlug: String? = null,
        slot: MealSlot? = MealSlot.Lunch,
        plate: Plate? = Plate(photoBytes = byteArrayOf(1, 2, 3)),
    ) = MealDraft(
        audienceCrewIds = setOf(crew),
        authorId = account,
        day = today,
        plate = plate,
        dish = dish,
        description = Description.EMPTY,
        slot = slot,
        ingredients = ingredients,
        detectedIngredients = detectedIngredients,
        detectedDishSlug = detectedDishSlug,
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

    /** §2.2 stamp-at-publish: the detected dish resolves to a cuisine slug written onto the meal. */
    @Test fun publish_stamps_cuisine_from_detected_dish() = runTest {
        val f = Fixture()
        val repo = repository(f, FakeCuisineReadPort(map = mapOf("pizza" to "italian")))

        val result = repo.publish(draft(detectedDishSlug = "pizza"))

        assertTrue(result is Result.Ok)
        assertEquals("italian", f.firestore.writes.single().dto.cuisine)
    }

    /** A dish absent from the cuisine map stamps no cuisine — and publish still succeeds. */
    @Test fun publish_leaves_cuisine_null_when_dish_unmapped() = runTest {
        val f = Fixture()
        val repo = repository(f, FakeCuisineReadPort(map = emptyMap()))

        val result = repo.publish(draft(detectedDishSlug = "unknown-dish"))

        assertTrue(result is Result.Ok)
        assertEquals(null, f.firestore.writes.single().dto.cuisine)
    }

    /** Advisory: a cuisine-lookup FAULT never blocks publish — cuisine just stays null. */
    @Test fun publish_succeeds_with_null_cuisine_when_lookup_throws() = runTest {
        val f = Fixture()
        val repo = repository(f, FakeCuisineReadPort(fault = true))

        val result = repo.publish(draft(detectedDishSlug = "pizza"))

        assertTrue(result is Result.Ok)
        assertEquals(1, f.firestore.writes.size)
        assertEquals(null, f.firestore.writes.single().dto.cuisine)
    }

    /** No classified dish on the draft → no lookup, no cuisine. */
    @Test fun publish_leaves_cuisine_null_when_no_dish_detected() = runTest {
        val f = Fixture()
        val repo = repository(f, FakeCuisineReadPort(map = mapOf("pizza" to "italian")))

        val result = repo.publish(draft(detectedDishSlug = null))

        assertTrue(result is Result.Ok)
        assertEquals(null, f.firestore.writes.single().dto.cuisine)
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

    /** The happy path stamps the author identity + plate PATH onto the DTO and writes under
     *  the deterministic day/slot doc id. */
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
        // The plate PATH returned by the upload datasource is persisted (not a URL); author
        // avatar is no longer denormalized onto the meal doc.
        assertEquals(f.storage.url, write.dto.platePath)
    }

    /** Fan-out: a plate published to several crews writes one doc + one image copy PER crew,
     *  each under its own crew path (so the per-crew signed-URL read model stays valid). */
    @Test fun publish_fans_out_one_doc_and_one_image_copy_per_selected_crew() = runTest {
        val crew2 = (CrewId.of("crew-2") as Result.Ok).value
        val f = Fixture()
        val repo = repository(f)

        val result = repo.publish(draft().copy(audienceCrewIds = setOf(crew, crew2)))

        assertTrue(result is Result.Ok)
        assertEquals(2, f.firestore.writes.size)
        assertEquals(2, f.storage.uploads.size)
        assertEquals(setOf(crew.value, crew2.value), f.firestore.writes.map { it.dto.crewId }.toSet())
        assertEquals(setOf(crew, crew2), f.storage.uploads.map { it.crewId }.toSet())
        // Per-crew deterministic doc ids.
        assertEquals(
            setOf("crew-1_acc-1_2026-05-18_lunch", "crew-2_acc-1_2026-05-18_lunch"),
            f.firestore.writes.map { it.docId }.toSet(),
        )
    }

    /** When the slot is already taken in every selected crew, nothing is written and the result
     *  is AlreadyPostedToday (the audience-aware "no crew left to receive it" rule). */
    @Test fun publish_returns_already_posted_when_slot_taken_in_all_selected_crews() = runTest {
        val crew2 = (CrewId.of("crew-2") as Result.Ok).value
        val f = Fixture().apply { firestore.existingSlots = setOf(MealSlot.Lunch) }
        val repo = repository(f)

        val result = repo.publish(draft().copy(audienceCrewIds = setOf(crew, crew2)))

        assertEquals(Result.failure(MealError.Publish.AlreadyPostedToday), result)
        assertEquals(0, f.firestore.writes.size)
        assertEquals(0, f.storage.uploads.size)
    }

    /** An empty audience is rejected before any IO. */
    @Test fun publish_with_no_crew_selected_returns_no_crew_selected() = runTest {
        val f = Fixture()
        val repo = repository(f)

        val result = repo.publish(draft().copy(audienceCrewIds = emptySet()))

        assertEquals(Result.failure(MealError.Publish.NoCrewSelected), result)
        assertEquals(0, f.storage.uploads.size)
        assertEquals(0, f.firestore.writes.size)
    }

    // ---------------------------------------------------------------------------------
    // MealKind seam — behaviorally-inert Solo discriminator threaded end-to-end
    // (spec 2026-06-14-meal-post-types §4 / §6; w4-meal-kind-seam-integration)
    // ---------------------------------------------------------------------------------

    /**
     * §4/§6 round trip: the returned `Meal` from `publish` is `MealKind.Solo`. The repo builds the
     * representative aggregate via `dto.toDomain()` over the DTO it actually writes — so this proves
     * the publish→domain leg, not just the mapper in isolation (which `MealMapperTest` covers).
     */
    @Test fun publish_returns_meal_with_solo_kind() = runTest {
        val f = Fixture()
        val repo = repository(f)

        val result = repo.publish(draft())

        assertTrue(result is Result.Ok)
        assertEquals(MealKind.Solo, result.value.kind)
    }

    /** The write path stamps the `"solo"` discriminator on every published DTO — the only kind
     *  the system writes today (the draft carries no `kind`, spec §4.3). */
    @Test fun publish_stamps_solo_discriminator_on_written_dto() = runTest {
        val f = Fixture()
        val repo = repository(f)

        val result = repo.publish(draft())

        assertTrue(result is Result.Ok)
        assertEquals("solo", f.firestore.writes.single().dto.kind)
    }

    /** End-to-end publish→read round trip: the exact DTO the repo wrote, read back through the
     *  same `toDomain` the feed/stats/detail read paths use, deserializes to `MealKind.Solo`. This
     *  closes the loop `MealMapperTest` only checks at the DTO↔domain boundary. */
    @Test fun published_dto_reads_back_as_solo_kind() = runTest {
        val f = Fixture()
        val repo = repository(f)

        assertTrue(repo.publish(draft()) is Result.Ok)
        val writtenDto = f.firestore.writes.single().dto

        val readBack = writtenDto.toDomain()
        assertTrue(readBack is Result.Ok)
        assertEquals(MealKind.Solo, readBack.value.kind)
    }

    /** Tolerant read (spec §6.2): a legacy/pre-seam doc with NO `kind` field deserializes to Solo.
     *  Pre-launch there is no migration — old docs must read as `Solo` for free. Mirrors the
     *  serialization default `MealDto.kind = "solo"`, asserted here at the repository read seam. */
    @Test fun legacy_doc_without_kind_field_reads_back_as_solo() = runTest {
        // A DTO that predates the seam: the `kind` field was never written, so the deserializer
        // fills the `"solo"` default. Build it omitting `kind` to model exactly that doc shape.
        val legacy = MealDto(
            id = "legacy-1", authorId = "acc-1", authorName = "Sam",
            crewId = "crew-1", dayKey = "2026-05-18", platePath = "crews/crew-1/meals/legacy-1.jpg",
            dishName = "Pizza", publishedAtEpochMs = 1731_000_000_000,
        )
        assertEquals("solo", legacy.kind)

        val readBack = legacy.toDomain()
        assertTrue(readBack is Result.Ok)
        assertEquals(MealKind.Solo, readBack.value.kind)
    }

    /** Fan-out keeps the seam inert across every per-crew copy: each written DTO carries `"solo"`. */
    @Test fun publish_fan_out_stamps_solo_on_every_per_crew_copy() = runTest {
        val crew2 = (CrewId.of("crew-2") as Result.Ok).value
        val f = Fixture()
        val repo = repository(f)

        val result = repo.publish(draft().copy(audienceCrewIds = setOf(crew, crew2)))

        assertTrue(result is Result.Ok)
        assertEquals(2, f.firestore.writes.size)
        assertTrue(f.firestore.writes.all { it.dto.kind == "solo" })
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

    /** Author "delete my post": removes the (day, slot) plate from every crew, at each crew's
     *  own deterministic doc id. */
    @Test fun deleteFromAllCrews_deletes_each_crews_deterministic_doc() = runTest {
        val crew2 = (CrewId.of("crew-2") as Result.Ok).value
        val f = Fixture()
        val repo = repository(f)

        val result = repo.deleteFromAllCrews(setOf(crew, crew2), account, today, MealSlot.Lunch)

        assertEquals(Result.success(Unit), result)
        assertEquals(
            setOf(
                crew to "crew-1_acc-1_2026-05-18_lunch",
                crew2 to "crew-2_acc-1_2026-05-18_lunch",
            ),
            f.firestore.deleteCalls.toSet(),
        )
    }

    /** A best-effort fan-out delete surfaces a transient fault so the UI can offer a retry. */
    @Test fun deleteFromAllCrews_surfaces_unavailable_fault() = runTest {
        val f = Fixture().apply { firestore.deleteFault = RuntimeException("UNAVAILABLE: network down") }
        val repo = repository(f)

        val result = repo.deleteFromAllCrews(setOf(crew), account, today, MealSlot.Lunch)

        assertEquals(Result.failure(MealDeleteError.Unavailable), result)
    }
}
