package es.schsebastian.foodrats.core.domain.achievement

/**
 * Typed failures of [AchievementProgressPort] (spec §6.1). Sealed interface with `data object`
 * leaves — never an enum — so a payload can be attached later without a breaking change. The
 * feature folds these into `AchievementError.Read.*`.
 */
sealed interface AchievementProgressError {
    data object Unauthorized : AchievementProgressError
    data object Unavailable : AchievementProgressError
}
