package es.schsebastian.foodrats.feature.notifications.i18n

import es.schsebastian.foodrats.core.i18n.StringKey
import foodrats.feature.notifications.generated.resources.Res
import foodrats.feature.notifications.generated.resources.notifications_error_denied
import foodrats.feature.notifications.generated.resources.notifications_error_denied_forever
import foodrats.feature.notifications.generated.resources.notifications_error_schedule_failed
import foodrats.feature.notifications.generated.resources.notifications_error_token_persist
import foodrats.feature.notifications.generated.resources.notifications_error_token_unavailable
import foodrats.feature.notifications.generated.resources.notifications_error_unavailable
import foodrats.feature.notifications.generated.resources.notifications_permission_allow
import foodrats.feature.notifications.generated.resources.notifications_permission_body
import foodrats.feature.notifications.generated.resources.notifications_permission_settings
import foodrats.feature.notifications.generated.resources.notifications_permission_skip
import foodrats.feature.notifications.generated.resources.notifications_permission_title
import foodrats.feature.notifications.generated.resources.notifications_streak_body
import foodrats.feature.notifications.generated.resources.notifications_streak_title
import org.jetbrains.compose.resources.StringResource

enum class NotificationStringKey(override val resourceId: StringResource) : StringKey {
    PermissionTitle(Res.string.notifications_permission_title),
    PermissionBody(Res.string.notifications_permission_body),
    PermissionAllow(Res.string.notifications_permission_allow),
    PermissionSkip(Res.string.notifications_permission_skip),
    PermissionSettings(Res.string.notifications_permission_settings),
    StreakTitle(Res.string.notifications_streak_title),
    StreakBody(Res.string.notifications_streak_body),
    ErrorDenied(Res.string.notifications_error_denied),
    ErrorDeniedForever(Res.string.notifications_error_denied_forever),
    ErrorUnavailable(Res.string.notifications_error_unavailable),
    ErrorTokenUnavailable(Res.string.notifications_error_token_unavailable),
    ErrorTokenPersist(Res.string.notifications_error_token_persist),
    ErrorScheduleFailed(Res.string.notifications_error_schedule_failed),
}
