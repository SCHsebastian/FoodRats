package es.schsebastian.foodrats.feature.feed.domain.usecase

import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealRatingPort
import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.OutboxPort
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.first

/**
 * Records a rater's [Score] for a meal. The rating invariants (self-vote, already-rated,
 * rating-window) are enforced authoritatively by the Firestore transaction + rules and
 * surface here as [RateError] leaves; this use case makes the rater identity an explicit
 * input ([raterId]) and gives the VM a single delegation point rather than reaching the
 * port directly.
 *
 * OFFLINE-FIRST (P2 §0.5). The online success path is unchanged. When the device is offline
 * — or the direct write fails with a connectivity-class error ([RateError.Offline] /
 * [RateError.RateUnavailable]) — the command is durably parked in the [OutboxPort] and the
 * use case returns [Result.Ok], so the UI treats it as accepted; the `OutboxRunner` replays
 * it (idempotently — rate overwrites `ratings[uid]`) when connectivity returns.
 */
class RateMealUseCase(
    private val ratings: MealRatingPort,
    private val connectivity: ConnectivityPort,
    private val outbox: OutboxPort,
) {
    suspend operator fun invoke(
        crewId: CrewId,
        mealId: MealId,
        raterId: AccountId,
        score: Score,
    ): Result<Unit, RateError> {
        if (!connectivity.isOnline().first()) {
            return enqueue(crewId, mealId, raterId, score)
        }
        return when (val r = ratings.rate(crewId, mealId, raterId, score)) {
            is Result.Ok -> r
            is Result.Err -> when (r.error) {
                RateError.Offline, RateError.RateUnavailable ->
                    enqueue(crewId, mealId, raterId, score)
                else -> r
            }
        }
    }

    private suspend fun enqueue(
        crewId: CrewId,
        mealId: MealId,
        raterId: AccountId,
        score: Score,
    ): Result<Unit, RateError> {
        outbox.enqueue(PendingCommand.RateMeal(crewId, mealId, raterId, score))
        return Result.success(Unit)
    }
}
