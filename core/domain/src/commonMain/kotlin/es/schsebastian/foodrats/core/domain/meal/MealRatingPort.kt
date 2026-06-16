package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result

interface MealRatingPort {
    /**
     * Records [raterId]'s [score] for [mealId]. Self-vote / already-rated are enforced
     * authoritatively by the Firestore transaction + rules (concurrency backstop); this
     * port carries [raterId] so the rating identity is explicit in the domain contract
     * rather than implied by ambient auth state inside the adapter.
     */
    suspend fun rate(
        crewId: CrewId,
        mealId: MealId,
        raterId: AccountId,
        score: Score,
    ): Result<Unit, RateError>
}

sealed interface RateError {
    data object Unauthorized       : RateError
    data object CannotRateOwnMeal  : RateError
    data object AlreadyRated       : RateError
    data object RatingWindowClosed : RateError
    data object Offline            : RateError
    data object RateUnavailable    : RateError
}
