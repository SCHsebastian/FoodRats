package es.schsebastian.foodrats.feature.achievements.domain.error

/**
 * The feature's typed failures (sealed, never enum — keeps the door open to payloads). [Session]
 * covers the absence of an account / active crew; [Read] covers port read/write failures mapped in
 * from `MealReadError` / `AchievementProgressError`. (spec §11)
 */
sealed interface AchievementError {
    sealed interface Session : AchievementError {
        data object NotSignedIn : Session
        data object NoActiveCrew : Session
    }

    sealed interface Read : AchievementError {
        data object Unauthorized : Read
        data object Unavailable : Read
    }
}
