package es.schsebastian.foodrats.feature.meal.presentation.compose

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.config.FeatureFlagPort
import es.schsebastian.foodrats.core.domain.preferences.AiPreferenceError
import es.schsebastian.foodrats.core.domain.preferences.AiPreferencePort
import es.schsebastian.foodrats.core.domain.crew.CrewMembershipPort
import es.schsebastian.foodrats.core.domain.crew.CrewSummary
import es.schsebastian.foodrats.core.domain.location.Coordinates
import es.schsebastian.foodrats.core.domain.location.LocationError
import es.schsebastian.foodrats.core.domain.location.LocationProvider
import es.schsebastian.foodrats.core.domain.meal.ClassifierError
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishLabel
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.core.domain.meal.Ingredient
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.meal.MealClassifierPort
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealPublishPolicy
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealUploadCoordinator
import es.schsebastian.foodrats.core.domain.meal.PlateSource
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.presentation.photopicker.PhotoMetadata
import es.schsebastian.foodrats.core.presentation.photopicker.PhotoSource
import es.schsebastian.foodrats.core.presentation.photopicker.PickedPhoto
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.model.Plate
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import es.schsebastian.foodrats.feature.meal.domain.test.FakeMealRepository
import es.schsebastian.foodrats.feature.meal.domain.usecase.ClassifyDraftPlateUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ComposePlateViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val zone = TimeZone.UTC
    private val clock = object : Clock { override fun now() = Instant.parse("2026-05-24T12:00:00Z") }
    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val account = (AccountId.of("acc-1") as Result.Ok).value

    private fun bytes(s: String) = s.encodeToByteArray()

    private fun draftWithPhoto(label: String) = MealDraft(
        audienceCrewIds = setOf(crew),
        authorId = account,
        day = MealDay(LocalDate(2026, 5, 24), zone),
        plates = listOf(Plate(bytes(label))),
        dish = null,
        description = Description.EMPTY,
    )

    private fun draftWithPhotos(vararg photos: Plate) = MealDraft(
        audienceCrewIds = setOf(crew),
        authorId = account,
        day = MealDay(LocalDate(2026, 5, 24), zone),
        plates = photos.toList(),
        dish = null,
        description = Description.EMPTY,
    )

    private fun plate(label: String, source: PlateSource = PlateSource.Camera) = Plate(bytes(label), source = source)

    private suspend fun vmWith(
        repo: FakeMealRepository,
        classifyResult: (ByteArray) -> Result<List<DishLabel>, ClassifierError>,
        dishMap: Map<String, List<String>> = mapOf("pizza" to listOf("tomato", "cheese")),
        mealAiEnabled: Boolean = true,
        crews: List<CrewId> = listOf(crew),
    ): ComposePlateViewModel = ComposePlateViewModel(
        updateDraft = UpdateMealDraftUseCase(repo),
        repository = repo,
        crewMembership = FakeCrewMembership(crews),
        uploadCoordinator = object : MealUploadCoordinator { override fun enqueueDraftUpload() {} },
        locationProvider = object : LocationProvider {
            override suspend fun current(): Result<Coordinates, LocationError> =
                Result.failure(LocationError.Unavailable)
        },
        classifyPlate = ClassifyDraftPlateUseCase(
            FakeClassifier(classifyResult),
            FakeIngredients(dishMap),
            FakeFeatureFlags(mealAiEnabled),
            object : AiPreferencePort {
                override val enabled: Flow<Boolean> = flowOf(true)
                override suspend fun set(enabled: Boolean): Result<Unit, AiPreferenceError> = Result.success(Unit)
            },
        ),
        clock = clock,
        zone = zone,
    )

    @Test fun on_photo_classified_seeds_detected_only_not_confirmed() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhoto("plate")) }
        val vm = vmWith(repo, classifyResult = { Result.success(listOf(DishLabel("pizza", 0.9f))) })

        vm.onPhotoCaptured(bytes("plate"))

        vm.state.test {
            val st = expectMostRecentItem()
            assertFalse(st.classifying)
            assertEquals(listOf(IngredientSlug.of("tomato").getOrNull()!!, IngredientSlug.of("cheese").getOrNull()!!), st.detectedIngredients)
            // Detected ≠ confirmed: classification must NOT populate the confirmed list.
            assertEquals(emptyList(), st.draftIngredients)
        }
        // SetDetected stamps the detected set only; the user-confirmed `ingredients` stays empty.
        val draft = repo.observeDraft().first()!!
        assertEquals(listOf(IngredientSlug.of("tomato").getOrNull()!!, IngredientSlug.of("cheese").getOrNull()!!), draft.detectedIngredients)
        assertEquals(emptyList(), draft.ingredients)
    }

    @Test fun killswitch_off_skips_classification_no_detections_no_error() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhoto("plate")) }
        var classifierCalled = false
        val vm = vmWith(
            repo,
            classifyResult = { classifierCalled = true; Result.success(listOf(DishLabel("pizza", 0.9f))) },
            mealAiEnabled = false,
        )

        vm.onPhotoCaptured(bytes("plate"))

        vm.state.test {
            val st = expectMostRecentItem()
            assertFalse(st.classifying)
            // Kill-switch on: no detections surfaced and NO error/banner (advisory feature).
            assertEquals(emptyList(), st.detectedIngredients)
            assertEquals(null, st.classifierError)
        }
        assertFalse(classifierCalled, "kill-switch off must never invoke the on-device classifier")
        val draft = repo.observeDraft().first()!!
        assertEquals(emptyList(), draft.detectedIngredients)
        assertEquals(emptyList(), draft.ingredients)
    }

    @Test fun classifier_failure_surfaces_error_and_keeps_canContinue() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhoto("plate")) }
        val vm = vmWith(repo, classifyResult = { Result.failure(ClassifierError.Run.InferenceFailed) })
        // Make the form valid so canContinue is true before classification fails.
        vm.onIntent(ComposePlateIntent.DishChanged("Pizza"))

        vm.onPhotoCaptured(bytes("plate"))

        vm.state.test {
            val st = expectMostRecentItem()
            assertFalse(st.classifying)
            assertEquals(ClassifierError.Run.InferenceFailed, st.classifierError)
            assertTrue(st.canContinue, "classification is advisory — must not block publishing")
        }
    }

    @Test fun dish_too_long_blocks_continue_and_shows_too_long_message() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhoto("plate")) }
        val vm = vmWith(repo, classifyResult = { Result.success(listOf(DishLabel("pizza", 0.9f))) })

        vm.onIntent(ComposePlateIntent.DishChanged("x".repeat(DishName.MAX_LEN + 1)))

        vm.state.test {
            val st = expectMostRecentItem()
            assertTrue(st.dishTooLong)
            // The RIGHT message: "Keep the dish name short." (TooLong), not the blank "Tell us what you ate."
            assertEquals(MealError.Validation.TooLong, st.error)
            assertFalse(st.canContinue, "an over-length dish must block Continue")
        }
    }

    @Test fun valid_dish_clears_too_long_message() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhoto("plate")) }
        val vm = vmWith(repo, classifyResult = { Result.success(listOf(DishLabel("pizza", 0.9f))) })

        vm.onIntent(ComposePlateIntent.DishChanged("x".repeat(DishName.MAX_LEN + 1)))
        vm.onIntent(ComposePlateIntent.DishChanged("Pizza"))

        vm.state.test {
            val st = expectMostRecentItem()
            assertFalse(st.dishTooLong)
            assertEquals(null, st.error)
            assertTrue(st.canContinue, "a valid dish + photo + crew should allow Continue")
        }
    }

    @Test fun over_length_dish_on_confirm_maps_to_too_long_not_blank() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhoto("plate")) }
        val vm = vmWith(repo, classifyResult = { Result.success(listOf(DishLabel("pizza", 0.9f))) })

        vm.onIntent(ComposePlateIntent.DishChanged("x".repeat(DishName.MAX_LEN + 1)))
        vm.onIntent(ComposePlateIntent.RequestConfirm)

        vm.state.test {
            val st = expectMostRecentItem()
            // The submit path (persistDraft → DishName.of) must distinguish too-long from blank.
            assertEquals(MealError.Validation.TooLong, st.error)
            assertFalse(st.showConfirm, "a too-long dish must not open the publish confirm dialog")
        }
    }

    @Test fun re_capture_overwrites_manual_edits() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhoto("plate-1")) }
        val vm = vmWith(
            repo,
            classifyResult = { jpeg ->
                when (jpeg.decodeToString()) {
                    "plate-1" -> Result.success(listOf(DishLabel("pizza", 0.9f)))
                    else -> Result.success(listOf(DishLabel("salad", 0.9f)))
                }
            },
            dishMap = mapOf("pizza" to listOf("tomato"), "salad" to listOf("lettuce", "olive")),
        )

        vm.onPhotoCaptured(bytes("plate-1"))
        // User trims the selection in the picker (writes through the draft port).
        repo.setIngredients(listOf(IngredientSlug.of("only-one").getOrNull()!!))
        // Re-capture a different plate.
        vm.onPhotoCaptured(bytes("plate-2"))

        vm.state.test {
            val st = expectMostRecentItem()
            // Re-capture overwrites the detected seed...
            assertEquals(listOf(IngredientSlug.of("lettuce").getOrNull()!!, IngredientSlug.of("olive").getOrNull()!!), st.detectedIngredients)
            // ...but the user's confirmed selection is left untouched (detected ≠ confirmed).
            assertEquals(listOf(IngredientSlug.of("only-one").getOrNull()!!), st.draftIngredients)
        }
    }

    @Test fun seeded_audience_subset_is_not_clobbered_to_all_crews() = runTest {
        // Regression: the composer is opened with the draft pre-seeded to ONE crew (the active
        // crew the user launched from), while they belong to three. loadCrewsAndCounts must keep
        // that subset, not reconcile it up to "all crews" — the bug was reading the transient empty
        // initial selection (observeDraft vs observeMyCrews race) and defaulting to all.
        val crew2 = (CrewId.of("crew-2") as Result.Ok).value
        val crew3 = (CrewId.of("crew-3") as Result.Ok).value
        val seeded = draftWithPhoto("plate").copy(audienceCrewIds = setOf(crew))
        val repo = FakeMealRepository().apply { saveDraft(seeded) }
        val vm = vmWith(repo, classifyResult = { Result.failure(ClassifierError.Run.InferenceFailed) }, crews = listOf(crew, crew2, crew3))

        vm.state.test {
            assertEquals(setOf(crew), expectMostRecentItem().selectedCrewIds)
        }
        // And the persisted draft audience stays the single seeded crew.
        assertEquals(setOf(crew), repo.observeDraft().first()!!.audienceCrewIds)
    }

    @Test fun seeded_audience_drops_a_left_crew_but_keeps_the_rest() = runTest {
        // The reconcile still does its job: a seeded crew the user is no longer a member of is
        // dropped from the audience (here crew3 is gone), without inflating to all crews.
        val crew2 = (CrewId.of("crew-2") as Result.Ok).value
        val crew3 = (CrewId.of("crew-3") as Result.Ok).value
        val seeded = draftWithPhoto("plate").copy(audienceCrewIds = setOf(crew, crew3))
        val repo = FakeMealRepository().apply { saveDraft(seeded) }
        val vm = vmWith(repo, classifyResult = { Result.failure(ClassifierError.Run.InferenceFailed) }, crews = listOf(crew, crew2))

        vm.state.test {
            assertEquals(setOf(crew), expectMostRecentItem().selectedCrewIds)
        }
        assertEquals(setOf(crew), repo.observeDraft().first()!!.audienceCrewIds)
    }

    // ── multi-photo strip: AddPhotos / RemovePhotoAt / MovePhoto / SelectPhoto ────────────

    @Test fun add_photos_trims_to_remaining_capacity_and_surfaces_the_cap_notice() = runTest {
        val existing = (1..MealPublishPolicy.MAX_PHOTOS_PER_MEAL - 2).map { plate("existing-$it") }
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhotos(*existing.toTypedArray())) }
        val vm = vmWith(repo, classifyResult = { Result.success(emptyList()) })

        // Only 2 slots remain; offer 5 — 3 must be trimmed, in order, from the tail.
        vm.onIntent(
            ComposePlateIntent.AddPhotos(
                (1..5).map { PickedPhoto(bytes("new-$it"), PhotoSource.Camera) },
            ),
        )

        val draft = repo.observeDraft().first()!!
        assertEquals(MealPublishPolicy.MAX_PHOTOS_PER_MEAL, draft.plates.size)
        assertEquals("new-1", draft.plates[draft.plates.size - 2].photoBytes.decodeToString())
        assertEquals("new-2", draft.plates[draft.plates.size - 1].photoBytes.decodeToString())
        vm.state.test {
            assertEquals(MealError.Validation.TooManyPhotos, expectMostRecentItem().error)
        }
    }

    @Test fun add_photos_under_cap_clears_a_previous_cap_notice() = runTest {
        val existing = (1..MealPublishPolicy.MAX_PHOTOS_PER_MEAL - 1).map { plate("existing-$it") }
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhotos(*existing.toTypedArray())) }
        val vm = vmWith(repo, classifyResult = { Result.success(emptyList()) })
        // First add hits the cap (1 slot, 2 offered) and sets the notice.
        vm.onIntent(ComposePlateIntent.AddPhotos(listOf(PickedPhoto(bytes("a"), PhotoSource.Camera), PickedPhoto(bytes("b"), PhotoSource.Camera))))
        vm.state.test { assertEquals(MealError.Validation.TooManyPhotos, expectMostRecentItem().error) }
        // Free a slot, then add exactly one more — this add is NOT trimmed, so the stale notice clears.
        vm.onIntent(ComposePlateIntent.RemovePhotoAt(0))
        vm.onIntent(ComposePlateIntent.AddPhotos(listOf(PickedPhoto(bytes("c"), PhotoSource.Camera))))

        vm.state.test { assertNull(expectMostRecentItem().error) }
    }

    @Test fun add_photos_with_empty_list_is_a_noop() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhotos()) }
        val vm = vmWith(repo, classifyResult = { Result.success(emptyList()) })

        vm.onIntent(ComposePlateIntent.AddPhotos(emptyList()))

        assertTrue(repo.observeDraft().first()!!.plates.isEmpty())
        vm.state.test { assertNull(expectMostRecentItem().error) }
    }

    @Test fun add_photos_reentry_while_a_batch_is_in_flight_drops_the_second_call_without_losing_the_first() = runTest {
        // Regression for R1: two overlapping AddPhotos intents each run in their own coroutine
        // (MviViewModel.onIntent). Before the isAddingPhotos guard, a second call arriving while the
        // first batch's per-photo loop was still persisting would read the same stale draft and its
        // saveDraft would clobber the first batch's still-pending photo — silent data loss, no error.
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhotos()) }
        val gate = CompletableDeferred<Unit>()
        // Suspend only the write that lands the batch's SECOND photo (plates.size == 2) — the exact
        // point where the original interleaving happened — so a second AddPhotos can be dispatched
        // mid-batch: after the first photo has landed but before the second has.
        val gatedRepo = object : MealRepository by repo {
            override suspend fun saveDraft(draft: MealDraft): Result<Unit, MealError> {
                if (draft.plates.size == 2) gate.await()
                return repo.saveDraft(draft)
            }
        }
        val vm = ComposePlateViewModel(
            updateDraft = UpdateMealDraftUseCase(gatedRepo),
            repository = gatedRepo,
            crewMembership = FakeCrewMembership(crew),
            uploadCoordinator = object : MealUploadCoordinator { override fun enqueueDraftUpload() {} },
            locationProvider = object : LocationProvider {
                override suspend fun current(): Result<Coordinates, LocationError> =
                    Result.failure(LocationError.Unavailable)
            },
            classifyPlate = ClassifyDraftPlateUseCase(
                FakeClassifier { Result.success(emptyList()) },
                FakeIngredients(emptyMap()),
                FakeFeatureFlags(true),
                object : AiPreferencePort {
                    override val enabled: Flow<Boolean> = flowOf(true)
                    override suspend fun set(enabled: Boolean): Result<Unit, AiPreferenceError> = Result.success(Unit)
                },
            ),
            clock = clock,
            zone = zone,
        )

        // First batch: two photos. The loop's second AddPhoto suspends on the gate mid-batch.
        vm.onIntent(
            ComposePlateIntent.AddPhotos(
                listOf(PickedPhoto(bytes("p1"), PhotoSource.Camera), PickedPhoto(bytes("p2"), PhotoSource.Camera)),
            ),
        )
        assertTrue(vm.state.value.isAddingPhotos)
        assertEquals(listOf("p1"), gatedRepo.observeDraft().first()!!.plates.map { it.photoBytes.decodeToString() })

        // Re-entry while the first batch is still in flight must be a no-op — it must NOT interleave
        // its own read-then-write on top of the first batch's still-pending second photo.
        vm.onIntent(ComposePlateIntent.AddPhotos(listOf(PickedPhoto(bytes("p3"), PhotoSource.Camera))))
        assertEquals(listOf("p1"), gatedRepo.observeDraft().first()!!.plates.map { it.photoBytes.decodeToString() })

        gate.complete(Unit)

        // Both of the FIRST batch's photos landed, in order; the re-entrant call's photo never did.
        assertEquals(listOf("p1", "p2"), repo.observeDraft().first()!!.plates.map { it.photoBytes.decodeToString() })
        assertFalse(vm.state.value.isAddingPhotos)
    }

    @Test fun move_photo_reorders_the_draft_and_selection_follows_the_moved_photo() = runTest {
        val repo = FakeMealRepository().apply {
            saveDraft(draftWithPhotos(plate("A"), plate("B"), plate("C")))
        }
        val vm = vmWith(repo, classifyResult = { Result.success(emptyList()) })

        vm.onIntent(ComposePlateIntent.MovePhoto(0, 1))

        val draft = repo.observeDraft().first()!!
        assertEquals(listOf("B", "A", "C"), draft.plates.map { it.photoBytes.decodeToString() })
        vm.state.test {
            val st = expectMostRecentItem()
            // Selection follows the moved photo (A) to its new slot, not the slot it vacated.
            assertEquals(1, st.selectedIndex)
            assertEquals("A", st.selectedPhoto?.photoBytes?.decodeToString())
        }
    }

    @Test fun move_photo_with_out_of_bounds_index_is_a_noop_and_does_not_disturb_selection() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhotos(plate("A"), plate("B"))) }
        val vm = vmWith(repo, classifyResult = { Result.success(emptyList()) })

        vm.onIntent(ComposePlateIntent.MovePhoto(0, 5))

        assertEquals(listOf("A", "B"), repo.observeDraft().first()!!.plates.map { it.photoBytes.decodeToString() })
        vm.state.test { assertEquals(0, expectMostRecentItem().selectedIndex) }
    }

    @Test fun remove_photo_at_removes_the_selected_tile_and_selects_the_new_last_photo() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhotos(plate("A"), plate("B"), plate("C"))) }
        val vm = vmWith(repo, classifyResult = { Result.success(emptyList()) })
        vm.onIntent(ComposePlateIntent.SelectPhoto(2)) // select C, the last photo

        vm.onIntent(ComposePlateIntent.RemovePhotoAt(2))

        val draft = repo.observeDraft().first()!!
        assertEquals(listOf("A", "B"), draft.plates.map { it.photoBytes.decodeToString() })
        vm.state.test {
            val st = expectMostRecentItem()
            // C is gone; the selection clamps to the new last photo (B) — a sane neighbor, not a crash.
            assertEquals(1, st.selectedIndex)
            assertEquals("B", st.selectedPhoto?.photoBytes?.decodeToString())
        }
    }

    @Test fun remove_photo_at_before_the_selection_shifts_the_selection_to_keep_pointing_at_the_same_photo() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhotos(plate("A"), plate("B"), plate("C"))) }
        val vm = vmWith(repo, classifyResult = { Result.success(emptyList()) })
        vm.onIntent(ComposePlateIntent.SelectPhoto(2)) // select C

        vm.onIntent(ComposePlateIntent.RemovePhotoAt(0)) // remove A, which sits BEFORE the selection

        vm.state.test {
            val st = expectMostRecentItem()
            // The index shifts down by one so the selection keeps pointing at C itself, not whatever
            // photo happens to land in the numeric slot 2 used to occupy.
            assertEquals(1, st.selectedIndex)
            assertEquals("C", st.selectedPhoto?.photoBytes?.decodeToString())
        }
    }

    @Test fun select_photo_out_of_bounds_is_a_noop() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhotos(plate("A"), plate("B"))) }
        val vm = vmWith(repo, classifyResult = { Result.success(emptyList()) })

        vm.onIntent(ComposePlateIntent.SelectPhoto(5))

        vm.state.test { assertEquals(0, expectMostRecentItem().selectedIndex) }
    }

    @Test fun select_photo_within_bounds_updates_the_selection() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhotos(plate("A"), plate("B"))) }
        val vm = vmWith(repo, classifyResult = { Result.success(emptyList()) })

        vm.onIntent(ComposePlateIntent.SelectPhoto(1))

        vm.state.test { assertEquals(1, expectMostRecentItem().selectedIndex) }
    }

    @Test fun selected_index_clamps_when_the_photo_list_shrinks_out_from_under_it() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhotos(plate("A"), plate("B"), plate("C"))) }
        val vm = vmWith(repo, classifyResult = { Result.success(emptyList()) })
        vm.onIntent(ComposePlateIntent.SelectPhoto(2))

        // Shrink the draft directly (not via RemovePhotoAt) to exercise the observer's own safety
        // clamp in isolation, independent of RemovePhotoAt's precise sane-neighbor computation.
        repo.saveDraft(repo.observeDraft().first()!!.copy(plates = listOf(plate("A"))))

        vm.state.test { assertEquals(0, expectMostRecentItem().selectedIndex) }
    }

    @Test fun classification_fingerprint_follows_the_primary_across_reorder() = runTest {
        val repo = FakeMealRepository().apply {
            saveDraft(draftWithPhotos(plate("plate-a"), plate("plate-b")))
        }
        val vm = vmWith(
            repo,
            classifyResult = { jpeg ->
                when (jpeg.decodeToString()) {
                    "plate-a" -> Result.success(listOf(DishLabel("pizza", 0.9f)))
                    else -> Result.success(listOf(DishLabel("salad", 0.9f)))
                }
            },
            dishMap = mapOf("pizza" to listOf("tomato", "cheese"), "salad" to listOf("lettuce", "olive")),
        )

        vm.onPhotoCaptured(bytes("plate-a"))
        vm.state.test {
            assertEquals(
                listOf(IngredientSlug.of("tomato").getOrNull()!!, IngredientSlug.of("cheese").getOrNull()!!),
                expectMostRecentItem().detectedIngredients,
            )
        }

        vm.onIntent(ComposePlateIntent.MovePhoto(0, 1))
        vm.state.test {
            // plate-b slid into index 0 — it's now the primary the compose screen would reclassify.
            assertEquals("plate-b", expectMostRecentItem().primaryPhoto?.photoBytes?.decodeToString())
        }

        // The screen's LaunchedEffect would now call onPhotoCaptured with the NEW primary's bytes.
        // Its content differs from the last-classified fingerprint (plate-a), so this must actually
        // run rather than be deduped.
        vm.onPhotoCaptured(bytes("plate-b"))
        vm.state.test {
            assertEquals(
                listOf(IngredientSlug.of("lettuce").getOrNull()!!, IngredientSlug.of("olive").getOrNull()!!),
                expectMostRecentItem().detectedIngredients,
            )
        }
    }

    /** 2026-05-24T13:30Z — 13:30 in the test's UTC zone → MealSlot.forHour(13) = Lunch. */
    private val composeLunchTakenAtMs = Instant.parse("2026-05-24T13:30:00Z").toEpochMilliseconds()

    @Test fun exif_prefill_on_add_photos_applies_once_from_the_first_metadata_carrying_gallery_photo() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhotos()) }
        val vm = vmWith(repo, classifyResult = { Result.success(emptyList()) })

        vm.onIntent(
            ComposePlateIntent.AddPhotos(
                listOf(
                    // First photo carries no metadata — nothing to prefill from it.
                    PickedPhoto(bytes("g1"), PhotoSource.Gallery, metadata = null),
                    // Second photo's EXIF is the first WITH metadata — it wins the prefill.
                    PickedPhoto(
                        bytes("g2"), PhotoSource.Gallery,
                        metadata = PhotoMetadata(takenAtEpochMs = composeLunchTakenAtMs, latitude = 41.4, longitude = 2.17),
                    ),
                    // A third photo's metadata must NOT override the already-set fields.
                    PickedPhoto(
                        bytes("g3"), PhotoSource.Gallery,
                        metadata = PhotoMetadata(takenAtEpochMs = null, latitude = 10.0, longitude = 20.0),
                    ),
                ),
            ),
        )

        val draft = repo.observeDraft().first()!!
        assertEquals(3, draft.plates.size)
        assertEquals(MealSlot.Lunch, draft.slot)
        assertEquals(41.4, draft.coordinates?.latitude)
        assertEquals(2.17, draft.coordinates?.longitude)
    }

    @Test fun photo_pick_failed_surfaces_an_upload_error() = runTest {
        val repo = FakeMealRepository().apply { saveDraft(draftWithPhotos(plate("A"))) }
        val vm = vmWith(repo, classifyResult = { Result.success(emptyList()) })

        vm.onIntent(ComposePlateIntent.PhotoPickFailed)

        vm.state.test { assertEquals(MealError.Publish.PhotoUploadFailed, expectMostRecentItem().error) }
    }

    private class FakeCrewMembership(private val crews: List<CrewId>) : CrewMembershipPort {
        constructor(crew: CrewId) : this(listOf(crew))
        override fun observeMyCrews(accountId: AccountId): Flow<List<CrewSummary>> =
            MutableStateFlow(crews.map { CrewSummary(it, "Crew ${it.value}") })
    }

    private class FakeClassifier(
        private val result: (ByteArray) -> Result<List<DishLabel>, ClassifierError>,
    ) : MealClassifierPort {
        override suspend fun classify(jpeg: ByteArray) = result(jpeg)
    }

    private class FakeIngredients(private val dishMap: Map<String, List<String>>) : IngredientReadPort {
        override fun observeCatalog(): Flow<Map<IngredientSlug, Ingredient>> = MutableStateFlow(emptyMap())
        override suspend fun findBySlugs(slugs: Set<IngredientSlug>): List<Ingredient> = emptyList()
        override suspend fun suggestForDish(dishSlug: String): List<IngredientSlug> =
            dishMap[dishSlug].orEmpty().map { IngredientSlug.of(it).getOrNull()!! }
    }

    private class FakeFeatureFlags(private val mealAiEnabled: Boolean) : FeatureFlagPort {
        override fun isMealAiEnabled(): Boolean = mealAiEnabled
    }
}
