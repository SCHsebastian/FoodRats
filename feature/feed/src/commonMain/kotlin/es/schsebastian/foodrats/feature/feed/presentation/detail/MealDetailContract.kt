package es.schsebastian.foodrats.feature.feed.presentation.detail

import es.schsebastian.foodrats.core.designsystem.molecules.FrReportReasonOption
import es.schsebastian.foodrats.core.designsystem.molecules.FrScoreStyle
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
    /** True while the "this is your last change" confirmation dialog is showing. */
    val showChangeVoteConfirm: Boolean = false,
    /**
     * True once the viewer has confirmed they want to change their already-cast vote: the locked
     * "Your vote" tile is replaced by the picker (pre-filled with the current score) so they can
     * re-pick. Reset on a successful re-rate (the new vote is then `edited` and locks permanently).
     */
    val voteEditMode: Boolean = false,
    val commentRows: List<CommentRowUi> = emptyList(),
    val commentsLoading: Boolean = true,
    val commentReadError: CommentError.Read? = null,
    val commentInput: String = "",
    val isPostingComment: Boolean = false,
    val commentWriteError: CommentError.Write? = null,
    /** Id of the comment currently being edited inline; `null` when no row is in edit mode. */
    val editingCommentId: MealCommentId? = null,
    /** In-progress edited text for [editingCommentId]. */
    val commentEditInput: String = "",
    /** True while an edit save is in flight. */
    val isEditingComment: Boolean = false,
    val commentEditError: CommentError.Edit? = null,
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
    /** Transient success toast shown after a block succeeds. */
    val blockSuccess: Boolean = false,
    /**
     * Active crew's chosen Score display vocabulary (C8). Defaults to [FrScoreStyle.Stars] for
     * pre-C8 crews. Drives the voting picker so it matches the feed meal-card badge.
     */
    val scoreStyle: FrScoreStyle = FrScoreStyle.Stars,
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

    /** Viewer tapped "Change my vote" — opens the one-time-only confirmation dialog. */
    data object RequestChangeVote : MealDetailIntent
    /** Viewer confirmed the change in the dialog — reveals the picker, pre-filled. */
    data object ConfirmChangeVote : MealDetailIntent
    /** Viewer dismissed the change-vote dialog without confirming. */
    data object CancelChangeVote : MealDetailIntent

    data object DismissError : MealDetailIntent
    data class CommentInputChanged(val value: String) : MealDetailIntent
    data object PostComment : MealDetailIntent

    /** Enter inline edit mode for the viewer's own comment, pre-filling the field with its text. */
    data class StartEditComment(val id: MealCommentId) : MealDetailIntent
    /** Update the in-progress edited text. */
    data class EditCommentInputChanged(val value: String) : MealDetailIntent
    /** Leave edit mode without saving. */
    data object CancelEditComment : MealDetailIntent
    /** Save the edited text for the comment currently in edit mode. */
    data object SubmitEditComment : MealDetailIntent

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
    /** Clear the transient block-success toast. */
    data object DismissBlockSuccess : MealDetailIntent
}

sealed interface MealDetailEffect : MviEffect
