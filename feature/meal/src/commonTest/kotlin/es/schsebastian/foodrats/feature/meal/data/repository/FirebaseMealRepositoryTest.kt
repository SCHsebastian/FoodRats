package es.schsebastian.foodrats.feature.meal.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.cash.turbine.test
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
import es.schsebastian.foodrats.core.domain.meal.PlateSource
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
import es.schsebastian.foodrats.core.domain.image.ImageUrlPort
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.feature.meal.data.local.LocalMeal
import es.schsebastian.foodrats.feature.meal.data.local.MealLocalStore
import es.schsebastian.foodrats.feature.meal.data.local.MealDraftLocalStore
import es.schsebastian.foodrats.feature.meal.data.test.FakeAccountReadPort
import es.schsebastian.foodrats.feature.meal.data.test.FakeImageUrlPort
import es.schsebastian.foodrats.feature.meal.data.test.FakeMealAuthorIdentity
import es.schsebastian.foodrats.feature.meal.data.test.FakeMealFirestore
import es.schsebastian.foodrats.feature.meal.data.test.FakeMealLocalStore
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
    // The publish path keys the doc id by a stable hash of the plate bytes (the draft below uses
    // byteArrayOf(1,2,3)); compute it the same way so the expected ids track the production formula.
    private val token = byteArrayOf(1, 2, 3).contentHashCode().toUInt().toString(16)
    private val mealId = (MealId.of("crew-1_acc-1_2026-05-18_$token") as Result.Ok).value

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
        local: MealLocalStore = FakeMealLocalStore(),
        accountRead: AccountReadPort = noAccounts,
        imageUrls: ImageUrlPort = FakeImageUrlPort(),
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
            local = local,
            dispatchers = dispatchers,
            errorMapper = MealErrorMapper(NoopCrashReporter),
            clock = FixedClock(Instant.parse("2026-05-18T12:00:00Z")),
            authorIdentity = fixture.identity,
            zone = zone,
            accountRead = accountRead,
            imageUrls = imageUrls,
            cuisineRead = cuisineRead,
        )
    }

    private fun draft(
        ingredients: List<IngredientSlug> = emptyList(),
        detectedIngredients: List<IngredientSlug> = emptyList(),
        detectedDishSlug: String? = null,
        slot: MealSlot? = MealSlot.Lunch,
        plate: Plate? = Plate(photoBytes = byteArrayOf(1, 2, 3)),
        plates: List<Plate> = listOfNotNull(plate),
    ) = MealDraft(
        audienceCrewIds = setOf(crew),
        authorId = account,
        day = today,
        plates = plates,
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

    /** Provenance: the published DTO carries the draft plate's source ("gallery"). */
    @Test fun publish_stamps_plate_source_from_draft_plate() = runTest {
        val f = Fixture()
        val repo = repository(f)

        val result = repo.publish(
            draft(plate = Plate(photoBytes = byteArrayOf(1, 2, 3), source = PlateSource.Gallery)),
        )

        assertTrue(result is Result.Ok)
        assertEquals("gallery", f.firestore.writes.single().dto.plateSource)
        // The representative Meal returned to the caller carries it too.
        assertEquals(PlateSource.Gallery, result.value.plateSource)
    }

    /**
     * firestore.rules (2026-07-19 sweep) rejects meal creates whose `authorName` exceeds 120
     * chars. The provider-supplied displayName is unbounded (Apple Sign-In names are free
     * text), so the publish path must truncate at the wire seam — otherwise one long name
     * permanently rejects every publish for that user.
     */
    @Test fun publish_truncates_over_long_author_name_to_server_cap() = runTest {
        val f = Fixture()
        f.identity.author = MealAuthorIdentity.Author(
            uid = "acc-1",
            displayName = "n".repeat(300),
            avatarUrl = null,
        )
        val repo = repository(f)

        val result = repo.publish(draft())

        assertTrue(result is Result.Ok)
        assertEquals("n".repeat(120), f.firestore.writes.single().dto.authorName)
    }

    /** A camera plate (the default) stamps "camera" — never null — on fresh publishes. */
    @Test fun publish_stamps_camera_plate_source_by_default() = runTest {
        val f = Fixture()
        val repo = repository(f)

        val result = repo.publish(draft())

        assertTrue(result is Result.Ok)
        assertEquals("camera", f.firestore.writes.single().dto.plateSource)
    }

    /** Fan-out: every per-crew copy of a gallery plate carries the marker. */
    @Test fun publish_fan_out_stamps_plate_source_on_every_per_crew_copy() = runTest {
        val crew2 = (CrewId.of("crew-2") as Result.Ok).value
        val f = Fixture()
        val repo = repository(f)

        val result = repo.publish(
            draft(plate = Plate(photoBytes = byteArrayOf(1, 2, 3), source = PlateSource.Gallery))
                .copy(audienceCrewIds = setOf(crew, crew2)),
        )

        assertTrue(result is Result.Ok)
        assertEquals(2, f.firestore.writes.size)
        assertTrue(f.firestore.writes.all { it.dto.plateSource == "gallery" })
    }

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
        // The orphan was cleaned up at the deterministic upload path (crew + meal id + index 0).
        assertEquals(Triple(crew, mealId.value, 0), f.storage.deletes.single())
    }

    /** Regression: the double-fire publish race. A concurrent publish of the SAME draft creates
     *  the doc between this attempt's free-slot pre-check and its write, so the write is rejected
     *  by the `!exists` create rule (PERMISSION_DENIED). The just-overwritten blob now backs a LIVE
     *  meal — it MUST NOT be deleted (the "image uploaded then vanishes" bug), and the outcome is
     *  AlreadyPostedToday, not PublishUnavailable. */
    @Test fun publish_write_rejected_but_doc_now_exists_keeps_plate_and_reports_already_posted() = runTest {
        val f = Fixture().apply {
            firestore.writeFault = RuntimeException("PERMISSION_DENIED: create rule !exists rejected the duplicate")
            // The doc is absent at the pre-check but present once the write is rejected (the race).
            firestore.existingIdsAfterWriteFault = setOf(mealId.value)
        }
        val repo = repository(f)

        val result = repo.publish(draft())

        assertEquals(1, f.storage.uploads.size)
        // The blob backs the live doc — cleanup must be skipped.
        assertEquals(emptyList(), f.storage.deletes)
        assertEquals(Result.failure(MealError.Publish.AlreadyPostedToday), result)
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

    /** Slot is optional: a draft with no slot publishes, persisting an empty slot string. */
    @Test fun publish_without_slot_succeeds_and_persists_empty_slot() = runTest {
        val f = Fixture()
        val repo = repository(f)

        val result = repo.publish(draft(slot = null))

        assertTrue(result is Result.Ok)
        assertEquals("", f.firestore.writes.single().dto.slot)
    }

    /** The happy path stamps the author identity + plate PATH onto the DTO and writes under
     *  the deterministic token doc id. */
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
        // Per-crew deterministic token doc ids.
        assertEquals(
            setOf("crew-1_acc-1_2026-05-18_$token", "crew-2_acc-1_2026-05-18_$token"),
            f.firestore.writes.map { it.docId }.toSet(),
        )
    }

    /** When every selected crew is already at the per-crew daily cap, nothing is written and the
     *  result is AlreadyPostedToday (the audience-aware "no crew left to receive it" rule). */
    @Test fun publish_returns_already_posted_when_all_selected_crews_at_daily_cap() = runTest {
        val crew2 = (CrewId.of("crew-2") as Result.Ok).value
        // 10 existing ids (the cap) for the (crew, day) — the fake reports this for every crew.
        val f = Fixture().apply { firestore.existingIds = (1..10).map { "crew-1_acc-1_2026-05-18_id$it" }.toSet() }
        val repo = repository(f)

        val result = repo.publish(draft().copy(audienceCrewIds = setOf(crew, crew2)))

        assertEquals(Result.failure(MealError.Publish.AlreadyPostedToday), result)
        assertEquals(0, f.firestore.writes.size)
        assertEquals(0, f.storage.uploads.size)
    }

    /** Fan-out where one crew already holds this EXACT logical post (an idempotent retry / double
     *  fire) while another crew has never seen it: the already-holding crew is skipped with NO
     *  upload, while the other crew proceeds normally. The fake's `existingMealIds` isn't keyed by
     *  crew, but the deterministic id itself embeds the crew id, so seeding only crew-1's id
     *  (never crew-2's, which differs) skips exactly the one crew. */
    @Test fun publish_fan_out_skips_crew_already_holding_the_deterministic_id_while_the_other_proceeds() = runTest {
        val crew2 = (CrewId.of("crew-2") as Result.Ok).value
        val f = Fixture().apply { firestore.existingIds = setOf(mealId.value) }
        val repo = repository(f)

        val result = repo.publish(draft().copy(audienceCrewIds = setOf(crew, crew2)))

        assertTrue(result is Result.Ok)
        assertEquals(1, f.firestore.writes.size, "the already-holding crew contributes no write")
        assertEquals(1, f.storage.uploads.size, "the already-holding crew contributes no upload")
        assertEquals(crew2.value, f.firestore.writes.single().dto.crewId)
        assertEquals(crew2, f.storage.uploads.single().crewId)
        assertEquals(crew2, result.value.crewId)
    }

    /** Daily-cap boundary, one below: a single selected crew with 9 existing (non-matching) ids
     *  still has room and the publish succeeds. Paired with the exactly-10 case below. */
    @Test fun publish_single_crew_with_nine_existing_ids_still_has_room_and_succeeds() = runTest {
        val f = Fixture().apply { firestore.existingIds = (1..9).map { "crew-1_acc-1_2026-05-18_other$it" }.toSet() }
        val repo = repository(f)

        val result = repo.publish(draft())

        assertTrue(result is Result.Ok)
        assertEquals(1, f.firestore.writes.size)
    }

    /** Daily-cap boundary, exactly at the cap: a single selected crew with 10 existing
     *  (non-matching) ids is full — AlreadyPostedToday, no IO. Distinct from
     *  `publish_returns_already_posted_when_all_selected_crews_at_daily_cap` (which uses a 2-crew
     *  audience); this isolates the boundary for ONE crew. */
    @Test fun publish_single_crew_at_exactly_ten_existing_ids_is_already_posted_today() = runTest {
        val f = Fixture().apply { firestore.existingIds = (1..10).map { "crew-1_acc-1_2026-05-18_other$it" }.toSet() }
        val repo = repository(f)

        val result = repo.publish(draft())

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
    // multi-photo: idempotency token formula (decision 4)
    // ---------------------------------------------------------------------------------

    /** Single-photo publish keeps the EXACT legacy token formula — ids minted before multi-photo
     *  existed must not shift. (`token`/`mealId` class fields already use that exact formula.) */
    @Test fun publish_single_photo_token_matches_legacy_formula() = runTest {
        val f = Fixture()
        val repo = repository(f)

        val result = repo.publish(draft())

        assertTrue(result is Result.Ok)
        assertEquals(mealId.value, f.firestore.writes.single().docId)
    }

    /** Multi-photo publish uses the ordered fold formula, deterministic across independent
     *  publish attempts of the SAME photo set in the SAME order (a retry re-derives the
     *  identical id, not just "stable within one call"). */
    @Test fun publish_multi_photo_token_is_deterministic_across_calls() = runTest {
        val bytesA = byteArrayOf(1, 2, 3)
        val bytesB = byteArrayOf(4, 5, 6)
        val multiToken = listOf(bytesA, bytesB).fold(17) { acc, b -> 31 * acc + b.contentHashCode() }.toUInt().toString(16)
        val expectedMealId = "crew-1_acc-1_2026-05-18_$multiToken"

        val result1 = repository(Fixture()).publish(draft(plates = listOf(Plate(bytesA), Plate(bytesB))))
        assertTrue(result1 is Result.Ok)
        assertEquals(expectedMealId, result1.value.id.value)

        val f2 = Fixture()
        val result2 = repository(f2).publish(draft(plates = listOf(Plate(bytesA), Plate(bytesB))))
        assertTrue(result2 is Result.Ok)
        assertEquals(expectedMealId, f2.firestore.writes.single().docId)
    }

    /** Reordering the SAME photos changes the token — a reorder is a different logical post, not
     *  an idempotent retry of the same one. */
    @Test fun publish_multi_photo_token_changes_when_photos_are_reordered() = runTest {
        val bytesA = byteArrayOf(1, 2, 3)
        val bytesB = byteArrayOf(4, 5, 6)

        val forward = Fixture()
        val forwardResult = repository(forward).publish(draft(plates = listOf(Plate(bytesA), Plate(bytesB))))
        assertTrue(forwardResult is Result.Ok)

        val reversed = Fixture()
        val reversedResult = repository(reversed).publish(draft(plates = listOf(Plate(bytesB), Plate(bytesA))))
        assertTrue(reversedResult is Result.Ok)

        assertTrue(
            forward.firestore.writes.single().docId != reversed.firestore.writes.single().docId,
            "reordering photos must change the deterministic id (a different logical post)",
        )
    }

    /** Two drafts sharing plates[0] AND plates[1] but differing only at plates[2] must still fold
     *  to a different token — the fold is sensitive to every element, not just the first divergence
     *  point being "detected". */
    @Test fun publish_multi_photo_token_differs_when_only_a_later_photo_changes() = runTest {
        val bytesA = byteArrayOf(1, 2, 3)
        val bytesB = byteArrayOf(4, 5, 6)
        val bytesC1 = byteArrayOf(7, 8, 9)
        val bytesC2 = byteArrayOf(10, 11, 12)

        val first = Fixture()
        val firstResult = repository(first).publish(draft(plates = listOf(Plate(bytesA), Plate(bytesB), Plate(bytesC1))))
        assertTrue(firstResult is Result.Ok)

        val second = Fixture()
        val secondResult = repository(second).publish(draft(plates = listOf(Plate(bytesA), Plate(bytesB), Plate(bytesC2))))
        assertTrue(secondResult is Result.Ok)

        assertTrue(
            first.firestore.writes.single().docId != second.firestore.writes.single().docId,
            "sharing plates[0]/[1] but differing at plates[2] must still produce a different id",
        )
    }

    /** The fold's seed (17) / multiplier (31) will silently Int-overflow-and-wrap for a long enough
     *  varied-byte photo set (Kotlin Int arithmetic never throws on overflow). This proves the
     *  resulting token stays well-formed (`.toUInt().toString(16)` never throws) and — the actually
     *  interesting property — still fully DETERMINISTIC across two independent publish attempts of
     *  the identical extreme-content photo set, exactly like the small-byte-array case above. */
    @Test fun publish_multi_photo_token_stays_deterministic_for_overflow_inducing_byte_content() = runTest {
        val extremePlates = (0 until 10).map { i ->
            Plate(ByteArray(50) { b -> ((i * 97 + b * 131 - 128) and 0xFF).toByte() })
        }

        val first = Fixture()
        val firstResult = repository(first).publish(draft(plates = extremePlates))
        assertTrue(firstResult is Result.Ok)

        val second = Fixture()
        val secondResult = repository(second).publish(draft(plates = extremePlates))
        assertTrue(secondResult is Result.Ok)

        assertEquals(first.firestore.writes.single().docId, second.firestore.writes.single().docId)
    }

    /** Locks the LITERAL legacy hex value for a known byte array — stronger than
     *  `publish_single_photo_token_matches_legacy_formula`, which re-derives the expectation via the
     *  SAME Kotlin expression production uses (so it would silently "pass" even if the fold/formula
     *  drifted, as long as the test constant drifted identically). A hardcoded hex literal catches
     *  an accidental change to the formula that a re-derived constant cannot. */
    @Test fun publish_single_photo_token_hex_matches_the_exact_legacy_formula_for_known_bytes() = runTest {
        val f = Fixture()
        val repo = repository(f)

        val result = repo.publish(draft(plate = Plate(photoBytes = byteArrayOf(1, 2, 3))))

        assertTrue(result is Result.Ok)
        assertEquals("crew-1_acc-1_2026-05-18_7861", f.firestore.writes.single().docId)
    }

    // ---------------------------------------------------------------------------------
    // multi-photo: N uploads, doc write once, orphan cleanup deletes all N on failure
    // ---------------------------------------------------------------------------------

    /** A 3-photo draft uploads exactly 3 objects, in order, at index-aware paths, and writes the
     *  Firestore doc exactly ONCE with all 3 entries mirrored into `plates`. */
    @Test fun publish_multi_photo_uploads_n_objects_and_writes_doc_once() = runTest {
        val f = Fixture()
        val repo = repository(f)
        val plates = listOf(
            Plate(byteArrayOf(1, 2, 3), source = PlateSource.Camera),
            Plate(byteArrayOf(4, 5, 6), source = PlateSource.Gallery),
            Plate(byteArrayOf(7, 8, 9), source = PlateSource.Camera),
        )

        val result = repo.publish(draft(plates = plates))

        assertTrue(result is Result.Ok)
        assertEquals(1, f.firestore.writes.size, "exactly ONE doc write per crew regardless of photo count")
        assertEquals(3, f.storage.uploads.size)
        assertEquals(listOf(0, 1, 2), f.storage.uploads.map { it.index })
        val writtenDto = f.firestore.writes.single().dto
        assertEquals(3, writtenDto.plates.size)
        assertEquals(listOf("camera", "gallery", "camera"), writtenDto.plates.map { it.source })
        // The top-level platePath/plateSource mirror the PRIMARY (plates[0]) exactly.
        assertEquals(writtenDto.plates[0].path, writtenDto.platePath)
        assertEquals("camera", writtenDto.plateSource)
        // The representative Meal's plates round-trip too (paths, pre-enrichment).
        assertEquals(3, result.value.plates.size)
    }

    /** A doc-write failure AFTER all N uploads succeeded best-effort deletes ALL N uploaded
     *  objects — not just the primary. */
    @Test fun publish_multi_photo_write_failure_cleans_up_all_n_uploaded_objects() = runTest {
        val f = Fixture().apply {
            firestore.writeFault = RuntimeException("PERMISSION_DENIED: write rejected after upload")
        }
        val repo = repository(f)
        val plates = listOf(Plate(byteArrayOf(1, 2, 3)), Plate(byteArrayOf(4, 5, 6)), Plate(byteArrayOf(7, 8, 9)))

        val result = repo.publish(draft(plates = plates))

        assertEquals(Result.failure(MealError.Publish.PublishUnavailable), result)
        assertEquals(3, f.storage.uploads.size, "all 3 photos were uploaded before the write failed")
        assertEquals(3, f.storage.deletes.size, "cleanup must delete every uploaded object, not just the primary")
        assertEquals(listOf(0, 1, 2), f.storage.deletes.map { it.third })
    }

    /** R2 regression: a mid-loop UPLOAD failure (plate 3 of 3) never reaches `firestore.write`, so
     *  the doc-write-failure catch above never runs for this crew. Without dedicated cleanup on the
     *  upload path itself, plates 0 and 1 — already uploaded THIS attempt — would leak in Storage
     *  forever once the retry budget exhausts on a photo that fails every attempt. This locks that
     *  the upload-failure branch best-effort deletes exactly what landed before the failure, and
     *  that the original upload error (not a doc-write error) is what publish() returns. */
    @Test fun publish_multi_photo_upload_failure_on_the_last_plate_cleans_up_the_ones_already_uploaded() = runTest {
        val f = Fixture().apply {
            storage.uploadFault = RuntimeException("Storage upload failed")
            storage.uploadFaultAtIndex = 2 // the 3rd of 3 plates
        }
        val repo = repository(f)
        val plates = listOf(Plate(byteArrayOf(1, 2, 3)), Plate(byteArrayOf(4, 5, 6)), Plate(byteArrayOf(7, 8, 9)))

        val result = repo.publish(draft(plates = plates))

        assertEquals(Result.failure(MealError.Publish.PhotoUploadFailed), result, "the ORIGINAL upload error propagates")
        assertEquals(3, f.storage.uploads.size, "the failing 3rd upload is still attempted (and counted) before throwing")
        assertEquals(0, f.firestore.writes.size, "an upload failure must never reach firestore.write")
        assertEquals(2, f.storage.deletes.size, "cleanup deletes exactly the 2 plates that landed before the failure")
        assertEquals(listOf(0, 1), f.storage.deletes.map { it.third })
    }

    /** Mirror of the "last plate" case above at the OTHER end: a mid-batch upload failure on the
     *  VERY FIRST plate has nothing to clean up — `uploaded` is still empty when the failure hits. */
    @Test fun publish_multi_photo_upload_failure_on_the_first_plate_needs_no_cleanup() = runTest {
        val f = Fixture().apply {
            storage.uploadFault = RuntimeException("Storage upload failed")
            storage.uploadFaultAtIndex = 0 // the 1st of 3 plates — nothing has landed yet
        }
        val repo = repository(f)
        val plates = listOf(Plate(byteArrayOf(1, 2, 3)), Plate(byteArrayOf(4, 5, 6)), Plate(byteArrayOf(7, 8, 9)))

        val result = repo.publish(draft(plates = plates))

        assertEquals(Result.failure(MealError.Publish.PhotoUploadFailed), result)
        assertEquals(1, f.storage.uploads.size, "the failing 1st upload is still attempted (and counted) before throwing")
        assertEquals(0, f.firestore.writes.size)
        assertEquals(0, f.storage.deletes.size, "nothing landed before the failure, so there is nothing to clean up")
    }

    /** The 10-photo (cap boundary) analogue of `publish_multi_photo_write_failure_cleans_up_all_n_uploaded_objects`:
     *  a doc-write failure after ALL 10 uploads succeeded best-effort deletes every one of them. */
    @Test fun publish_ten_photo_doc_write_failure_cleans_up_all_ten_uploaded_objects() = runTest {
        val f = Fixture().apply {
            firestore.writeFault = RuntimeException("PERMISSION_DENIED: write rejected after upload")
        }
        val repo = repository(f)
        val plates = (1..10).map { Plate(byteArrayOf(it.toByte())) }

        val result = repo.publish(draft(plates = plates))

        assertEquals(Result.failure(MealError.Publish.PublishUnavailable), result)
        assertEquals(10, f.storage.uploads.size, "all 10 photos were uploaded before the write failed")
        assertEquals(10, f.storage.deletes.size, "cleanup must delete every uploaded object, not just the primary")
        assertEquals((0..9).toList(), f.storage.deletes.map { it.third })
    }

    /** A draft exceeding the photo cap is rejected before any IO — the repository is the hard
     *  backstop even if some upstream layer somehow let a too-large list through. */
    @Test fun publish_with_too_many_photos_returns_too_many_photos_and_uploads_nothing() = runTest {
        val f = Fixture()
        val repo = repository(f)
        val tooMany = (1..11).map { Plate(byteArrayOf(it.toByte())) }

        val result = repo.publish(draft(plates = tooMany))

        assertEquals(Result.failure(MealError.Validation.TooManyPhotos), result)
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

    /** Author "delete my post": removes the plate (identified by its shared token) from every
     *  crew, reconstructing each crew's own deterministic doc id from that token. */
    @Test fun deleteFromAllCrews_deletes_each_crews_deterministic_doc() = runTest {
        val crew2 = (CrewId.of("crew-2") as Result.Ok).value
        val f = Fixture()
        val repo = repository(f)

        val result = repo.deleteFromAllCrews(setOf(crew, crew2), account, today, "tok1")

        assertEquals(Result.success(Unit), result)
        assertEquals(
            setOf(
                crew to "crew-1_acc-1_2026-05-18_tok1",
                crew2 to "crew-2_acc-1_2026-05-18_tok1",
            ),
            f.firestore.deleteCalls.toSet(),
        )
    }

    /** A best-effort fan-out delete surfaces a transient fault so the UI can offer a retry. */
    @Test fun deleteFromAllCrews_surfaces_unavailable_fault() = runTest {
        val f = Fixture().apply { firestore.deleteFault = RuntimeException("UNAVAILABLE: network down") }
        val repo = repository(f)

        val result = repo.deleteFromAllCrews(setOf(crew), account, today, "tok1")

        assertEquals(Result.failure(MealDeleteError.Unavailable), result)
    }

    // ---------------------------------------------------------------------------------
    // Read-path inversion (offline-first P3a-T4): observeFeed/observeRange source the enriched
    // per-crew stream from the LOCAL store (MealLocalStore), not Firestore. The enrichment —
    // signed URLs via ImageUrlPort + live identity via AccountReadPort → toMealWithRatings — must
    // be byte-for-byte the same as before. These tests seed a fake local store and assert the
    // exact enrichment, plus that the feed never reads Firestore.observeForRange.
    // ---------------------------------------------------------------------------------

    private fun localMeal(
        mealId: String,
        dayKey: String,
        authorId: String = "acc-1",
        platePath: String? = "crews/crew-1/meals/$mealId.jpg",
        thumbnailPath: String? = "crews/crew-1/thumbs/$mealId.jpg",
        ratings: List<es.schsebastian.foodrats.feature.meal.data.local.LocalRating> = emptyList(),
        publishedAtEpochMs: Long = 100L,
        /** JSON-encoded `List<PlateEntryDto>` mirror of `MealDto.plates`; `null` (the default)
         *  models a legacy/single-photo row, exactly like the production column default. */
        platesJson: String? = null,
    ) = LocalMeal(
        mealId = mealId,
        crewId = "crew-1",
        authorId = authorId,
        authorName = "Baked Name",
        dayKey = dayKey,
        slot = "lunch",
        platePath = platePath,
        thumbnailPath = thumbnailPath,
        thumbHash = null,
        dishName = "Pizza",
        description = "",
        latitude = null,
        longitude = null,
        publishedAtEpochMs = publishedAtEpochMs,
        ratingSum = ratings.sumOf { it.score }.toLong(),
        voterCount = ratings.size.toLong(),
        ingredientsCsv = "",
        classifierVersion = null,
        cuisine = null,
        kind = "solo",
        platesJson = platesJson,
        pending = 0L,
        idempotencyKey = null,
        ratings = ratings,
    )

    private fun account(id: String, name: String, avatar: String? = null): Account = Account(
        id = (AccountId.of(id) as Result.Ok).value,
        handle = id,
        displayName = name,
        email = null,
        avatarUrl = avatar,
    )

    /** observeFeed reads the local store, filters to the requested day, and mints signed plate +
     *  thumbnail URLs from the stored PATHS (never persisting URLs). */
    @Test fun observeFeed_enriches_local_rows_with_signed_urls_filtered_by_day() = runTest {
        val f = Fixture()
        val local = FakeMealLocalStore(
            listOf(
                localMeal("m1", dayKey = "2026-05-18"),
                // A different day must be filtered out by observeFeed's dayKey predicate.
                localMeal("m2", dayKey = "2026-05-17"),
            ),
        )
        val repo = repository(f, local = local, imageUrls = FakeImageUrlPort())

        repo.observeFeed(crew, today).test {
            val result = expectMostRecentItem()
            assertTrue(result is Result.Ok)
            val meals = result.value
            assertEquals(listOf("m1"), meals.map { it.meal.id.value })
            // PATH → signed URL minted at read time (the store holds only the path).
            assertEquals("signed://crews/crew-1/meals/m1.jpg", meals.single().meal.photoUrl)
            assertEquals("signed://crews/crew-1/thumbs/m1.jpg", meals.single().meal.thumbnailUrl)
            cancelAndIgnoreRemainingEvents()
        }
        // The feed never touched Firestore.observeForRange — the sync engine is its only consumer.
        assertEquals(0, f.firestore.writes.size)
    }

    /** Live identity overrides the row's baked `authorName`: a rename in the accounts doc shows in
     *  the feed without a republish (the enrichment resolves AccountReadPort.observeMany, not the
     *  denormalized snapshot). */
    @Test fun observeFeed_resolves_live_author_identity_over_baked_name() = runTest {
        val f = Fixture()
        val accounts = FakeAccountReadPort().apply {
            set((AccountId.of("acc-1") as Result.Ok).value, account("acc-1", "Renamed Chef", "https://live/ada.png"))
        }
        val local = FakeMealLocalStore(listOf(localMeal("m1", dayKey = "2026-05-18")))
        val repo = repository(f, local = local, accountRead = accounts)

        repo.observeFeed(crew, today).test {
            val result = expectMostRecentItem()
            assertTrue(result is Result.Ok)
            val meal = result.value.single().meal
            // Live identity wins over the stored "Baked Name".
            assertEquals("Renamed Chef", meal.author.displayName)
            assertEquals("https://live/ada.png", meal.author.avatarUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** observeRange over the in-window 30-day range filters the enriched local stream inclusively. */
    @Test fun observeRange_enriches_and_filters_inclusive_range() = runTest {
        val f = Fixture()
        val local = FakeMealLocalStore(
            listOf(
                localMeal("in-lo", dayKey = "2026-04-19"),  // window lower bound (today-29)
                localMeal("in-hi", dayKey = "2026-05-18"),  // today
                localMeal("out", dayKey = "2026-04-18"),    // before the requested range
            ),
        )
        val repo = repository(f, local = local)
        val from = MealDay(LocalDate(2026, 4, 19), zone)

        repo.observeRange(crew, from, today).test {
            val result = expectMostRecentItem()
            assertTrue(result is Result.Ok)
            assertEquals(
                setOf("2026-04-19", "2026-05-18"),
                result.value.map { it.meal.day.toKey() }.toSet(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** 365-day stats range (extends BEFORE the memoized 30-day window) reads the local store DIRECTLY
     *  and surfaces the full retained history — not the silent 30-day cap. */
    @Test fun observeRange_beyond_window_reads_local_directly_for_full_history() = runTest {
        val f = Fixture()
        val local = FakeMealLocalStore(
            listOf(
                // 200 days back — far outside the 30-day sync window, retained locally for stats.
                localMeal("historic", dayKey = "2025-10-30"),
                localMeal("recent", dayKey = "2026-05-18"),
            ),
        )
        val repo = repository(f, local = local)
        val from = MealDay(LocalDate(2025, 5, 18), zone) // today - 365d

        repo.observeRange(crew, from, today).test {
            val result = expectMostRecentItem()
            assertTrue(result is Result.Ok)
            // Both the historic AND recent rows are visible — stats see beyond the 30-day window.
            assertEquals(
                setOf("2025-10-30", "2026-05-18"),
                result.value.map { it.meal.day.toKey() }.toSet(),
            )
            cancelAndIgnoreRemainingEvents()
        }
        // The non-memoized direct read requested exactly the asked-for 365-day range.
        assertTrue(local.rangeCalls.any { it.second == "2025-05-18" && it.third == "2026-05-18" })
    }

    /** A local-read throw degrades to a benign EMPTY feed (the signed-out / PERMISSION_DENIED
     *  contract), not a failure — downstream renders an empty state. */
    @Test fun observeFeed_benign_empty_on_local_read_throw() = runTest {
        val f = Fixture()
        val throwingLocal = object : MealLocalStore() {
            override fun observeRange(crewId: String, fromKey: String, toKey: String): Flow<List<LocalMeal>> =
                kotlinx.coroutines.flow.flow { throw RuntimeException("PERMISSION_DENIED: token revoked") }
        }
        val repo = repository(f, local = throwingLocal)

        repo.observeFeed(crew, today).test {
            val result = expectMostRecentItem()
            assertTrue(result is Result.Ok)
            assertEquals(emptyList<MealWithRatings>(), result.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------------------------------------------------------------------------------
    // enrichedStream / read model: multi-photo plates enrichment edge cases
    // (2026-07-13 hardening pass)
    // ---------------------------------------------------------------------------------

    /** An extra plate whose signed URL fails to resolve (e.g. `mintPlateUrls` didn't return an
     *  entry for that specific path) is DROPPED from `Meal.plates` — never surfaced with a blank
     *  URL — while the primary photo (`photoUrl`, resolved independently) stays intact. */
    @Test fun observeFeed_drops_extra_plate_when_its_signed_url_fails_to_resolve_but_keeps_primary() = runTest {
        val f = Fixture()
        val extraPath = "crews/crew-1/meals/m1_p1.jpg"
        val local = FakeMealLocalStore(
            listOf(
                localMeal(
                    "m1", dayKey = "2026-05-18",
                    platesJson = """[{"path":"crews/crew-1/meals/m1.jpg","source":"camera"},{"path":"$extraPath","source":"gallery"}]""",
                ),
            ),
        )
        val imageUrls = FakeImageUrlPort(missingPaths = setOf(extraPath))
        val repo = repository(f, local = local, imageUrls = imageUrls)

        repo.observeFeed(crew, today).test {
            val result = expectMostRecentItem()
            assertTrue(result is Result.Ok)
            val meal = result.value.single().meal
            assertEquals("signed://crews/crew-1/meals/m1.jpg", meal.photoUrl, "primary must stay intact")
            assertEquals(1, meal.plates.size, "the extra with the missing URL must be dropped")
            assertEquals("signed://crews/crew-1/meals/m1.jpg", meal.plates[0].photoUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** When EVERY extra's signed URL fails to resolve, the meal degrades to exactly the primary
     *  photo — `plates` ends up holding only the (successfully resolved) primary entry. */
    @Test fun observeFeed_degrades_to_primary_only_when_all_extra_urls_fail_to_resolve() = runTest {
        val f = Fixture()
        val extra1 = "crews/crew-1/meals/m1_p1.jpg"
        val extra2 = "crews/crew-1/meals/m1_p2.jpg"
        val local = FakeMealLocalStore(
            listOf(
                localMeal(
                    "m1", dayKey = "2026-05-18",
                    platesJson = """[{"path":"crews/crew-1/meals/m1.jpg","source":"camera"},""" +
                        """{"path":"$extra1","source":"gallery"},{"path":"$extra2","source":"gallery"}]""",
                ),
            ),
        )
        val imageUrls = FakeImageUrlPort(missingPaths = setOf(extra1, extra2))
        val repo = repository(f, local = local, imageUrls = imageUrls)

        repo.observeFeed(crew, today).test {
            val result = expectMostRecentItem()
            assertTrue(result is Result.Ok)
            val meal = result.value.single().meal
            assertEquals("signed://crews/crew-1/meals/m1.jpg", meal.photoUrl)
            assertEquals(
                listOf("signed://crews/crew-1/meals/m1.jpg"),
                meal.plates.map { it.photoUrl },
                "every extra failed to resolve — only the primary survives",
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A legacy row (no `platesJson` at all — the default) yields an EMPTY `Meal.plates`, never a
     *  single-entry list synthesized from `photoUrl` — readers fall back to `photoUrl`/`plateSource`
     *  for the one photo, exactly like the DTO-level `empty_plates_falls_back_to_legacy_single_photo_shape`. */
    @Test fun observeFeed_legacy_row_with_no_plates_json_yields_empty_plates_list() = runTest {
        val f = Fixture()
        val local = FakeMealLocalStore(listOf(localMeal("m1", dayKey = "2026-05-18")))
        val repo = repository(f, local = local)

        repo.observeFeed(crew, today).test {
            val result = expectMostRecentItem()
            assertTrue(result is Result.Ok)
            val meal = result.value.single().meal
            assertTrue(meal.plates.isEmpty(), "a legacy row (no platesJson) must yield an empty plates list")
            assertEquals("signed://crews/crew-1/meals/m1.jpg", meal.photoUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A corrupted/malformed `platesJson` string in a SQLDelight row (e.g. a garbage value from a
     *  partial write or manual corruption) must degrade to the legacy empty-plates shape, NEVER
     *  crash the feed read. Exercises the real (non-faked) `LocalMeal.toMealDto()` ->
     *  `String?.toPlateEntries()` tolerant-decode path end-to-end through `observeFeed`. */
    @Test fun observeFeed_corrupted_plates_json_degrades_to_empty_plates_without_crashing() = runTest {
        val f = Fixture()
        val local = FakeMealLocalStore(
            listOf(localMeal("m1", dayKey = "2026-05-18", platesJson = "{this is not valid json at all")),
        )
        val repo = repository(f, local = local)

        repo.observeFeed(crew, today).test {
            val result = expectMostRecentItem()
            assertTrue(result is Result.Ok, "a corrupted platesJson row must never crash/fail the feed read")
            val meal = result.value.single().meal
            assertTrue(meal.plates.isEmpty(), "malformed JSON degrades to the legacy empty-plates shape")
            assertEquals("signed://crews/crew-1/meals/m1.jpg", meal.photoUrl, "the rest of the row is unaffected")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
