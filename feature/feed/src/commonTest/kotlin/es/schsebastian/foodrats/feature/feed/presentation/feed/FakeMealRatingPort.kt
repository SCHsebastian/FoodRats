package es.schsebastian.foodrats.feature.feed.presentation.feed

import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealRatingPort
import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result

class FakeMealRatingPort : MealRatingPort {
    data class Call(val crewId: String, val mealId: String, val raterId: String, val score: Int)
    val calls = mutableListOf<Call>()
    var nextResult: Result<Unit, RateError> = Result.success(Unit)
    override suspend fun rate(
        crewId: CrewId,
        mealId: MealId,
        raterId: AccountId,
        score: Score,
    ): Result<Unit, RateError> {
        calls += Call(crewId.value, mealId.value, raterId.value, score.value)
        return nextResult
    }
}
