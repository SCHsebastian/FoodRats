package es.schsebastian.foodrats.feature.feed.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealDeleteError
import es.schsebastian.foodrats.core.domain.meal.MealDeletePort
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result

class FakeMealDeletePort : MealDeletePort {
    data class Call(val crewId: String, val mealId: String)
    val calls = mutableListOf<Call>()
    var nextResult: Result<Unit, MealDeleteError> = Result.success(Unit)

    override suspend fun delete(crewId: CrewId, mealId: MealId): Result<Unit, MealDeleteError> {
        calls += Call(crewId.value, mealId.value)
        return nextResult
    }

    override suspend fun deleteFromAllCrews(
        crewIds: Set<CrewId>,
        authorId: AccountId,
        day: MealDay,
        token: String,
    ): Result<Unit, MealDeleteError> = nextResult
}
