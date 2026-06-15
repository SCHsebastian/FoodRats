package es.schsebastian.foodrats.feature.feed.i18n

import es.schsebastian.foodrats.core.i18n.StringKey
import foodrats.feature.feed.generated.resources.Res
import foodrats.feature.feed.generated.resources.feed_comments_error_blank
import foodrats.feature.feed.generated.resources.feed_comments_error_too_long
import foodrats.feature.feed.generated.resources.feed_comments_error_unauthorized
import foodrats.feature.feed.generated.resources.feed_comments_error_unavailable
import foodrats.feature.feed.generated.resources.feed_comments_empty
import foodrats.feature.feed.generated.resources.feed_comments_input_placeholder
import foodrats.feature.feed.generated.resources.feed_comments_relative_days
import foodrats.feature.feed.generated.resources.feed_comments_relative_hours
import foodrats.feature.feed.generated.resources.feed_comments_relative_just_now
import foodrats.feature.feed.generated.resources.feed_comments_relative_minutes
import foodrats.feature.feed.generated.resources.feed_comments_send_cta
import foodrats.feature.feed.generated.resources.feed_comments_title
import foodrats.feature.feed.generated.resources.feed_deleted_author
import foodrats.feature.feed.generated.resources.feed_delete_cancel_cta
import foodrats.feature.feed.generated.resources.feed_delete_comment_confirm_title
import foodrats.feature.feed.generated.resources.feed_delete_comment_cta
import foodrats.feature.feed.generated.resources.feed_delete_confirm_cta
import foodrats.feature.feed.generated.resources.feed_delete_meal_confirm_body
import foodrats.feature.feed.generated.resources.feed_delete_meal_confirm_title
import foodrats.feature.feed.generated.resources.feed_delete_meal_cta
import foodrats.feature.feed.generated.resources.feed_average_heading
import foodrats.feature.feed.generated.resources.feed_blind_author
import foodrats.feature.feed.generated.resources.feed_reaction_count
import foodrats.feature.feed.generated.resources.feed_reaction_cta
import foodrats.feature.feed.generated.resources.feed_reaction_error_meal_not_found
import foodrats.feature.feed.generated.resources.feed_reaction_error_offline
import foodrats.feature.feed.generated.resources.feed_reaction_error_unauthorized
import foodrats.feature.feed.generated.resources.feed_reaction_error_unavailable
import foodrats.feature.feed.generated.resources.feed_reactions_label
import foodrats.feature.feed.generated.resources.feed_detail_back_cta
import foodrats.feature.feed.generated.resources.feed_detail_not_found
import foodrats.feature.feed.generated.resources.feed_detail_title
import foodrats.feature.feed.generated.resources.feed_empty_headline
import foodrats.feature.feed.generated.resources.feed_empty_subtext
import foodrats.feature.feed.generated.resources.feed_ingredient_separator
import foodrats.feature.feed.generated.resources.feed_ingredients_heading
import foodrats.feature.feed.generated.resources.feed_error_crew_not_found
import foodrats.feature.feed.generated.resources.feed_error_not_signed_in
import foodrats.feature.feed.generated.resources.feed_error_unauthorized
import foodrats.feature.feed.generated.resources.feed_error_unavailable
import foodrats.feature.feed.generated.resources.feed_next_day
import foodrats.feature.feed.generated.resources.feed_no_active_crew_headline
import foodrats.feature.feed.generated.resources.feed_no_active_crew_subtext
import foodrats.feature.feed.generated.resources.feed_pick_crew_cta
import foodrats.feature.feed.generated.resources.feed_no_votes_yet
import foodrats.feature.feed.generated.resources.feed_plates_count
import foodrats.feature.feed.generated.resources.feed_prev_day
import foodrats.feature.feed.generated.resources.feed_queue_dismiss_cta
import foodrats.feature.feed.generated.resources.feed_queue_failed
import foodrats.feature.feed.generated.resources.feed_queue_pending
import foodrats.feature.feed.generated.resources.feed_queue_retry_cta
import foodrats.feature.feed.generated.resources.feed_rating_summary
import foodrats.feature.feed.generated.resources.feed_rating_summary_votes
import foodrats.feature.feed.generated.resources.feed_share_meal
import foodrats.feature.feed.generated.resources.feed_slot_breakfast
import foodrats.feature.feed.generated.resources.feed_slot_dinner
import foodrats.feature.feed.generated.resources.feed_slot_lunch
import foodrats.feature.feed.generated.resources.feed_time_of_day
import foodrats.feature.feed.generated.resources.feed_yesterday
import foodrats.feature.feed.generated.resources.feed_voter_score
import foodrats.feature.feed.generated.resources.feed_rate_error_already_rated
import foodrats.feature.feed.generated.resources.feed_rate_error_cannot_rate_own_meal
import foodrats.feature.feed.generated.resources.feed_rate_error_offline
import foodrats.feature.feed.generated.resources.feed_rate_error_unauthorized
import foodrats.feature.feed.generated.resources.feed_rate_error_unavailable
import foodrats.feature.feed.generated.resources.feed_rate_error_window_closed
import foodrats.feature.feed.generated.resources.feed_rate_this_meal
import foodrats.feature.feed.generated.resources.feed_title
import foodrats.feature.feed.generated.resources.feed_votes_heading
import foodrats.feature.feed.generated.resources.feed_voters_label
import foodrats.feature.feed.generated.resources.feed_voter_score_compact
import foodrats.feature.feed.generated.resources.feed_crew_score_label
import foodrats.feature.feed.generated.resources.feed_location_label
import foodrats.feature.feed.generated.resources.feed_location_map_cta
import foodrats.feature.feed.generated.resources.feed_your_vote
import org.jetbrains.compose.resources.StringResource

enum class FeedStringKey(override val resourceId: StringResource) : StringKey {
    Title(Res.string.feed_title),
    EmptyHeadline(Res.string.feed_empty_headline),
    EmptySubtext(Res.string.feed_empty_subtext),
    PrevDay(Res.string.feed_prev_day),
    NextDay(Res.string.feed_next_day),
    NoActiveCrewHeadline(Res.string.feed_no_active_crew_headline),
    NoActiveCrewSubtext(Res.string.feed_no_active_crew_subtext),
    PickCrewCta(Res.string.feed_pick_crew_cta),
    Yesterday(Res.string.feed_yesterday),
    PlatesCount(Res.string.feed_plates_count),
    TimeOfDay(Res.string.feed_time_of_day),
    RatingSummaryVotes(Res.string.feed_rating_summary_votes),
    SlotBreakfast(Res.string.feed_slot_breakfast),
    SlotLunch(Res.string.feed_slot_lunch),
    SlotDinner(Res.string.feed_slot_dinner),
    ErrorNotSignedIn(Res.string.feed_error_not_signed_in),
    ErrorUnauthorized(Res.string.feed_error_unauthorized),
    ErrorCrewNotFound(Res.string.feed_error_crew_not_found),
    ErrorUnavailable(Res.string.feed_error_unavailable),
    NoVotesYet(Res.string.feed_no_votes_yet),
    BlindAuthor(Res.string.feed_blind_author),
    ReactionCta(Res.string.feed_reaction_cta),
    ReactionCount(Res.string.feed_reaction_count),
    ReactionsLabel(Res.string.feed_reactions_label),
    ReactionErrorOffline(Res.string.feed_reaction_error_offline),
    ReactionErrorUnauthorized(Res.string.feed_reaction_error_unauthorized),
    ReactionErrorMealNotFound(Res.string.feed_reaction_error_meal_not_found),
    ReactionErrorUnavailable(Res.string.feed_reaction_error_unavailable),
    AverageHeading(Res.string.feed_average_heading),
    RateThisMeal(Res.string.feed_rate_this_meal),
    YourVote(Res.string.feed_your_vote),
    RatingSummary(Res.string.feed_rating_summary),
    VoterScore(Res.string.feed_voter_score),
    VotesHeading(Res.string.feed_votes_heading),
    VotersLabel(Res.string.feed_voters_label),
    VoterScoreCompact(Res.string.feed_voter_score_compact),
    CrewScoreLabel(Res.string.feed_crew_score_label),
    LocationLabel(Res.string.feed_location_label),
    LocationMapCta(Res.string.feed_location_map_cta),
    IngredientsHeading(Res.string.feed_ingredients_heading),
    IngredientSeparator(Res.string.feed_ingredient_separator),
    RateErrorCannotRateOwnMeal(Res.string.feed_rate_error_cannot_rate_own_meal),
    RateErrorAlreadyRated(Res.string.feed_rate_error_already_rated),
    RateErrorWindowClosed(Res.string.feed_rate_error_window_closed),
    RateErrorUnauthorized(Res.string.feed_rate_error_unauthorized),
    RateErrorOffline(Res.string.feed_rate_error_offline),
    RateErrorUnavailable(Res.string.feed_rate_error_unavailable),
    ShareMeal(Res.string.feed_share_meal),
    DetailBackCta(Res.string.feed_detail_back_cta),
    DetailTitle(Res.string.feed_detail_title),
    DetailNotFound(Res.string.feed_detail_not_found),
    CommentsTitle(Res.string.feed_comments_title),
    CommentsEmpty(Res.string.feed_comments_empty),
    CommentsInputPlaceholder(Res.string.feed_comments_input_placeholder),
    CommentsSendCta(Res.string.feed_comments_send_cta),
    CommentsRelativeJustNow(Res.string.feed_comments_relative_just_now),
    CommentsRelativeMinutes(Res.string.feed_comments_relative_minutes),
    CommentsRelativeHours(Res.string.feed_comments_relative_hours),
    CommentsRelativeDays(Res.string.feed_comments_relative_days),
    CommentsErrorBlank(Res.string.feed_comments_error_blank),
    CommentsErrorTooLong(Res.string.feed_comments_error_too_long),
    CommentsErrorUnavailable(Res.string.feed_comments_error_unavailable),
    CommentsErrorUnauthorized(Res.string.feed_comments_error_unauthorized),
    DeletedAuthor(Res.string.feed_deleted_author),
    DeleteMealCta(Res.string.feed_delete_meal_cta),
    DeleteMealConfirmTitle(Res.string.feed_delete_meal_confirm_title),
    DeleteMealConfirmBody(Res.string.feed_delete_meal_confirm_body),
    DeleteCommentCta(Res.string.feed_delete_comment_cta),
    DeleteCommentConfirmTitle(Res.string.feed_delete_comment_confirm_title),
    DeleteConfirmCta(Res.string.feed_delete_confirm_cta),
    DeleteCancelCta(Res.string.feed_delete_cancel_cta),
    QueuePending(Res.string.feed_queue_pending),
    QueueFailed(Res.string.feed_queue_failed),
    QueueRetryCta(Res.string.feed_queue_retry_cta),
    QueueDismissCta(Res.string.feed_queue_dismiss_cta),
}
