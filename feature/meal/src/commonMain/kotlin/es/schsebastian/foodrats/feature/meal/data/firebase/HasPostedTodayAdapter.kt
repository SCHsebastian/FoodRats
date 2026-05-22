package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.HasPostedTodayPort
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.withContext

internal class HasPostedTodayAdapter(
    private val firestore: MealFirestoreDataSource,
    private val dispatchers: DispatcherProvider,
) : HasPostedTodayPort {

    override suspend fun hasPosted(
        accountId: AccountId,
        crewId: CrewId,
        day: MealDay,
    ): Result<Boolean, MealReadError> = withContext(dispatchers.io) {
        runCatching<Result<Boolean, MealReadError>> {
            val taken = firestore.takenSlots(crewId, accountId, day.toKey())
            Result.success(taken.isNotEmpty())
        }.fold(
            onSuccess = { it },
            onFailure = { Result.failure(MealReadError.Unavailable) },
        )
    }
}
