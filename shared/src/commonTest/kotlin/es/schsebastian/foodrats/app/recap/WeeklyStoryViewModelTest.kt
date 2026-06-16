package es.schsebastian.foodrats.app.recap

import app.cash.turbine.test
import es.schsebastian.foodrats.core.data.share.RecordingStoryShareController
import es.schsebastian.foodrats.core.data.share.StoryShareOutcome
import es.schsebastian.foodrats.core.designsystem.templates.ShareCardFormat
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.DigestStorySource
import es.schsebastian.foodrats.core.domain.analytics.RecordingAnalyticsTracker
import es.schsebastian.foodrats.core.domain.time.FixedClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyStoryViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val analytics = RecordingAnalyticsTracker()
    private val shareController = RecordingStoryShareController()
    private val clock = FixedClock(Instant.parse("2026-06-10T12:00:00Z"))
    private val zone = TimeZone.UTC

    /** A three-scene recap: cover + streak + your-week. */
    private fun threeSceneRecap() = WeeklyRecap(
        scenes = listOf(
            RecapScene.Cover(weekLabel = "2026-06-08"),
            RecapScene.Streak(streakDays = 4),
            RecapScene.YourWeek(streakDays = 4, cuisinesCollected = 3, ingredientsCollected = 12),
        ),
    )

    private fun vmWith(
        result: WeeklyRecapResult,
        source: DigestStorySource = DigestStorySource.NOTIFICATION,
    ) = WeeklyStoryViewModel(
        recapStream = { flowOf(result) },
        source = source,
        storyShareController = shareController,
        clock = clock,
        zone = zone,
        analytics = analytics,
    )

    @Test
    fun ready_recap_loads_scenes_and_fires_open_plus_first_scene_viewed() = runTest {
        val recap = threeSceneRecap()
        val vm = vmWith(WeeklyRecapResult.Ready(recap), DigestStorySource.IN_APP)

        vm.state.test {
            val s = expectMostRecentItem()
            assertFalse(s.isLoading)
            assertFalse(s.failed)
            assertEquals(3, s.sceneCount)
            assertEquals(0, s.currentIndex)
        }

        val opened = analytics.events.filterIsInstance<AnalyticsEvent.DigestStoryOpened>().single()
        assertEquals(DigestStorySource.IN_APP, opened.source)
        assertEquals(3, opened.sceneCount)
        // The first scene (cover) view is tracked on load.
        val firstViewed = analytics.events.filterIsInstance<AnalyticsEvent.DigestStorySceneViewed>().first()
        assertEquals(RecapSceneKind.Cover.wire, firstViewed.sceneKind)
        assertEquals(0, firstViewed.sceneIndex)
    }

    @Test
    fun advancing_steps_scenes_and_tracks_each_view() = runTest {
        val vm = vmWith(WeeklyRecapResult.Ready(threeSceneRecap()))

        vm.onIntent(WeeklyStoryIntent.Advance)
        assertEquals(1, vm.state.value.currentIndex)
        vm.onIntent(WeeklyStoryIntent.Advance)
        assertEquals(2, vm.state.value.currentIndex)

        val viewed = analytics.events.filterIsInstance<AnalyticsEvent.DigestStorySceneViewed>()
        // cover (load) + streak (advance 1) + your_week (advance 2).
        assertEquals(
            listOf(RecapSceneKind.Cover.wire, RecapSceneKind.Streak.wire, RecapSceneKind.YourWeek.wire),
            viewed.map { it.sceneKind },
        )
    }

    @Test
    fun advancing_past_last_scene_completes_and_dismisses() = runTest {
        val recap = threeSceneRecap()
        val vm = vmWith(WeeklyRecapResult.Ready(recap))
        // Move to the last scene.
        vm.onIntent(WeeklyStoryIntent.Advance)
        vm.onIntent(WeeklyStoryIntent.Advance)
        assertTrue(vm.state.value.isLastScene)

        vm.effects.test {
            vm.onIntent(WeeklyStoryIntent.Advance)
            assertEquals(WeeklyStoryEffect.Dismiss, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        val completed = analytics.events.filterIsInstance<AnalyticsEvent.DigestStoryCompleted>().single()
        assertEquals(3, completed.sceneCount)
    }

    @Test
    fun pause_and_resume_toggle_the_paused_flag() = runTest {
        val vm = vmWith(WeeklyRecapResult.Ready(threeSceneRecap()))
        vm.onIntent(WeeklyStoryIntent.Pause)
        assertTrue(vm.state.value.isPaused)
        vm.onIntent(WeeklyStoryIntent.Resume)
        assertFalse(vm.state.value.isPaused)
    }

    @Test
    fun close_emits_dismiss() = runTest {
        val vm = vmWith(WeeklyRecapResult.Ready(threeSceneRecap()))
        vm.effects.test {
            vm.onIntent(WeeklyStoryIntent.Close)
            assertEquals(WeeklyStoryEffect.Dismiss, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun failed_read_marks_failed_and_fires_no_analytics() = runTest {
        val vm = vmWith(WeeklyRecapResult.Failed)
        assertTrue(vm.state.value.failed)
        assertFalse(vm.state.value.isLoading)
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun empty_recap_does_not_fire_open_and_is_skipped_as_empty() = runTest {
        // A quiet week assembles to an empty scene list (assembler always adds cover + your-week,
        // but the stream could yield an explicitly empty recap); the VM must not track open.
        val vm = vmWith(WeeklyRecapResult.Ready(WeeklyRecap(scenes = emptyList())))
        assertTrue(vm.state.value.recap?.isEmpty == true)
        assertTrue(analytics.events.isEmpty())
    }

    // ───────────────────────────── share CTA ─────────────────────────────

    @Test
    fun sharing_a_shareable_scene_invokes_the_controller_and_tracks_recap_share() = runTest {
        val vm = vmWith(WeeklyRecapResult.Ready(threeSceneRecap()))
        // Advance to the streak scene (index 1), which is shareable.
        vm.onIntent(WeeklyStoryIntent.Advance)
        assertEquals(RecapSceneKind.Streak, vm.state.value.currentScene?.kind)
        assertTrue(vm.state.value.canShareCurrentScene)

        vm.onIntent(WeeklyStoryIntent.ShareScene)

        // The controller was called once; the streak card carries no plate URL.
        assertEquals(1, shareController.callCount)
        assertNull(shareController.lastCall?.plateUrl)
        assertEquals(ShareCardFormat.Story, shareController.lastCall?.format)
        // OpenedInstagram (the default outcome) → the `share` analytics event fires with the scene kind.
        val shared = analytics.events.filterIsInstance<AnalyticsEvent.RecapShared>().single()
        assertEquals(RecapSceneKind.Streak.wire, shared.sceneKind)
        // The transient flag is reset and a success toast is set.
        assertFalse(vm.state.value.isPreparingShare)
        assertEquals(ShareOutcomeUi.Succeeded, vm.state.value.shareOutcome)
    }

    @Test
    fun sharing_the_top_meal_scene_passes_its_plate_url_to_the_controller() = runTest {
        val recap = WeeklyRecap(
            scenes = listOf(
                RecapScene.TopMeal(
                    photoUrl = "https://signed/plate.jpg",
                    dishName = "Lasagna",
                    authorName = "Sam",
                    score = 8.4,
                    ratingCount = 5,
                ),
            ),
        )
        val vm = vmWith(WeeklyRecapResult.Ready(recap))
        assertEquals(RecapSceneKind.TopMeal, vm.state.value.currentScene?.kind)

        vm.onIntent(WeeklyStoryIntent.ShareScene)

        assertEquals("https://signed/plate.jpg", shareController.lastCall?.plateUrl)
        assertEquals(RecapSceneKind.TopMeal.wire, analytics.events.filterIsInstance<AnalyticsEvent.RecapShared>().single().sceneKind)
    }

    @Test
    fun a_failed_share_sets_the_failed_toast_and_fires_no_analytics() = runTest {
        shareController.outcome = StoryShareOutcome.Failed
        val vm = vmWith(WeeklyRecapResult.Ready(threeSceneRecap()))
        vm.onIntent(WeeklyStoryIntent.Advance) // → streak scene

        vm.onIntent(WeeklyStoryIntent.ShareScene)

        assertEquals(1, shareController.callCount)
        assertEquals(ShareOutcomeUi.Failed, vm.state.value.shareOutcome)
        assertTrue(analytics.events.filterIsInstance<AnalyticsEvent.RecapShared>().isEmpty())
    }

    @Test
    fun a_fallback_sheet_outcome_still_fires_recap_share() = runTest {
        shareController.outcome = StoryShareOutcome.OpenedFallbackSheet
        val vm = vmWith(WeeklyRecapResult.Ready(threeSceneRecap()))
        vm.onIntent(WeeklyStoryIntent.Advance) // → streak scene

        vm.onIntent(WeeklyStoryIntent.ShareScene)

        assertEquals(1, analytics.events.filterIsInstance<AnalyticsEvent.RecapShared>().size)
        assertEquals(ShareOutcomeUi.OpenedSheet, vm.state.value.shareOutcome)
    }

    @Test
    fun sharing_a_non_shareable_scene_is_a_no_op() = runTest {
        // The cover scene (index 0) is not shareable — no controller call, no analytics, no toast.
        val vm = vmWith(WeeklyRecapResult.Ready(threeSceneRecap()))
        assertEquals(RecapSceneKind.Cover, vm.state.value.currentScene?.kind)
        assertFalse(vm.state.value.canShareCurrentScene)

        vm.onIntent(WeeklyStoryIntent.ShareScene)

        assertEquals(0, shareController.callCount)
        assertTrue(analytics.events.filterIsInstance<AnalyticsEvent.RecapShared>().isEmpty())
        assertNull(vm.state.value.shareOutcome)
    }

    @Test
    fun dismiss_share_outcome_clears_the_toast() = runTest {
        val vm = vmWith(WeeklyRecapResult.Ready(threeSceneRecap()))
        vm.onIntent(WeeklyStoryIntent.Advance) // → streak scene
        vm.onIntent(WeeklyStoryIntent.ShareScene)
        assertEquals(ShareOutcomeUi.Succeeded, vm.state.value.shareOutcome)

        vm.onIntent(WeeklyStoryIntent.DismissShareOutcome)
        assertNull(vm.state.value.shareOutcome)
    }
}
