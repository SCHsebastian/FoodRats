package es.schsebastian.foodrats.feature.achievements.domain.model

import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.model.AccountId

/**
 * Everything the (pure) evaluator needs, already resolved from ports by the caller. Keeping this a
 * plain data class makes every criterion unit-testable against a hand-built `List<MealWithRatings>`.
 * (spec §5.4)
 *
 * @property accountId the member whose achievements are being evaluated.
 * @property crewMeals the active crew's window (whole crew — `Personal` criteria filter to
 *   [accountId] inside the evaluator).
 * @property personalStreakDays the member's current personal streak in days (derived; spec §7).
 * @property crewStreakDays the crew's current shared streak in days (derived; spec §7).
 * @property bestCookAccountId who currently leads average score (min 3 plates), or null if no one
 *   qualifies.
 */
data class AchievementSignals(
    val accountId: AccountId,
    val crewMeals: List<MealWithRatings>,
    val personalStreakDays: Int,
    val crewStreakDays: Int,
    val bestCookAccountId: AccountId?,
)
