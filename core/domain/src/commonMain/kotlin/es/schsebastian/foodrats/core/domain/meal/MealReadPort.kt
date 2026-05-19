package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

interface MealReadPort {
    fun observeFeed(crewId: CrewId, day: MealDay): Flow<Result<List<MealWithRatings>, MealReadError>>
    fun observeRange(crewId: CrewId, from: MealDay, to: MealDay): Flow<Result<List<MealWithRatings>, MealReadError>>
}

sealed interface MealReadError {
    data object Unauthorized : MealReadError
    data object CrewNotFound : MealReadError
    data object Unavailable  : MealReadError
}
