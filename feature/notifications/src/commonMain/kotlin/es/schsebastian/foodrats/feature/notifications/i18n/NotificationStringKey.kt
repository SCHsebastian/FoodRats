package es.schsebastian.foodrats.feature.notifications.i18n

import es.schsebastian.foodrats.core.i18n.StringKey
import foodrats.feature.notifications.generated.resources.Res
import foodrats.feature.notifications.generated.resources.notifications_channel_streak_nudges
import foodrats.feature.notifications.generated.resources.notifications_comment_mention_body
import foodrats.feature.notifications.generated.resources.notifications_comment_mention_title
import foodrats.feature.notifications.generated.resources.notifications_error_denied
import foodrats.feature.notifications.generated.resources.notifications_error_denied_forever
import foodrats.feature.notifications.generated.resources.notifications_error_payload_parse
import foodrats.feature.notifications.generated.resources.notifications_error_schedule_failed
import foodrats.feature.notifications.generated.resources.notifications_error_token_persist
import foodrats.feature.notifications.generated.resources.notifications_error_token_unavailable
import foodrats.feature.notifications.generated.resources.notifications_error_unavailable
import foodrats.feature.notifications.generated.resources.notifications_inactivity_body
import foodrats.feature.notifications.generated.resources.notifications_inactivity_title
import foodrats.feature.notifications.generated.resources.notifications_new_comment_body
import foodrats.feature.notifications.generated.resources.notifications_new_comment_title
import foodrats.feature.notifications.generated.resources.notifications_new_meal_post_body
import foodrats.feature.notifications.generated.resources.notifications_new_meal_post_title
import foodrats.feature.notifications.generated.resources.notifications_permission_allow
import foodrats.feature.notifications.generated.resources.notifications_permission_body
import foodrats.feature.notifications.generated.resources.notifications_permission_save_failed
import foodrats.feature.notifications.generated.resources.notifications_permission_settings
import foodrats.feature.notifications.generated.resources.notifications_permission_skip
import foodrats.feature.notifications.generated.resources.notifications_permission_title
import foodrats.feature.notifications.generated.resources.notifications_social_nudge_body
import foodrats.feature.notifications.generated.resources.notifications_social_nudge_title
import foodrats.feature.notifications.generated.resources.notifications_weekly_digest_body
import foodrats.feature.notifications.generated.resources.notifications_weekly_digest_title
import org.jetbrains.compose.resources.StringResource

enum class NotificationStringKey(override val resourceId: StringResource) : StringKey {
    PermissionTitle(Res.string.notifications_permission_title),
    PermissionBody(Res.string.notifications_permission_body),
    PermissionAllow(Res.string.notifications_permission_allow),
    PermissionSkip(Res.string.notifications_permission_skip),
    PermissionSettings(Res.string.notifications_permission_settings),
    PermissionSaveFailed(Res.string.notifications_permission_save_failed),
    InactivityTitle(Res.string.notifications_inactivity_title),
    InactivityBody(Res.string.notifications_inactivity_body),
    NewCommentTitle(Res.string.notifications_new_comment_title),
    NewCommentBody(Res.string.notifications_new_comment_body),
    CommentMentionTitle(Res.string.notifications_comment_mention_title),
    CommentMentionBody(Res.string.notifications_comment_mention_body),
    NewMealPostTitle(Res.string.notifications_new_meal_post_title),
    NewMealPostBody(Res.string.notifications_new_meal_post_body),
    WeeklyDigestTitle(Res.string.notifications_weekly_digest_title),
    WeeklyDigestBody(Res.string.notifications_weekly_digest_body),
    SocialNudgeTitle(Res.string.notifications_social_nudge_title),
    SocialNudgeBody(Res.string.notifications_social_nudge_body),
    ErrorDenied(Res.string.notifications_error_denied),
    ErrorDeniedForever(Res.string.notifications_error_denied_forever),
    ErrorUnavailable(Res.string.notifications_error_unavailable),
    ErrorTokenUnavailable(Res.string.notifications_error_token_unavailable),
    ErrorTokenPersist(Res.string.notifications_error_token_persist),
    ErrorScheduleFailed(Res.string.notifications_error_schedule_failed),
    ErrorPayloadParse(Res.string.notifications_error_payload_parse),
    ChannelStreakNudges(Res.string.notifications_channel_streak_nudges),
}
