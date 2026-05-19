package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result

interface MealRatingPort {
    suspend fun rate(crewId: CrewId, mealId: MealId, score: Score): Result<Unit, RateError>
}

sealed interface RateError {
    data object Unauthorized       : RateError
    data object CannotRateOwnMeal  : RateError
    data object AlreadyRated       : RateError
    data object RatingWindowClosed : RateError
    data object RateUnavailable    : RateError
}
