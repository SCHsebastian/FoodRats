package es.schsebastian.foodrats.feature.stats.domain.usecase

import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.stats.domain.compute.computeCrewStreak
import es.schsebastian.foodrats.feature.stats.domain.compute.computeLeaderboard
import es.schsebastian.foodrats.feature.stats.domain.compute.computePersonalStreak
import es.schsebastian.foodrats.feature.stats.domain.compute.computeTagVariety
import es.schsebastian.foodrats.feature.stats.domain.compute.computeTopDishes
import es.schsebastian.foodrats.feature.stats.domain.error.StatsError
import es.schsebastian.foodrats.feature.stats.domain.model.StatsSnapshot
import es.schsebastian.foodrats.feature.stats.domain.model.StatsWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

class ObserveStatsUseCase(
    private val activeCrew: ActiveCrewProvider,
    private val session: SessionProvider,
    private val mealRead: MealReadPort,
    private val clock: Clock,
    private val zone: TimeZone,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<Result<StatsSnapshot, StatsError>> {
        return combine(activeCrew.current, session.current) { crewId, sess -> crewId to sess }
            .flatMapLatest { (crewId, sess) ->
                when {
                    sess == null -> flowOf(Result.failure(StatsError.Session.NotSignedIn))
                    crewId == null -> flowOf(Result.failure(StatsError.Session.NoActiveCrew))
                    else -> {
                        val today: LocalDate = clock.now().toLocalDateTime(zone).date
                        val from = today.minus(DatePeriod(days = StatsWindow.DAYS - 1))
                        mealRead.observeRange(crewId, MealDay(from, zone), MealDay(today, zone))
                            .map { r ->
                                when (r) {
                                    is Result.Ok  -> Result.success(snapshot(r.value, sess.accountId, today))
                                    is Result.Err -> Result.failure(r.error.toStatsError())
                                }
                            }
                    }
                }
            }
    }

    private fun snapshot(
        aggregates: List<MealWithRatings>,
        accountId: AccountId,
        today: LocalDate,
    ): StatsSnapshot {
        val meals = aggregates.map { it.meal }
        val memberIds = meals.map { it.author.accountId }.distinct()
        return StatsSnapshot(
            crewStreak = computeCrewStreak(meals, memberIds, today, zone),
            personalStreak = computePersonalStreak(meals, accountId, today, zone),
            topDishes = computeTopDishes(meals, limit = 5),
            leaderboard = computeLeaderboard(aggregates),
            tagVarietyCount = computeTagVariety(meals),
            mealsConsidered = meals.size,
        )
    }
}

private fun MealReadError.toStatsError() = when (this) {
    MealReadError.Unauthorized -> StatsError.Read.Unauthorized
    MealReadError.CrewNotFound -> StatsError.Read.CrewNotFound
    MealReadError.Unavailable  -> StatsError.Read.Unavailable
}
