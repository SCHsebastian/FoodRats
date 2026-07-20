package es.schsebastian.foodrats.feature.stats.domain.usecase

import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.stats.domain.error.StatsError
import es.schsebastian.foodrats.feature.stats.domain.model.MealCalendarMonth
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
import kotlinx.datetime.plus

/**
 * Streams one calendar month of the signed-in member's OWN meals in the active crew, grouped by
 * day — the "My plates" monthly calendar read model. [month] is a flow of first-of-month cursors;
 * each new cursor re-subscribes the inclusive month range on [MealReadPort.observeRange].
 *
 * Known limitation (accepted): `observeRange` is served from the local SQLDelight mirror with a
 * rolling ~30-day sync window, so months older than that only show meals the device already has
 * cached locally.
 */
class ObserveMyMealCalendarUseCase(
    private val activeCrew: ActiveCrewProvider,
    private val session: SessionProvider,
    private val mealRead: MealReadPort,
    private val zone: TimeZone,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(month: Flow<LocalDate>): Flow<Result<MealCalendarMonth, StatsError>> =
        combine(activeCrew.current, session.current, month) { c, s, m -> Triple(c, s, m) }
            .flatMapLatest { (crewId, sess, first) ->
                when {
                    sess == null   -> flowOf(Result.failure(StatsError.Session.NotSignedIn))
                    crewId == null -> flowOf(Result.failure(StatsError.Session.NoActiveCrew))
                    else -> {
                        val last = first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
                        mealRead.observeRange(crewId, MealDay(first, zone), MealDay(last, zone))
                            .map { r ->
                                when (r) {
                                    is Result.Ok -> Result.success(
                                        MealCalendarMonth(
                                            month = first,
                                            mealsByDay = r.value
                                                .filter { it.meal.author.accountId == sess.accountId }
                                                .sortedBy { it.meal.publishedAt }
                                                .groupBy { it.meal.day.date },
                                        ),
                                    )
                                    is Result.Err -> Result.failure(r.error.toStatsError())
                                }
                            }
                    }
                }
            }
}
