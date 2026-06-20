package es.schsebastian.foodrats.feature.feed.presentation.detail

import es.schsebastian.foodrats.core.designsystem.molecules.FrReportReasonOption
import es.schsebastian.foodrats.core.domain.meal.CommentError
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealDeleteError
import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.domain.moderation.ReportError
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.feed.domain.error.FeedError
import es.schsebastian.foodrats.feature.feed.presentation.components.CommentRowUi
import es.schsebastian.foodrats.feature.feed.presentation.components.FeedMealUi

data class MealDetailState(
    val meal: FeedMealUi? = null,
    val isLoading: Boolean = true,
    val error: FeedError? = null,
    val notFound: Boolean = false,
    val pendingRate: Boolean = false,
    val rateError: RateError? = null,
    val commentRows: List<CommentRowUi> = emptyList(),
    val commentsLoading: Boolean = true,
    val commentReadError: CommentError.Read? = null,
    val commentInput: String = "",
    val isPostingComment: Boolean = false,
    val commentWriteError: CommentError.Write? = null,
    val canDeleteMeal: Boolean = false,
    /** True when the viewer may report/block the meal's author (signed in + not their own meal). */
    val canModerateMeal: Boolean = false,
    val isDeletingMeal: Boolean = false,
    val mealDeleted: Boolean = false,
    val mealDeleteError: MealDeleteError? = null,
    val commentDeleteError: CommentError.Delete? = null,
    /** True while the plate share card is rasterizing (decode + render); shows a spinner. */
    val isPreparingShare: Boolean = false,
    /** Transient share-outcome toast; cleared via [MealDetailIntent.DismissShareOutcome]. */
    val shareOutcome: ShareOutcomeUi? = null,
    // UGC compliance §4/§5 — report + block on the meal author, the meal, or a comment.
    /** When non-null, the report sheet is open against this target. */
    val reportTarget: ReportTargetUi? = null,
    /** True while a report submit is in flight (disables the sheet). */
    val reportSubmitting: Boolean = false,
    /** Transient success toast shown after a report is accepted. */
    val reportSuccess: Boolean = false,
    val reportError: ReportError? = null,
    val blockError: es.schsebastian.foodrats.core.domain.account.BlockError? = null,
) : MviState

/**
 * What the open report sheet targets (UGC compliance §4). Carries only the identifiers the ViewModel
 * needs to build the domain `ReportTarget` — the author id (for a meal/user report's self-report guard
 * and for an account report) and, for a comment, the comment id.
 */
sealed interface ReportTargetUi {
    /** Report the meal itself. */
    data object Meal : ReportTargetUi
    /** Report the meal's author (account-level). */
    data object Author : ReportTargetUi
    /** Report a specific comment. */
    data class Comment(val commentId: MealCommentId) : ReportTargetUi
}

/** Presentation mirror of `StoryShareOutcome` → which toast the screen shows (spec §10). */
enum class ShareOutcomeUi { Succeeded, OpenedSheet, Failed }

sealed interface MealDetailIntent : MviIntent {
    data class RateMeal(val score: Int) : MealDetailIntent
    data object DismissError : MealDetailIntent
    data class CommentInputChanged(val value: String) : MealDetailIntent
    data object PostComment : MealDetailIntent
    data object DeleteMeal : MealDetailIntent
    data class DeleteComment(val id: MealCommentId) : MealDetailIntent

    /** Share the displayed plate to Instagram Stories. */
    data object ShareTapped : MealDetailIntent
    data object DismissShareOutcome : MealDetailIntent

    // UGC compliance §4/§5 — report + block.
    /** Open the report sheet against [target] (meal / author / comment). */
    data class OpenReport(val target: ReportTargetUi) : MealDetailIntent
    /** Submit the open report with the chosen reason. */
    data class SubmitReport(val reason: FrReportReasonOption) : MealDetailIntent
    /** Dismiss the report sheet without submitting. */
    data object DismissReport : MealDetailIntent
    /** Clear the transient report-success toast. */
    data object DismissReportSuccess : MealDetailIntent
    /** Block the meal's author; their content disappears reactively. */
    data object BlockAuthor : MealDetailIntent
    /** Block a comment's author by id. */
    data class BlockCommentAuthor(val commentAuthorId: String) : MealDetailIntent
}

sealed interface MealDetailEffect : MviEffect
