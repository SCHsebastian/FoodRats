package es.schsebastian.foodrats.feature.feed.presentation.feed

import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.domain.meal.ReactionError
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.feed.domain.error.FeedError
import es.schsebastian.foodrats.feature.feed.domain.model.FeedDay
import es.schsebastian.foodrats.feature.feed.presentation.components.FeedMealUi
import kotlinx.datetime.LocalDate

data class FeedState(
    val day: FeedDay? = null,
    /** Local "today" in the feed's zone; lets the day header label as Today/Yesterday. */
    val today: LocalDate? = null,
    val meals: List<FeedMealUi> = emptyList(),
    val isLoading: Boolean = true,
    val error: FeedError? = null,
    val canGoPrev: Boolean = false,
    val canGoNext: Boolean = false,
    val pendingRateMealId: String? = null,
    val rateError: RateError? = null,
    /** Last reaction-toggle failure, surfaced via the feed error banner. */
    val reactError: ReactionError? = null,
    val isUploadActive: Boolean = false,
    /**
     * Offline-first publish-queue aggregate (roadmap §5.2), surfaced in the feed
     * top bar. [queuedPending] = drafts still trying to publish on their own
     * (queued / mid-upload / retryable); [queuedFailed] = drafts the retry runner
     * gave up on (`Failed(retryable = false)`), which need a user retry/dismiss.
     * Both zero → the queue bar hides.
     */
    val queuedPending: Int = 0,
    val queuedFailed: Int = 0,
    /** Active crew's blind-voting flag; masks meal authors until the viewer rates. */
    val blindVoting: Boolean = false,
) : MviState

sealed interface FeedIntent : MviIntent {
    data object PrevDay : FeedIntent
    data object NextDay : FeedIntent
    data object DismissError : FeedIntent
    data class RateMeal(val mealId: String, val score: Int) : FeedIntent

    /** Toggle the viewer's daily-emote reaction on the meal. */
    data class ReactMeal(val mealId: String) : FeedIntent

    /** Re-arm the terminal-failed queued drafts so the runner drains them again. */
    data object RetryQueuedDrafts : FeedIntent

    /** Drop the terminal-failed queued drafts from the queue. */
    data object DismissQueuedDrafts : FeedIntent
}

sealed interface FeedEffect : MviEffect
