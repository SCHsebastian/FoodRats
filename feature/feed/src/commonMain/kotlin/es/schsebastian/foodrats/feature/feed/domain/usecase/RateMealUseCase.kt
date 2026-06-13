package es.schsebastian.foodrats.feature.feed.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealRatingPort
import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result

/**
 * Records a rater's [Score] for a meal. The rating invariants (self-vote, already-rated,
 * rating-window) are enforced authoritatively by the Firestore transaction + rules and
 * surface here as [RateError] leaves; this use case makes the rater identity an explicit
 * input ([raterId]) and gives the VM a single delegation point rather than reaching the
 * port directly.
 */
class RateMealUseCase(private val ratings: MealRatingPort) {
    suspend operator fun invoke(
        crewId: CrewId,
        mealId: MealId,
        raterId: AccountId,
        score: Score,
    ): Result<Unit, RateError> = ratings.rate(crewId, mealId, raterId, score)
}
