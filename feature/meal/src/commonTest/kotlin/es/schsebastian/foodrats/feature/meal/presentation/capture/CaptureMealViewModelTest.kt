package es.schsebastian.foodrats.feature.meal.presentation.capture

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.CaptureSource
import es.schsebastian.foodrats.core.domain.analytics.RecordingAnalyticsTracker
import es.schsebastian.foodrats.core.domain.crew.CrewMembershipPort
import es.schsebastian.foodrats.core.domain.crew.CrewSummary
import es.schsebastian.foodrats.core.domain.location.Coordinates
import es.schsebastian.foodrats.core.domain.meal.MealPublishPolicy
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.PlateSource
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.preferences.DefaultAudienceError
import es.schsebastian.foodrats.core.domain.preferences.DefaultAudiencePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.presentation.photopicker.PhotoMetadata
import es.schsebastian.foodrats.core.presentation.photopicker.PhotoSource
import es.schsebastian.foodrats.core.presentation.photopicker.PickedPhoto
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import es.schsebastian.foodrats.feature.meal.domain.test.FakeMealRepository
import es.schsebastian.foodrats.feature.meal.domain.usecase.StartMealDraftUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftUseCase
import es.schsebastian.foodrats.feature.meal.i18n.MealStringKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureMealViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val zone = TimeZone.UTC
    private val clock = object : Clock { override fun now() = Instant.parse("2026-05-16T12:00:00Z") }
    private val account = (AccountId.of("acc-1") as Result.Ok).value
    private val crew = (CrewId.of("crew-1") as Result.Ok).value

    private class FakeSessionProvider(
        private val session: Session?,
        private val error: SessionError = SessionError.NotSignedIn,
    ) : SessionProvider {
        override val current: Flow<Session?> = MutableStateFlow(session)
        override suspend fun requireCurrent(): Result<Session, SessionError> =
            session?.let { Result.success(it) } ?: Result.failure(error)
    }

    private class FakeCrewMembership(private val crews: List<CrewId>) : CrewMembershipPort {
        override fun observeMyCrews(accountId: AccountId): Flow<List<CrewSummary>> =
            MutableStateFlow(crews.map { CrewSummary(it, "Crew ${it.value}") })
    }

    private class FakeDefaultAudiencePort(
        private val saved: Set<CrewId>? = null,
    ) : DefaultAudiencePort {
        override val defaultAudience: Flow<Set<CrewId>?> = MutableStateFlow(saved)
        override suspend fun set(crewIds: Set<CrewId>): Result<Unit, DefaultAudienceError> =
            Result.success(Unit)
    }

    private val analytics = RecordingAnalyticsTracker()
    private val crew2 = (CrewId.of("crew-2") as Result.Ok).value

    private fun viewModel(
        repo: FakeMealRepository = FakeMealRepository(),
        session: Session? = Session(account, crew),
        crews: List<CrewId> = listOf(crew),
        savedDefaultAudience: Set<CrewId>? = null,
    ) = CaptureMealViewModel(
        startDraft = StartMealDraftUseCase(repo, clock, zone),
        updateDraft = UpdateMealDraftUseCase(repo),
        sessionProvider = FakeSessionProvider(session),
        crewMembership = FakeCrewMembership(crews),
        defaultAudience = FakeDefaultAudiencePort(savedDefaultAudience),
        analytics = analytics,
    )

    @Test fun session_error_on_start_surfaces_error_banner() = runTest {
        val vm = viewModel(session = null)
        vm.state.test {
            vm.onIntent(CaptureMealIntent.Start)
            assertEquals(MealStringKey.CaptureSessionError, expectMostRecentItem().error)
        }
        assertEquals(emptyList(), analytics.events)
    }

    @Test fun no_crews_on_start_surfaces_error_banner() = runTest {
        val vm = viewModel(crews = emptyList())
        vm.state.test {
            vm.onIntent(CaptureMealIntent.Start)
            assertEquals(MealStringKey.CaptureNoCrews, expectMostRecentItem().error)
        }
        assertEquals(emptyList(), analytics.events)
    }

    @Test fun successful_start_leaves_no_error() = runTest {
        val vm = viewModel()
        vm.state.test {
            vm.onIntent(CaptureMealIntent.Start)
            assertEquals(null, expectMostRecentItem().error)
        }
    }

    @Test fun successful_start_tracks_meal_capture_started_once() = runTest {
        val vm = viewModel()
        vm.onIntent(CaptureMealIntent.Start)
        assertEquals(
            listOf<AnalyticsEvent>(AnalyticsEvent.MealCaptureStarted(CaptureSource.UNKNOWN)),
            analytics.events.toList(),
        )
    }

    @Test fun draft_failure_on_start_does_not_track() = runTest {
        val repo = FakeMealRepository().apply {
            saveDraftResultOverride = Result.failure(MealError.Publish.PublishUnavailable)
        }
        val vm = viewModel(repo = repo)
        vm.state.test {
            vm.onIntent(CaptureMealIntent.Start)
            assertEquals(MealStringKey.CaptureDraftFailed, expectMostRecentItem().error)
        }
        assertEquals(emptyList(), analytics.events)
    }

    @Test fun active_crew_seeds_draft_with_active_crew_only() = runTest {
        // Launched from crew's feed (active crew) with crew2 also available and crew2 saved as the
        // last-used default: the active crew wins, so the draft targets ONLY the active crew.
        val repo = FakeMealRepository()
        val crews = listOf(crew, crew2)
        val vm = viewModel(repo = repo, session = Session(account, crew), crews = crews, savedDefaultAudience = setOf(crew2))
        vm.state.test {
            vm.onIntent(CaptureMealIntent.Start)
            expectMostRecentItem()
        }
        repo.observeDraft().test {
            val draft = awaitItem()
            assertEquals(setOf(crew), draft?.audienceCrewIds)
        }
    }

    @Test fun active_crew_not_a_member_falls_back_to_saved_or_all() = runTest {
        // Active crew references a crew the user isn't a member of (stale) → ignore it and fall back.
        val stale = (CrewId.of("stale-crew") as Result.Ok).value
        val repo = FakeMealRepository()
        val crews = listOf(crew, crew2)
        val vm = viewModel(repo = repo, session = Session(account, stale), crews = crews, savedDefaultAudience = null)
        vm.state.test {
            vm.onIntent(CaptureMealIntent.Start)
            expectMostRecentItem()
        }
        repo.observeDraft().test {
            val draft = awaitItem()
            assertEquals(setOf(crew, crew2), draft?.audienceCrewIds)
        }
    }

    @Test fun no_active_crew_no_saved_default_seeds_draft_with_all_crews() = runTest {
        val repo = FakeMealRepository()
        val crews = listOf(crew, crew2)
        val vm = viewModel(repo = repo, session = Session(account, null), crews = crews, savedDefaultAudience = null)
        vm.state.test {
            vm.onIntent(CaptureMealIntent.Start)
            expectMostRecentItem()
        }
        repo.observeDraft().test {
            val draft = awaitItem()
            assertEquals(setOf(crew, crew2), draft?.audienceCrewIds)
        }
    }

    @Test fun no_active_crew_saved_default_seeds_draft_with_saved_subset() = runTest {
        val repo = FakeMealRepository()
        // Only crew is in the saved default; crew2 is available but not saved.
        val crews = listOf(crew, crew2)
        val vm = viewModel(repo = repo, session = Session(account, null), crews = crews, savedDefaultAudience = setOf(crew))
        vm.state.test {
            vm.onIntent(CaptureMealIntent.Start)
            expectMostRecentItem()
        }
        repo.observeDraft().test {
            val draft = awaitItem()
            assertEquals(setOf(crew), draft?.audienceCrewIds)
        }
    }

    @Test fun photo_taken_reentry_while_capturing_is_dropped() = runTest {
        val repo = FakeMealRepository()
        val gate = CompletableDeferred<Unit>()
        // Suspend ONLY the photo-carrying save (Start's initial draft save has no plate) so a
        // second PhotoTaken can arrive while the first is still persisting.
        val gatedRepo = object : MealRepository by repo {
            override suspend fun saveDraft(draft: MealDraft): Result<Unit, MealError> {
                if (draft.plate != null) gate.await()
                return repo.saveDraft(draft)
            }
        }
        val vm = CaptureMealViewModel(
            startDraft = StartMealDraftUseCase(gatedRepo, clock, zone),
            updateDraft = UpdateMealDraftUseCase(gatedRepo),
            sessionProvider = FakeSessionProvider(Session(account, crew)),
            crewMembership = FakeCrewMembership(listOf(crew)),
            defaultAudience = FakeDefaultAudiencePort(),
            analytics = analytics,
        )
        vm.onIntent(CaptureMealIntent.Start)
        vm.effects.test {
            vm.onIntent(CaptureMealIntent.PhotoTaken(byteArrayOf(1)))
            assertTrue(vm.state.value.isCapturing)
            // Re-entry while the first photo is still being persisted must be a no-op.
            vm.onIntent(CaptureMealIntent.PhotoTaken(byteArrayOf(2)))
            gate.complete(Unit)
            assertEquals(CaptureMealEffect.NavigateToCompose, awaitItem())
            expectNoEvents() // exactly ONE navigation — the duplicate was dropped
        }
        assertEquals(false, vm.state.value.isCapturing)
        // The persisted plate is the FIRST photo; the duplicate never overwrote it.
        assertContentEquals(byteArrayOf(1), repo.observeDraft().first()?.plate?.photoBytes)
    }

    // ── PhotosTaken: atomic multi-photo batch append ──────────────────────

    @Test fun photos_taken_appends_every_photo_in_order() = runTest {
        val repo = FakeMealRepository()
        val vm = viewModel(repo = repo)
        vm.onIntent(CaptureMealIntent.Start)

        vm.onIntent(
            CaptureMealIntent.PhotosTaken(
                listOf(
                    PickedPhoto(byteArrayOf(1), PhotoSource.Camera),
                    PickedPhoto(byteArrayOf(2), PhotoSource.Gallery),
                    PickedPhoto(byteArrayOf(3), PhotoSource.Camera),
                ),
            ),
        )

        val draft = repo.observeDraft().first()!!
        assertEquals(3, draft.plates.size)
        assertContentEquals(byteArrayOf(1), draft.plates[0].photoBytes)
        assertContentEquals(byteArrayOf(2), draft.plates[1].photoBytes)
        assertContentEquals(byteArrayOf(3), draft.plates[2].photoBytes)
        assertEquals(listOf(PlateSource.Camera, PlateSource.Gallery, PlateSource.Camera), draft.plates.map { it.source })
    }

    @Test fun photos_taken_navigates_to_compose_once_for_the_whole_batch() = runTest {
        val repo = FakeMealRepository()
        val vm = viewModel(repo = repo)
        vm.onIntent(CaptureMealIntent.Start)

        vm.effects.test {
            vm.onIntent(
                CaptureMealIntent.PhotosTaken(
                    listOf(PickedPhoto(byteArrayOf(1), PhotoSource.Camera), PickedPhoto(byteArrayOf(2), PhotoSource.Camera)),
                ),
            )
            assertEquals(CaptureMealEffect.NavigateToCompose, awaitItem())
            expectNoEvents() // exactly ONE navigation for the whole batch, not one per photo
        }
    }

    @Test fun photos_taken_trims_to_the_photo_cap() = runTest {
        val repo = FakeMealRepository()
        val vm = viewModel(repo = repo)
        vm.onIntent(CaptureMealIntent.Start)
        val overCap = (1..MealPublishPolicy.MAX_PHOTOS_PER_MEAL + 3).map { PickedPhoto(byteArrayOf(it.toByte()), PhotoSource.Camera) }

        vm.onIntent(CaptureMealIntent.PhotosTaken(overCap))

        assertEquals(MealPublishPolicy.MAX_PHOTOS_PER_MEAL, repo.observeDraft().first()!!.plates.size)
    }

    @Test fun photos_taken_with_empty_list_is_a_noop() = runTest {
        val repo = FakeMealRepository()
        val vm = viewModel(repo = repo)
        vm.onIntent(CaptureMealIntent.Start)

        vm.effects.test {
            vm.onIntent(CaptureMealIntent.PhotosTaken(emptyList()))
            expectNoEvents()
        }
        assertEquals(emptyList(), repo.observeDraft().first()!!.plates)
    }

    @Test fun photos_taken_gallery_batch_prefills_from_the_first_photo_carrying_metadata() = runTest {
        val repo = FakeMealRepository()
        val vm = viewModel(repo = repo)
        vm.onIntent(CaptureMealIntent.Start)

        vm.onIntent(
            CaptureMealIntent.PhotosTaken(
                listOf(
                    // First photo carries no metadata — nothing to prefill from it.
                    PickedPhoto(byteArrayOf(1), PhotoSource.Gallery, metadata = null),
                    // Second photo's EXIF is the first WITH metadata — it wins the prefill.
                    PickedPhoto(
                        byteArrayOf(2), PhotoSource.Gallery,
                        metadata = PhotoMetadata(takenAtEpochMs = lunchTakenAtMs, latitude = 41.4, longitude = 2.17),
                    ),
                    // A third photo's metadata must NOT override the already-set fields.
                    PickedPhoto(
                        byteArrayOf(3), PhotoSource.Gallery,
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

    @Test fun no_active_crew_saved_default_outside_current_crews_falls_back_to_all_crews() = runTest {
        // The saved default references a crew the user has since left.
        val leftCrew = (CrewId.of("left-crew") as Result.Ok).value
        val repo = FakeMealRepository()
        val crews = listOf(crew)
        val vm = viewModel(repo = repo, session = Session(account, null), crews = crews, savedDefaultAudience = setOf(leftCrew))
        vm.state.test {
            vm.onIntent(CaptureMealIntent.Start)
            expectMostRecentItem()
        }
        repo.observeDraft().test {
            val draft = awaitItem()
            // Intersection is empty → fall back to all current crews.
            assertEquals(setOf(crew), draft?.audienceCrewIds)
        }
    }

    // ── gallery provenance + EXIF prefill ────────────────────────────────

    /** 2026-05-16T13:30Z — 13:30 in the test's UTC zone → MealSlot.forHour(13) = Lunch. */
    private val lunchTakenAtMs = Instant.parse("2026-05-16T13:30:00Z").toEpochMilliseconds()

    @Test fun gallery_photo_with_metadata_prefills_slot_and_coordinates_when_unset() = runTest {
        val repo = FakeMealRepository()
        val vm = viewModel(repo = repo)
        vm.onIntent(CaptureMealIntent.Start)
        vm.onIntent(
            CaptureMealIntent.PhotoTaken(
                bytes = byteArrayOf(1),
                source = PhotoSource.Gallery,
                metadata = PhotoMetadata(takenAtEpochMs = lunchTakenAtMs, latitude = 41.4, longitude = 2.17),
            ),
        )
        val draft = repo.observeDraft().first()!!
        assertEquals(PlateSource.Gallery, draft.plate?.source)
        assertEquals(MealSlot.Lunch, draft.slot)
        assertEquals(41.4, draft.coordinates?.latitude)
        assertEquals(2.17, draft.coordinates?.longitude)
    }

    @Test fun gallery_prefill_does_not_overwrite_a_user_set_slot() = runTest {
        val repo = FakeMealRepository()
        val vm = viewModel(repo = repo)
        vm.onIntent(CaptureMealIntent.Start)
        // The user already tagged the draft Dinner before the photo landed.
        repo.saveDraft(repo.observeDraft().first()!!.copy(slot = MealSlot.Dinner))
        vm.onIntent(
            CaptureMealIntent.PhotoTaken(
                bytes = byteArrayOf(1),
                source = PhotoSource.Gallery,
                metadata = PhotoMetadata(takenAtEpochMs = lunchTakenAtMs, latitude = 41.4, longitude = 2.17),
            ),
        )
        val draft = repo.observeDraft().first()!!
        // The 13:30 EXIF timestamp must NOT clobber the user's Dinner choice…
        assertEquals(MealSlot.Dinner, draft.slot)
        // …while the unset coordinates are still prefilled.
        assertEquals(41.4, draft.coordinates?.latitude)
    }

    @Test fun gallery_prefill_ignores_out_of_range_gps_and_never_fails_the_flow() = runTest {
        val repo = FakeMealRepository()
        val vm = viewModel(repo = repo)
        vm.onIntent(CaptureMealIntent.Start)
        vm.effects.test {
            vm.onIntent(
                CaptureMealIntent.PhotoTaken(
                    bytes = byteArrayOf(1),
                    source = PhotoSource.Gallery,
                    metadata = PhotoMetadata(takenAtEpochMs = null, latitude = 123.0, longitude = 999.0),
                ),
            )
            // Best-effort: the invalid pair is dropped and navigation still fires.
            assertEquals(CaptureMealEffect.NavigateToCompose, awaitItem())
        }
        val draft = repo.observeDraft().first()!!
        assertEquals(null, draft.coordinates)
        assertEquals(null, draft.slot)
        assertEquals(null, vm.state.value.error)
    }

    @Test fun camera_photo_prefills_nothing_and_keeps_camera_source() = runTest {
        val repo = FakeMealRepository()
        val vm = viewModel(repo = repo)
        vm.onIntent(CaptureMealIntent.Start)
        vm.onIntent(CaptureMealIntent.PhotoTaken(byteArrayOf(1), source = PhotoSource.Camera))
        val draft = repo.observeDraft().first()!!
        assertEquals(PlateSource.Camera, draft.plate?.source)
        assertEquals(null, draft.slot)
        assertEquals(null, draft.coordinates)
    }

    @Test fun gallery_photo_with_taken_at_but_no_gps_prefills_slot_only() = runTest {
        val repo = FakeMealRepository()
        val vm = viewModel(repo = repo)
        vm.onIntent(CaptureMealIntent.Start)
        vm.onIntent(
            CaptureMealIntent.PhotoTaken(
                bytes = byteArrayOf(1),
                source = PhotoSource.Gallery,
                metadata = PhotoMetadata(takenAtEpochMs = lunchTakenAtMs, latitude = null, longitude = null),
            ),
        )
        val draft = repo.observeDraft().first()!!
        assertEquals(MealSlot.Lunch, draft.slot)
        assertEquals(null, draft.coordinates)
    }

    @Test fun gallery_photo_with_gps_but_no_taken_at_prefills_coordinates_only() = runTest {
        val repo = FakeMealRepository()
        val vm = viewModel(repo = repo)
        vm.onIntent(CaptureMealIntent.Start)
        vm.onIntent(
            CaptureMealIntent.PhotoTaken(
                bytes = byteArrayOf(1),
                source = PhotoSource.Gallery,
                metadata = PhotoMetadata(takenAtEpochMs = null, latitude = 41.4, longitude = 2.17),
            ),
        )
        val draft = repo.observeDraft().first()!!
        assertEquals(null, draft.slot)
        assertEquals(41.4, draft.coordinates?.latitude)
        assertEquals(2.17, draft.coordinates?.longitude)
    }

    @Test fun gallery_prefill_with_only_latitude_out_of_range_drops_the_whole_pair_and_never_fails_the_flow() = runTest {
        val repo = FakeMealRepository()
        val vm = viewModel(repo = repo)
        vm.onIntent(CaptureMealIntent.Start)
        vm.effects.test {
            vm.onIntent(
                CaptureMealIntent.PhotoTaken(
                    bytes = byteArrayOf(1),
                    source = PhotoSource.Gallery,
                    // Longitude alone is valid, but Coordinates.of validates the pair atomically —
                    // one invalid axis rejects both, it doesn't partially apply.
                    metadata = PhotoMetadata(takenAtEpochMs = null, latitude = 90.1, longitude = 2.17),
                ),
            )
            assertEquals(CaptureMealEffect.NavigateToCompose, awaitItem())
        }
        val draft = repo.observeDraft().first()!!
        assertEquals(null, draft.coordinates)
        assertEquals(null, vm.state.value.error)
    }

    @Test fun gallery_prefill_skips_both_fields_already_user_set_and_changes_nothing() = runTest {
        val repo = FakeMealRepository()
        val vm = viewModel(repo = repo)
        vm.onIntent(CaptureMealIntent.Start)
        val userCoords = (Coordinates.of(10.0, 20.0) as Result.Ok).value
        // The user already picked a slot AND dropped a manual location pin before the photo landed.
        repo.saveDraft(repo.observeDraft().first()!!.copy(slot = MealSlot.Dinner, coordinates = userCoords))
        vm.onIntent(
            CaptureMealIntent.PhotoTaken(
                bytes = byteArrayOf(1),
                source = PhotoSource.Gallery,
                metadata = PhotoMetadata(takenAtEpochMs = lunchTakenAtMs, latitude = 41.4, longitude = 2.17),
            ),
        )
        val draft = repo.observeDraft().first()!!
        // Neither the EXIF-derived Lunch slot nor the EXIF GPS clobber the user's prior choices.
        assertEquals(MealSlot.Dinner, draft.slot)
        assertEquals(userCoords, draft.coordinates)
    }
}
