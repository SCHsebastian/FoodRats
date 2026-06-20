package es.schsebastian.foodrats.feature.feed.presentation.feed

import es.schsebastian.foodrats.core.designsystem.molecules.FrReportReasonOption
import es.schsebastian.foodrats.core.domain.account.BlockError
import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.domain.meal.ReactionError
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.feed.domain.error.FeedError
import es.schsebastian.foodrats.feature.feed.domain.model.FeedDay
import es.schsebastian.foodrats.feature.feed.presentation.components.FeedMealUi
import es.schsebastian.foodrats.feature.feed.presentation.components.RelativeTimestamp
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
    /**
     * Write-outbox aggregate (P2 §1 T8), surfaced in the feed top bar by
     * [es.schsebastian.foodrats.feature.feed.presentation.components.FrSyncStatusBar].
     * [syncPending] = rate/comment/reaction/crew-admin mutations still on their way
     * to applying (Pending / Uploading / retryable Failed); [syncFailed] = mutations
     * the runner gave up on (`Failed(retryable = false)`), needing a user
     * retry/dismiss. Both zero → the sync bar hides. Derived solely from
     * [es.schsebastian.foodrats.core.domain.outbox.OutboxPort.observePending] — the
     * single source of truth.
     */
    val syncPending: Int = 0,
    val syncFailed: Int = 0,
    /** Active crew's blind-voting flag; masks meal authors until the viewer rates. */
    val blindVoting: Boolean = false,
    /**
     * Freshness of the active crew's cached feed (offline-first P4-T2): a relative "synced X ago"
     * timestamp resolved against the clock when the last successful window sync landed, or `null` if
     * it has not synced yet this session. The VM owns the clock (mirrors `MealDetailViewModel`'s
     * comment relatives), so the screen just resolves the [RelativeTimestamp]. Derived solely from
     * [es.schsebastian.foodrats.core.domain.meal.FeedSyncStatusPort.lastSyncedAt] — single source of
     * truth.
     */
    val syncedRelative: RelativeTimestamp? = null,
    /** True while a user-triggered pull-to-refresh re-pull is in flight; drives the spinner. */
    val isRefreshing: Boolean = false,
    /**
     * Pending report opened from the feed overflow menu (UGC compliance §4).
     * When non-null the [FrReportSheet] is shown; on dismiss or submit it is cleared.
     */
    val feedReportTarget: FeedReportTarget? = null,
    /** True while the report write is in flight; disables the submit button. */
    val feedReportSubmitting: Boolean = false,
    /** Transient toast shown after a successful report from the feed (UGC compliance §4). */
    val feedReportSuccess: Boolean = false,
    /** Transient toast shown after a successful block from the feed (UGC compliance §5). */
    val feedBlockSuccess: Boolean = false,
    /**
     * Transient error surfaced when a block from the feed overflow fails (UGC compliance §5).
     * Mirrors [MealDetailViewModel]'s `blockError` pattern — failures are not silently dropped.
     * Cleared by [FeedIntent.DismissFeedBlockError] or by the next [FeedIntent.BlockFeedAuthor].
     */
    val feedBlockError: BlockError? = null,
    /**
     * Active crew's pinned welcome message (C6). Non-null → show the banner above the feed.
     * Null when the crew has no message or the user has dismissed it (per-crew, DataStore-persisted).
     * Derived from [es.schsebastian.foodrats.core.domain.crew.CrewWelcomePort.observeWelcomeMessage]
     * combined with [es.schsebastian.foodrats.core.domain.crew.CrewWelcomePort.isWelcomeDismissed].
     */
    val welcomeMessage: String? = null,
) : MviState

/**
 * The entity the user chose to report from the feed overflow menu (UGC compliance §4).
 * Carries both the meal id and author id regardless of variant so the sheet can route to the
 * correct [es.schsebastian.foodrats.core.domain.moderation.ReportPort] method.
 */
sealed interface FeedReportTarget {
    val mealId: String
    val authorId: String

    data class Meal(override val mealId: String, override val authorId: String) : FeedReportTarget
    data class Author(override val mealId: String, override val authorId: String) : FeedReportTarget
}

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

    /** Re-arm the terminal-failed outbox commands so the runner replays them. */
    data object RetrySyncOutbox : FeedIntent

    /** Drop the terminal-failed outbox commands from the outbox. */
    data object DismissSyncOutbox : FeedIntent

    /** Force a re-pull of the active crew's window (pull-to-refresh). */
    data object Refresh : FeedIntent

    // ── Feed overflow UGC actions (UGC compliance §4 / §5) ──────────────────────────────────────

    /** Open the [FrReportSheet] targeting the given meal or its author from the feed overflow. */
    data class OpenFeedReport(val target: FeedReportTarget) : FeedIntent

    /** Submit the report reason chosen in the [FrReportSheet] from the feed (UGC compliance §4). */
    data class SubmitFeedReport(val reason: FrReportReasonOption) : FeedIntent

    /** Dismiss the report sheet without submitting. */
    data object DismissFeedReport : FeedIntent

    /**
     * Block the meal's author directly from the feed overflow (UGC compliance §5).
     * A [es.schsebastian.foodrats.core.designsystem.molecules.FrConfirmDialog] is shown by the
     * screen BEFORE dispatching this intent so the write only fires after explicit confirmation.
     */
    data class BlockFeedAuthor(val authorId: String) : FeedIntent

    /** Clear the transient report-success toast. */
    data object DismissFeedReportSuccess : FeedIntent

    /** Clear the transient block-success toast. */
    data object DismissFeedBlockSuccess : FeedIntent

    /** Clear the transient block-error toast (UGC compliance §5). */
    data object DismissFeedBlockError : FeedIntent

    /** Dismiss the crew welcome banner; persists per-crew to DataStore (C6). */
    data object DismissWelcomeBanner : FeedIntent
}

sealed interface FeedEffect : MviEffect
