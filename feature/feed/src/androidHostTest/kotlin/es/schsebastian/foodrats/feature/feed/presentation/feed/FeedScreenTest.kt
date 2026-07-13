package es.schsebastian.foodrats.feature.feed.presentation.feed

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.domain.account.BlockError
import es.schsebastian.foodrats.core.domain.account.BlockedAccountsPort
import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.crew.CrewBlindVotingPort
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.FeedSyncStatusPort
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealPlate
import es.schsebastian.foodrats.core.domain.meal.MealPublishPolicy
import es.schsebastian.foodrats.core.domain.meal.MealReactionPort
import es.schsebastian.foodrats.core.domain.meal.MealReactions
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.meal.MealRatingPort
import es.schsebastian.foodrats.core.domain.meal.MealUploadProgressPort
import es.schsebastian.foodrats.core.domain.meal.MealUploadStatus
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.meal.OptimisticMealWritePort
import es.schsebastian.foodrats.core.domain.meal.PlateSource
import es.schsebastian.foodrats.core.domain.meal.QueuedUploadActionsPort
import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.domain.meal.ReactionError
import es.schsebastian.foodrats.core.domain.meal.ReactionKind
import es.schsebastian.foodrats.core.domain.meal.ReactionToggle
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntry
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryId
import es.schsebastian.foodrats.core.domain.outbox.OutboxError
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryStatus
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.feed.domain.usecase.ObserveFeedUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.RateMealUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

/**
 * [FeedScreen]'s `StructuralMealTile` is `private` (no focused composable seam), so this test
 * drives the real screen over a hand-assembled [FeedViewModel] with minimal fakes for every
 * mandatory port — the same shape as [FeedViewModelTest] (which lives in `commonTest` and is
 * therefore not visible from this `androidHostTest` source set, hence the local duplication).
 * Verifies the gallery-provenance marker chip (`track-feed.md`) renders end-to-end from a real
 * [Meal.plateSource] through [ObserveFeedUseCase] → [FeedViewModel] → [FeedScreen].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class FeedScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val zone = TimeZone.UTC
    private val clock = object : Clock { override fun now() = Instant.parse("2026-07-13T12:00:00Z") }
    private val account = (AccountId.of("acc-1") as Result.Ok).value
    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val day = MealDay(LocalDate(2026, 7, 13), zone)

    private class FakeMealReadPort(
        private val meals: List<MealWithRatings>,
    ) : MealReadPort {
        override fun observeFeed(crewId: CrewId, day: MealDay): Flow<Result<List<MealWithRatings>, MealReadError>> =
            MutableStateFlow(Result.success(meals))
        override fun observeRange(crewId: CrewId, from: MealDay, to: MealDay): Flow<Result<List<MealWithRatings>, MealReadError>> =
            MutableStateFlow(Result.success(meals))
    }

    private class FakeActiveCrewProvider(crewId: CrewId) : ActiveCrewProvider {
        override val current: Flow<CrewId?> = MutableStateFlow(crewId)
        override suspend fun set(crewId: CrewId) {}
        override suspend fun clear() {}
    }

    private class FakeSessionProvider(private val session: Session) : SessionProvider {
        override val current: Flow<Session?> = MutableStateFlow(session)
        override suspend fun requireCurrent(): Result<Session, SessionError> = Result.success(session)
    }

    private class FakeBlockedAccounts : BlockedAccountsPort {
        override fun observeBlocked(owner: AccountId): Flow<Set<AccountId>> = MutableStateFlow(emptySet())
        override suspend fun block(owner: AccountId, target: AccountId): Result<Unit, BlockError> = Result.success(Unit)
        override suspend fun unblock(owner: AccountId, target: AccountId): Result<Unit, BlockError> = Result.success(Unit)
    }

    private class FakeMealRatingPort : MealRatingPort {
        override suspend fun rate(crewId: CrewId, mealId: MealId, raterId: AccountId, score: Score): Result<Unit, RateError> =
            Result.success(Unit)
    }

    private class AlwaysOnline : ConnectivityPort {
        override fun isOnline(): Flow<Boolean> = flowOf(true)
    }

    private class NoopOutbox : es.schsebastian.foodrats.core.domain.outbox.OutboxPort {
        override suspend fun enqueue(cmd: PendingCommand): Result<OutboxEntry, OutboxError> =
            Result.failure(OutboxError.PersistenceUnavailable)
        override fun observePending(): Flow<List<OutboxEntry>> = MutableStateFlow(emptyList())
        override suspend fun markUploading(id: OutboxEntryId): Result<Boolean, OutboxError> = Result.success(false)
        override suspend fun markFailed(id: OutboxEntryId, errorKey: String, retryable: Boolean): Result<Unit, OutboxError> =
            Result.success(Unit)
        override suspend fun updateStatus(id: OutboxEntryId, status: OutboxEntryStatus): Result<Unit, OutboxError> =
            Result.success(Unit)
        override suspend fun remove(id: OutboxEntryId): Result<Unit, OutboxError> = Result.success(Unit)
        override suspend fun requeue(id: OutboxEntryId): Result<Unit, OutboxError> = Result.success(Unit)
    }

    private class NoopOptimistic : OptimisticMealWritePort {
        override suspend fun applyRate(crewId: CrewId, mealId: MealId, raterId: AccountId, score: Score, idempotencyKey: String) {}
        override suspend fun clearPending(idempotencyKey: String) {}
    }

    private class NoopUploadProgress : MealUploadProgressPort {
        override val status: StateFlow<MealUploadStatus> = MutableStateFlow(MealUploadStatus.Idle)
        override val queue = MealUploadProgressPort.DEFAULT_QUEUE
    }

    private class NoopBlindVoting : CrewBlindVotingPort {
        override fun observeBlindVoting(crewId: CrewId): Flow<Boolean> = MutableStateFlow(false)
    }

    private class NoopReactions : MealReactionPort {
        override fun observe(crewId: CrewId, mealId: MealId): Flow<Result<MealReactions, ReactionError.Read>> =
            MutableStateFlow(Result.success(MealReactions.empty(mealId)))
        override suspend fun toggle(
            crewId: CrewId, mealId: MealId, reactorId: AccountId, kind: ReactionKind,
        ): Result<ReactionToggle, ReactionError.Toggle> = Result.success(ReactionToggle.Added)
    }

    private class NoopQueuedUploadActions : QueuedUploadActionsPort {
        override suspend fun retryFailed() {}
        override suspend fun dismissFailed() {}
    }

    private class NoopSyncStatus : FeedSyncStatusPort {
        override fun lastSyncedAt(crewId: CrewId): Flow<Instant?> = MutableStateFlow(null)
        override suspend fun refresh(crewId: CrewId) {}
    }

    private fun meal(source: PlateSource) = Meal(
        id = (MealId.of("meal-1") as Result.Ok).value,
        author = MealAuthor(account, "Author", null),
        crewId = crew,
        day = day,
        slot = null,
        photoUrl = "https://example.test/photo.jpg",
        dish = (DishName.of("Pasta") as Result.Ok).value,
        description = Description.EMPTY,
        publishedAt = Instant.parse("2026-07-13T10:00:00Z"),
        plateSource = source,
    )

    /** multi-photo-crew15: a meal with [count] ordered photos, for the tile's photo-count chip. */
    private fun mealWithPlates(count: Int) = meal(PlateSource.Camera).copy(
        plates = List(count) { i ->
            MealPlate(photoUrl = "https://example.test/photo$i.jpg", source = PlateSource.Camera)
        },
    )

    private fun viewModel(meal: Meal) = viewModelForMeals(listOf(meal))

    /** Multi-meal variant (edge-case hardening, 2026-07-13): lets a test place a specific meal at a
     *  chosen bento rank (see [feedBentoItems]'s span table) instead of always landing on the wide
     *  hero tile. Duplicated wiring rather than reusing [viewModel] to avoid touching that existing,
     *  already-covered single-meal call site. */
    private fun viewModelForMeals(meals: List<Meal>) = FeedViewModel(
        observeFeed = ObserveFeedUseCase(
            FakeActiveCrewProvider(crew),
            FakeMealReadPort(meals.map { MealWithRatings(it, emptyList()) }),
            FakeSessionProvider(Session(account, crew)),
            FakeBlockedAccounts(),
        ),
        rateMeal = RateMealUseCase(FakeMealRatingPort(), AlwaysOnline(), NoopOutbox(), NoopOptimistic()),
        activeCrew = FakeActiveCrewProvider(crew),
        session = FakeSessionProvider(Session(account, crew)),
        clock = clock,
        zone = zone,
        uploadProgress = NoopUploadProgress(),
        blindVoting = NoopBlindVoting(),
        reactions = NoopReactions(),
        queuedUploadActions = NoopQueuedUploadActions(),
        connectivity = AlwaysOnline(),
        outbox = NoopOutbox(),
        syncStatus = NoopSyncStatus(),
        optimistic = NoopOptimistic(),
    )

    @Test fun gallery_meal_shows_the_gallery_marker_chip() {
        val vm = viewModel(meal(PlateSource.Gallery))

        rule.setContent {
            FoodRatsTheme {
                FeedScreen(
                    crewName = "Crew",
                    avatarInitials = "C",
                    avatarUrl = null,
                    onPickCrewClick = {},
                    onProfileClick = {},
                    onCrewSettingsClick = {},
                    onMealClick = { _, _ -> },
                    vm = vm,
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("GALLERY").assertExists()
        rule.onNodeWithContentDescription("Photo from gallery").assertExists()
    }

    @Test fun camera_meal_does_not_show_the_gallery_marker_chip() {
        val vm = viewModel(meal(PlateSource.Camera))

        rule.setContent {
            FoodRatsTheme {
                FeedScreen(
                    crewName = "Crew",
                    avatarInitials = "C",
                    avatarUrl = null,
                    onPickCrewClick = {},
                    onProfileClick = {},
                    onCrewSettingsClick = {},
                    onMealClick = { _, _ -> },
                    vm = vm,
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("GALLERY").assertDoesNotExist()
        rule.onNodeWithContentDescription("Photo from gallery").assertDoesNotExist()
    }

    // --- multi-photo-crew15: tile photo-count chip -----------------------------------------

    @Test fun multi_photo_meal_shows_the_photo_count_chip() {
        val vm = viewModel(mealWithPlates(3))

        rule.setContent {
            FoodRatsTheme {
                FeedScreen(
                    crewName = "Crew",
                    avatarInitials = "C",
                    avatarUrl = null,
                    onPickCrewClick = {},
                    onProfileClick = {},
                    onCrewSettingsClick = {},
                    onMealClick = { _, _ -> },
                    vm = vm,
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("×3").assertExists()
        rule.onNodeWithContentDescription("3 photos").assertExists()
    }

    @Test fun single_photo_meal_does_not_show_the_photo_count_chip() {
        val vm = viewModel(meal(PlateSource.Camera)) // no plates set -> legacy photoCount == 1

        rule.setContent {
            FoodRatsTheme {
                FeedScreen(
                    crewName = "Crew",
                    avatarInitials = "C",
                    avatarUrl = null,
                    onPickCrewClick = {},
                    onProfileClick = {},
                    onCrewSettingsClick = {},
                    onMealClick = { _, _ -> },
                    vm = vm,
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("×1").assertDoesNotExist()
        rule.onNodeWithContentDescription("1 photos").assertDoesNotExist()
    }

    // ── edge-case hardening (2026-07-13 track-edge-presentation) ─────────────────────────

    @Test fun photo_count_chip_pluralizes_correctly_at_two_photos() {
        // The chip is only ever shown for photoCount > 1, so N=2 is the smallest value the
        // FeedPluralKey.TilePhotoCountCd "other" form is ever actually rendered for.
        val vm = viewModel(mealWithPlates(2))

        rule.setContent {
            FoodRatsTheme {
                FeedScreen(
                    crewName = "Crew",
                    avatarInitials = "C",
                    avatarUrl = null,
                    onPickCrewClick = {},
                    onProfileClick = {},
                    onCrewSettingsClick = {},
                    onMealClick = { _, _ -> },
                    vm = vm,
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("×2").assertExists()
        rule.onNodeWithContentDescription("2 photos").assertExists()
    }

    @Test fun photo_count_chip_pluralizes_correctly_at_the_ten_photo_cap() {
        val vm = viewModel(mealWithPlates(MealPublishPolicy.MAX_PHOTOS_PER_MEAL))

        rule.setContent {
            FoodRatsTheme {
                FeedScreen(
                    crewName = "Crew",
                    avatarInitials = "C",
                    avatarUrl = null,
                    onPickCrewClick = {},
                    onProfileClick = {},
                    onCrewSettingsClick = {},
                    onMealClick = { _, _ -> },
                    vm = vm,
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("×${MealPublishPolicy.MAX_PHOTOS_PER_MEAL}").assertExists()
        rule.onNodeWithContentDescription("${MealPublishPolicy.MAX_PHOTOS_PER_MEAL} photos").assertExists()
    }

    @Test fun photo_count_chip_renders_on_a_narrow_non_hero_tile_too() {
        // feedBentoItems ranks by averageScore (all three tie at null -> -1.0, so the stable sort
        // keeps input order) and assigns span 6/4/2/3/... by rank; the 3rd-ranked meal here lands at
        // bento index 2 (span 2, `wide = false`) — the narrowest non-hero tile shape, distinct from
        // the wide hero every other test in this file exercises via a single-meal feed. The chip
        // logic isn't gated on tile width in StructuralMealTile, so it must still render here.
        val meals = listOf(
            meal(PlateSource.Camera).copy(id = (MealId.of("meal-1") as Result.Ok).value),
            meal(PlateSource.Camera).copy(id = (MealId.of("meal-2") as Result.Ok).value),
            mealWithPlates(4).copy(id = (MealId.of("meal-3") as Result.Ok).value),
        )
        val vm = viewModelForMeals(meals)

        rule.setContent {
            FoodRatsTheme {
                FeedScreen(
                    crewName = "Crew",
                    avatarInitials = "C",
                    avatarUrl = null,
                    onPickCrewClick = {},
                    onProfileClick = {},
                    onCrewSettingsClick = {},
                    onMealClick = { _, _ -> },
                    vm = vm,
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("×4").assertExists()
        rule.onNodeWithContentDescription("4 photos").assertExists()
    }
}
