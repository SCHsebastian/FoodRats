package es.schsebastian.foodrats.feature.feed.domain.usecase

import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealRatingPort
import es.schsebastian.foodrats.core.domain.meal.OptimisticMealWritePort
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
 *
 * OPTIMISTIC RENDER (P3b §P3b-T5). On that offline-fallback path the rate is also written into the
 * meal feed's local read source-of-truth via [OptimisticMealWritePort] BEFORE the command is
 * enqueued, so the star appears in the feed instantly (the feed renders from that local store). The
 * pending row carries the command's [PendingCommand.idempotencyKey]; when the rated meal next syncs
 * from the server the optimistic row is overwritten with server truth (pending auto-clears). The
 * online success path does NOT write optimistically — the server snapshot is the source of truth
 * and arrives via sync.
 */
class RateMealUseCase(
    private val ratings: MealRatingPort,
    private val connectivity: ConnectivityPort,
    private val outbox: OutboxPort,
    private val optimistic: OptimisticMealWritePort,
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
        val cmd = PendingCommand.RateMeal(crewId, mealId, raterId, score)
        // Render the star immediately (the feed reads the local store); the pending row carries the
        // command's idempotency key so the next server snapshot of this meal overwrites it. Applied
        // BEFORE enqueue so the optimistic write is visible the moment the command is durably parked.
        optimistic.applyRate(crewId, mealId, raterId, score, cmd.idempotencyKey)
        return when (outbox.enqueue(cmd)) {
            is Result.Ok -> Result.success(Unit)
            is Result.Err -> {
                // The command could not be durably persisted. Roll back the phantom star so
                // the UI does not show an accepted vote that will never reach the server.
                optimistic.clearPending(cmd.idempotencyKey)
                Result.failure(RateError.RateUnavailable)
            }
        }
    }
}
