package es.schsebastian.foodrats.feature.feed.domain.usecase

import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.feed.domain.error.FeedError
import es.schsebastian.foodrats.feature.feed.domain.model.FeedDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ObserveFeedUseCase(
    private val activeCrew: ActiveCrewProvider,
    private val mealRead: MealReadPort,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(day: Flow<FeedDay>): Flow<Result<List<Meal>, FeedError>> =
        combine(activeCrew.current, day) { crewId, d -> crewId to d }
            .flatMapLatest { (crewId, d) ->
                if (crewId == null) {
                    flowOf(Result.failure(FeedError.Session.NoActiveCrew))
                } else {
                    mealRead.observeFeed(crewId, d.day).map { r ->
                        when (r) {
                            is Result.Ok  -> Result.success(r.value)
                            is Result.Err -> Result.failure(r.error.toFeedError())
                        }
                    }
                }
            }
}

private fun MealReadError.toFeedError(): FeedError.Read = when (this) {
    MealReadError.Unauthorized  -> FeedError.Read.Unauthorized
    MealReadError.CrewNotFound  -> FeedError.Read.CrewNotFound
    MealReadError.Unavailable   -> FeedError.Read.Unavailable
}
