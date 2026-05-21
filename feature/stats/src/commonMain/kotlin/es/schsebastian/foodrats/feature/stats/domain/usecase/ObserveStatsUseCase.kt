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
import es.schsebastian.foodrats.feature.stats.domain.compute.computeHeroStats
import es.schsebastian.foodrats.feature.stats.domain.compute.computeWindow
import es.schsebastian.foodrats.feature.stats.domain.compute.currentRangeFor
import es.schsebastian.foodrats.feature.stats.domain.compute.daysInclusive
import es.schsebastian.foodrats.feature.stats.domain.compute.startOfIsoWeek
import es.schsebastian.foodrats.feature.stats.domain.compute.startOfMonth
import es.schsebastian.foodrats.feature.stats.domain.error.StatsError
import es.schsebastian.foodrats.feature.stats.domain.model.StatsSnapshot
import es.schsebastian.foodrats.feature.stats.domain.model.StatsWindow
import es.schsebastian.foodrats.feature.stats.domain.model.Tab
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    operator fun invoke(
        historicEnabled: Flow<Boolean>,
        epoch: Flow<Int>,
    ): Flow<Result<StatsSnapshot, StatsError>> =
        combine(activeCrew.current, session.current, epoch) { c, s, _ -> c to s }
            .flatMapLatest { (crewId, sess) ->
                when {
                    sess == null   -> flowOf(Result.failure(StatsError.Session.NotSignedIn))
                    crewId == null -> flowOf(Result.failure(StatsError.Session.NoActiveCrew))
                    else -> {
                        val today: LocalDate = clock.now().toLocalDateTime(zone).date
                        val (fromCurrent, _) = currentRangeFor(today)
                        val current = mealRead.observeRange(
                            crewId,
                            MealDay(fromCurrent, zone),
                            MealDay(today, zone),
                        )
                        val historic: Flow<List<MealWithRatings>?> = historicEnabled
                            .distinctUntilChanged()
                            .flatMapLatest { enabled ->
                                if (!enabled) flowOf<List<MealWithRatings>?>(null)
                                else mealRead.observeRange(
                                    crewId,
                                    MealDay(today.minus(DatePeriod(days = 365)), zone),
                                    MealDay(today, zone),
                                ).map { r ->
                                    when (r) {
                                        is Result.Ok  -> r.value
                                        is Result.Err -> null
                                    }
                                }
                            }
                        combine(current, historic) { c, h ->
                            when (c) {
                                is Result.Err -> Result.failure(c.error.toStatsError())
                                is Result.Ok  -> Result.success(compose(c.value, h, today, sess.accountId))
                            }
                        }
                    }
                }
            }

    private fun compose(
        currentMeals: List<MealWithRatings>,
        historicMeals: List<MealWithRatings>?,
        today: LocalDate,
        accountId: AccountId,
    ): StatsSnapshot {
        val weekFrom = startOfIsoWeek(today)
        val monthFrom = startOfMonth(today)
        val weekWindow = StatsWindow(Tab.Week, weekFrom, today, daysInclusive(weekFrom, today))
        val monthWindow = StatsWindow(Tab.Month, monthFrom, today, daysInclusive(monthFrom, today))
        val historicWindow = StatsWindow(
            tab = Tab.Historic,
            from = today.minus(DatePeriod(days = 365)),
            to = today,
            days = 366,
        )
        val weekMeals = currentMeals.filter { it.meal.day.date in weekWindow.from..weekWindow.to }
        val monthMeals = currentMeals.filter { it.meal.day.date in monthWindow.from..monthWindow.to }
        return StatsSnapshot(
            hero = computeHeroStats(currentMeals, accountId, today),
            week = computeWindow(weekMeals, weekWindow),
            month = computeWindow(monthMeals, monthWindow),
            historic = historicMeals?.let { computeWindow(it, historicWindow) },
        )
    }
}

private fun MealReadError.toStatsError() = when (this) {
    MealReadError.Unauthorized -> StatsError.Read.Unauthorized
    MealReadError.CrewNotFound -> StatsError.Read.CrewNotFound
    MealReadError.Unavailable  -> StatsError.Read.Unavailable
}
