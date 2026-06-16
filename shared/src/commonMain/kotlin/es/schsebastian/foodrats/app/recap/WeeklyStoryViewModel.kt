package es.schsebastian.foodrats.app.recap

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.data.share.StoryShareController
import es.schsebastian.foodrats.core.data.share.StoryShareOutcome
import es.schsebastian.foodrats.core.designsystem.templates.ShareCardFormat
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
import es.schsebastian.foodrats.core.domain.meal.DailyEmote
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * Drives the weekly-recap story player (roadmap §2.4). MVI single source of truth: scene index +
 * paused flag live only in [WeeklyStoryState]; the recap arrives PRE-ASSEMBLED from
 * [WeeklyRecapStream] (which folds the existing `ObserveStatsUseCase` + `ObserveAchievementsUseCase`
 * read paths via the pure assembler) — nothing is recomputed and no new server read is added.
 *
 * Analytics (§2.4, CHARTER §9): `digest_story_opened` fires once when the recap first becomes ready,
 * `digest_story_scene_viewed` fires for every scene that becomes visible (including the first), and
 * `digest_story_completed` fires when the player advances past the last scene. All are snake_case, no
 * PII (scene-kind slugs + counts only). The consent gate lives in the port — never re-checked here.
 * The auto-advance CLOCK lives in the Composable (it animates the progress and dispatches
 * [WeeklyStoryIntent.Advance]); the VM only owns which scene is current.
 *
 * [source] records whether the player was reached from the notification tap or an in-app entry.
 */
class WeeklyStoryViewModel(
    recapStream: WeeklyRecapStream,
    private val source: StoryOpenSource,
    private val storyShareController: StoryShareController,
    private val clock: Clock,
    private val zone: TimeZone,
    private val analytics: AnalyticsPort = NoopAnalyticsTracker,
) : MviViewModel<WeeklyStoryState, WeeklyStoryIntent, WeeklyStoryEffect>(WeeklyStoryState()) {

    private var openTracked = false

    init {
        viewModelScope.launch {
            recapStream().collect { result ->
                when (result) {
                    WeeklyRecapResult.Failed -> update { it.copy(isLoading = false, failed = true) }
                    is WeeklyRecapResult.Ready -> {
                        val recap = result.recap
                        val firstLoad = currentState.recap == null
                        update {
                            it.copy(
                                recap = recap,
                                isLoading = false,
                                failed = false,
                                // Keep the index in range if the recap reshaped under us.
                                currentIndex = it.currentIndex
                                    .coerceIn(0, (recap.sceneCount - 1).coerceAtLeast(0)),
                            )
                        }
                        if (!openTracked && !recap.isEmpty) {
                            openTracked = true
                            analytics.track(
                                AnalyticsEvent.DigestStoryOpened(
                                    source = source,
                                    sceneCount = recap.sceneCount,
                                ),
                            )
                        }
                        if (firstLoad && !recap.isEmpty) {
                            trackSceneViewed(recap, 0)
                        }
                    }
                }
            }
        }
    }

    override suspend fun handle(intent: WeeklyStoryIntent) = when (intent) {
        WeeklyStoryIntent.Advance -> advance()
        WeeklyStoryIntent.Back -> back()
        WeeklyStoryIntent.Pause -> update { it.copy(isPaused = true) }
        WeeklyStoryIntent.Resume -> update { it.copy(isPaused = false) }
        WeeklyStoryIntent.Close -> emit(WeeklyStoryEffect.Dismiss)
        WeeklyStoryIntent.ShareScene -> shareScene()
        WeeklyStoryIntent.DismissShareOutcome -> update { it.copy(shareOutcome = null) }
    }

    /**
     * Shares the current recap scene as a story card (spec §8.1 row 4). Only the shareable scenes
     * (top-meal / streak / your-week) produce a card; other scenes are a no-op. The controller owns
     * its IO (no `withContext` here); the off-screen card resolves its own i18n in composition. The
     * `share` analytics event fires ONLY when the launcher actually opened Instagram or the fallback
     * sheet — never on `Failed`, never in a use case (CHARTER §9). Pauses the auto-advance clock while
     * preparing so the scene doesn't flip out from under the rendered card.
     */
    private suspend fun shareScene() {
        if (currentState.isPreparingShare) return
        val scene = currentState.currentScene ?: return
        val todayEmote = DailyEmote.forDay(MealDay.today(clock, zone))
        val card = scene.toShareCard(todayEmote) ?: return
        update { it.copy(isPreparingShare = true, isPaused = true, shareOutcome = null) }
        val outcome = storyShareController.share(
            plateUrl = card.plateUrl,
            format = ShareCardFormat.Story,
        ) { plate -> RecapShareCardContent(card, plate) }
        if (outcome != StoryShareOutcome.Failed) {
            analytics.track(AnalyticsEvent.RecapShared(sceneKind = scene.kind.wire))
        }
        update {
            it.copy(
                isPreparingShare = false,
                isPaused = false,
                shareOutcome = when (outcome) {
                    StoryShareOutcome.OpenedInstagram     -> ShareOutcomeUi.Succeeded
                    StoryShareOutcome.OpenedFallbackSheet -> ShareOutcomeUi.OpenedSheet
                    StoryShareOutcome.Failed              -> ShareOutcomeUi.Failed
                },
            )
        }
    }

    private suspend fun advance() {
        val recap = currentState.recap ?: run { emit(WeeklyStoryEffect.Dismiss); return }
        if (currentState.isLastScene) {
            analytics.track(AnalyticsEvent.DigestStoryCompleted(sceneCount = recap.sceneCount))
            emit(WeeklyStoryEffect.Dismiss)
            return
        }
        val next = currentState.currentIndex + 1
        update { it.copy(currentIndex = next) }
        trackSceneViewed(recap, next)
    }

    private fun back() {
        val recap = currentState.recap ?: return
        val prev = (currentState.currentIndex - 1).coerceAtLeast(0)
        if (prev == currentState.currentIndex) return
        update { it.copy(currentIndex = prev) }
        trackSceneViewed(recap, prev)
    }

    private fun trackSceneViewed(recap: WeeklyRecap, index: Int) {
        val scene = recap.scenes.getOrNull(index) ?: return
        analytics.track(
            AnalyticsEvent.DigestStorySceneViewed(
                sceneKind = scene.kind.wire,
                sceneIndex = index,
            ),
        )
    }
}
