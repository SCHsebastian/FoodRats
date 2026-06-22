package es.schsebastian.foodrats.feature.feed.i18n

import es.schsebastian.foodrats.core.i18n.StringKey
import foodrats.feature.feed.generated.resources.Res
import foodrats.feature.feed.generated.resources.feed_comments_error_blank
import foodrats.feature.feed.generated.resources.feed_comments_error_objectionable
import foodrats.feature.feed.generated.resources.feed_comments_error_too_long
import foodrats.feature.feed.generated.resources.feed_comments_error_unauthorized
import foodrats.feature.feed.generated.resources.feed_comments_error_unavailable
import foodrats.feature.feed.generated.resources.feed_report_meal_cta
import foodrats.feature.feed.generated.resources.feed_report_user_cta
import foodrats.feature.feed.generated.resources.feed_report_comment_cta
import foodrats.feature.feed.generated.resources.feed_block_author_cta
import foodrats.feature.feed.generated.resources.feed_block_user_cta
import foodrats.feature.feed.generated.resources.feed_block_confirm_title
import foodrats.feature.feed.generated.resources.feed_block_confirm_body
import foodrats.feature.feed.generated.resources.feed_block_confirm_cta
import foodrats.feature.feed.generated.resources.feed_report_success
import foodrats.feature.feed.generated.resources.feed_block_success

import foodrats.feature.feed.generated.resources.feed_overflow_menu_cd
import foodrats.feature.feed.generated.resources.feed_report_submit_meal
import foodrats.feature.feed.generated.resources.feed_report_submit_user
import foodrats.feature.feed.generated.resources.feed_report_submit_comment
import foodrats.feature.feed.generated.resources.feed_report_error_already_reported
import foodrats.feature.feed.generated.resources.feed_report_error_unavailable
import foodrats.feature.feed.generated.resources.feed_block_error_unavailable
import foodrats.feature.feed.generated.resources.feed_report_reason_spam
import foodrats.feature.feed.generated.resources.feed_report_reason_harassment
import foodrats.feature.feed.generated.resources.feed_report_reason_hate
import foodrats.feature.feed.generated.resources.feed_report_reason_sexual
import foodrats.feature.feed.generated.resources.feed_report_reason_violence
import foodrats.feature.feed.generated.resources.feed_report_reason_other
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
import foodrats.feature.feed.generated.resources.feed_delete_meal_error_not_found
import foodrats.feature.feed.generated.resources.feed_delete_meal_error_unauthorized
import foodrats.feature.feed.generated.resources.feed_delete_meal_error_unavailable
import foodrats.feature.feed.generated.resources.feed_average_heading
import foodrats.feature.feed.generated.resources.feed_blind_author
import foodrats.feature.feed.generated.resources.feed_reaction_count
import foodrats.feature.feed.generated.resources.feed_reaction_cta
import foodrats.feature.feed.generated.resources.feed_reaction_error_meal_not_found
import foodrats.feature.feed.generated.resources.feed_reaction_error_offline
import foodrats.feature.feed.generated.resources.feed_reaction_error_unauthorized
import foodrats.feature.feed.generated.resources.feed_reaction_error_unavailable
import foodrats.feature.feed.generated.resources.feed_detail_back_cta
import foodrats.feature.feed.generated.resources.feed_detail_not_found
import foodrats.feature.feed.generated.resources.feed_detail_title
import foodrats.feature.feed.generated.resources.feed_empty_headline
import foodrats.feature.feed.generated.resources.feed_empty_subtext
import foodrats.feature.feed.generated.resources.feed_empty_subtext_past
import foodrats.feature.feed.generated.resources.feed_ingredient_separator
import foodrats.feature.feed.generated.resources.feed_ingredients_heading
import foodrats.feature.feed.generated.resources.feed_more_ingredients
import foodrats.feature.feed.generated.resources.feed_error_crew_not_found
import foodrats.feature.feed.generated.resources.feed_error_not_signed_in
import foodrats.feature.feed.generated.resources.feed_error_unauthorized
import foodrats.feature.feed.generated.resources.feed_error_unavailable
import foodrats.feature.feed.generated.resources.feed_next_day
import foodrats.feature.feed.generated.resources.feed_no_active_crew_headline
import foodrats.feature.feed.generated.resources.feed_no_active_crew_subtext
import foodrats.feature.feed.generated.resources.feed_pick_crew_cta
import foodrats.feature.feed.generated.resources.feed_no_votes_yet
import foodrats.feature.feed.generated.resources.feed_prev_day
import foodrats.feature.feed.generated.resources.feed_queue_dismiss_cta
import foodrats.feature.feed.generated.resources.feed_queue_failed
import foodrats.feature.feed.generated.resources.feed_queue_pending
import foodrats.feature.feed.generated.resources.feed_queue_retry_cta
import foodrats.feature.feed.generated.resources.feed_rating_summary
import foodrats.feature.feed.generated.resources.feed_share_meal
import foodrats.feature.feed.generated.resources.feed_sync_failed
import foodrats.feature.feed.generated.resources.feed_sync_pending
import foodrats.feature.feed.generated.resources.feed_synced_ago
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
import foodrats.feature.feed.generated.resources.feed_your_crew_eyebrow
import foodrats.feature.feed.generated.resources.feed_crew_settings_cd
import foodrats.feature.feed.generated.resources.feed_votes_heading
import foodrats.feature.feed.generated.resources.feed_voters_label
import foodrats.feature.feed.generated.resources.feed_voter_score_compact
import foodrats.feature.feed.generated.resources.feed_crew_score_label
import foodrats.feature.feed.generated.resources.feed_location_label
import foodrats.feature.feed.generated.resources.feed_location_map_cta
import foodrats.feature.feed.generated.resources.feed_welcome_dismiss
import foodrats.feature.feed.generated.resources.feed_weekly_challenge_label
import foodrats.feature.feed.generated.resources.feed_your_vote
import foodrats.feature.feed.generated.resources.feed_your_vote_locked
import foodrats.feature.feed.generated.resources.feed_your_vote_glyph_free
import foodrats.feature.feed.generated.resources.feed_badge_first
import foodrats.feature.feed.generated.resources.feed_badge_ten
import foodrats.feature.feed.generated.resources.feed_badge_fifty
import foodrats.feature.feed.generated.resources.feed_badge_hundred
import foodrats.feature.feed.generated.resources.feed_voter_score_glyph_free
import foodrats.feature.feed.generated.resources.feed_crew_banner_cd
import foodrats.feature.feed.generated.resources.feed_crew_banner_close_cd
import foodrats.feature.feed.generated.resources.feed_meal_photo_cd
import foodrats.feature.feed.generated.resources.feed_meal_photo_close_cd
import foodrats.feature.feed.generated.resources.feed_meal_photo_open_cd
import org.jetbrains.compose.resources.StringResource

enum class FeedStringKey(override val resourceId: StringResource) : StringKey {
    Title(Res.string.feed_title),
    YourCrewEyebrow(Res.string.feed_your_crew_eyebrow),
    CrewSettingsCd(Res.string.feed_crew_settings_cd),
    EmptyHeadline(Res.string.feed_empty_headline),
    EmptySubtext(Res.string.feed_empty_subtext),
    EmptySubtextPast(Res.string.feed_empty_subtext_past),
    PrevDay(Res.string.feed_prev_day),
    NextDay(Res.string.feed_next_day),
    NoActiveCrewHeadline(Res.string.feed_no_active_crew_headline),
    NoActiveCrewSubtext(Res.string.feed_no_active_crew_subtext),
    PickCrewCta(Res.string.feed_pick_crew_cta),
    Yesterday(Res.string.feed_yesterday),
    TimeOfDay(Res.string.feed_time_of_day),
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
    ReactionErrorOffline(Res.string.feed_reaction_error_offline),
    ReactionErrorUnauthorized(Res.string.feed_reaction_error_unauthorized),
    ReactionErrorMealNotFound(Res.string.feed_reaction_error_meal_not_found),
    ReactionErrorUnavailable(Res.string.feed_reaction_error_unavailable),
    AverageHeading(Res.string.feed_average_heading),
    RateThisMeal(Res.string.feed_rate_this_meal),
    YourVote(Res.string.feed_your_vote),
    YourVoteLockedEyebrow(Res.string.feed_your_vote_locked),
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
    MoreIngredients(Res.string.feed_more_ingredients),
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
    // UGC compliance §3 — comment blocked by the on-device objectionable-text filter.
    CommentsErrorObjectionable(Res.string.feed_comments_error_objectionable),
    // UGC compliance §4/§5 — report + block actions on meal detail and comment rows.
    ReportMealCta(Res.string.feed_report_meal_cta),
    ReportUserCta(Res.string.feed_report_user_cta),
    ReportCommentCta(Res.string.feed_report_comment_cta),
    BlockAuthorCta(Res.string.feed_block_author_cta),
    BlockUserCta(Res.string.feed_block_user_cta),
    BlockConfirmTitle(Res.string.feed_block_confirm_title),
    BlockConfirmBody(Res.string.feed_block_confirm_body),
    BlockConfirmCta(Res.string.feed_block_confirm_cta),
    ReportSuccess(Res.string.feed_report_success),
    BlockSuccess(Res.string.feed_block_success),
    // ReportError / BlockError → user message (feed owns the report+block UI surface here).
    ReportErrorAlreadyReported(Res.string.feed_report_error_already_reported),
    ReportErrorUnavailable(Res.string.feed_report_error_unavailable),
    BlockErrorUnavailable(Res.string.feed_block_error_unavailable),
    // Report-reason labels for the FrReportSheet.
    ReportReasonSpam(Res.string.feed_report_reason_spam),
    ReportReasonHarassment(Res.string.feed_report_reason_harassment),
    ReportReasonHate(Res.string.feed_report_reason_hate),
    ReportReasonSexual(Res.string.feed_report_reason_sexual),
    ReportReasonViolence(Res.string.feed_report_reason_violence),
    ReportReasonOther(Res.string.feed_report_reason_other),
    DeletedAuthor(Res.string.feed_deleted_author),
    DeleteMealCta(Res.string.feed_delete_meal_cta),
    DeleteMealConfirmTitle(Res.string.feed_delete_meal_confirm_title),
    DeleteMealConfirmBody(Res.string.feed_delete_meal_confirm_body),
    DeleteMealErrorUnauthorized(Res.string.feed_delete_meal_error_unauthorized),
    DeleteMealErrorNotFound(Res.string.feed_delete_meal_error_not_found),
    DeleteMealErrorUnavailable(Res.string.feed_delete_meal_error_unavailable),
    DeleteCommentCta(Res.string.feed_delete_comment_cta),
    DeleteCommentConfirmTitle(Res.string.feed_delete_comment_confirm_title),
    DeleteConfirmCta(Res.string.feed_delete_confirm_cta),
    DeleteCancelCta(Res.string.feed_delete_cancel_cta),
    QueuePending(Res.string.feed_queue_pending),
    QueueFailed(Res.string.feed_queue_failed),
    QueueRetryCta(Res.string.feed_queue_retry_cta),
    QueueDismissCta(Res.string.feed_queue_dismiss_cta),
    SyncPending(Res.string.feed_sync_pending),
    SyncFailed(Res.string.feed_sync_failed),
    SyncedAgo(Res.string.feed_synced_ago),
    // Feed card overflow menu (UGC compliance §4/§5).
    OverflowMenuCd(Res.string.feed_overflow_menu_cd),
    // Report-sheet submit labels per target type (UGC compliance §4 Item 5).
    ReportSubmitMeal(Res.string.feed_report_submit_meal),
    ReportSubmitUser(Res.string.feed_report_submit_user),
    ReportSubmitComment(Res.string.feed_report_submit_comment),
    // C6 — pinned crew welcome banner dismiss button.
    WelcomeDismiss(Res.string.feed_welcome_dismiss),
    // C5 — weekly challenge chip label prefix shown in the feed header.
    WeeklyChallengeLabel(Res.string.feed_weekly_challenge_label),
    // U5b — author badge labels rendered next to the author name on feed rows.
    BadgeFirst(Res.string.feed_badge_first),
    BadgeTen(Res.string.feed_badge_ten),
    BadgeFifty(Res.string.feed_badge_fifty),
    BadgeHundred(Res.string.feed_badge_hundred),
    // C8b — glyph-free voter-row score used when scoreStyle ≠ Stars on the detail screen.
    // Takes %1$s = pre-rendered score string (e.g. "😋", "3").
    VoterScoreGlyphFree(Res.string.feed_voter_score_glyph_free),
    // C8b — glyph-free "your vote" used on the feed card when scoreStyle ≠ Stars, so the
    // viewer's own vote doesn't render a ★ next to the crew's emoji/numeric style.
    // Takes %1$s = pre-rendered score string (e.g. "😍", "4").
    YourVoteGlyphFree(Res.string.feed_your_vote_glyph_free),
    // C9 — clickable crew banner hero + the close button of its full-screen viewer.
    CrewBannerCd(Res.string.feed_crew_banner_cd),
    CrewBannerCloseCd(Res.string.feed_crew_banner_close_cd),
    MealPhotoOpenCd(Res.string.feed_meal_photo_open_cd),
    MealPhotoCd(Res.string.feed_meal_photo_cd),
    MealPhotoCloseCd(Res.string.feed_meal_photo_close_cd),
}
