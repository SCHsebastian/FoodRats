package es.schsebastian.foodrats.feature.achievements.presentation

import es.schsebastian.foodrats.feature.achievements.domain.error.AchievementError
import es.schsebastian.foodrats.feature.achievements.i18n.AchievementStringKey

/**
 * Exhaustive `when` mapping every [AchievementError] leaf to its [AchievementStringKey]. A new error
 * leaf forces a compile error here, locked by `AchievementErrorToStringKeyTest`. (spec §11)
 */
fun AchievementError.toStringKey(): AchievementStringKey = when (this) {
    AchievementError.Session.NotSignedIn -> AchievementStringKey.ErrorNotSignedIn
    AchievementError.Session.NoActiveCrew -> AchievementStringKey.ErrorNoActiveCrew
    AchievementError.Read.Unauthorized -> AchievementStringKey.ErrorUnauthorized
    AchievementError.Read.Unavailable -> AchievementStringKey.ErrorUnavailable
}
