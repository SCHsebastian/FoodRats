package es.schsebastian.foodrats.app.recap

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.achievements.domain.usecase.AchievementsSnapshot
import es.schsebastian.foodrats.feature.achievements.domain.usecase.ObserveAchievementsUseCase
import es.schsebastian.foodrats.feature.stats.domain.model.StatsSnapshot
import es.schsebastian.foodrats.feature.stats.domain.usecase.ObserveStatsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * The recap's read seam. The ViewModel observes THIS, not the two concrete feature use cases — so it
 * stays unit-testable behind a trivial fake (the use cases pull a full Firebase graph). The default
 * implementation [statsAndAchievementsRecapStream] adapts `ObserveStatsUseCase` +
 * `ObserveAchievementsUseCase` through the pure [assembleWeeklyRecap], computing the ISO-week window
 * once from the [Clock]. Nothing is recomputed; this is purely a different rendering of existing data.
 */
fun interface WeeklyRecapStream {
    operator fun invoke(): Flow<WeeklyRecapResult>
}

/** What the recap read resolved to. [Failed] = the stats read errored (e.g. no active crew). */
sealed interface WeeklyRecapResult {
    data class Ready(val recap: WeeklyRecap) : WeeklyRecapResult
    data object Failed : WeeklyRecapResult
}

/**
 * The production [WeeklyRecapStream]: combines the stats + achievements observers and folds them into
 * a [WeeklyRecap] via the pure assembler. The week label is the ISO-week-start date string (a richer
 * locale-formatted label is a later polish; the date is unambiguous and PII-free meanwhile).
 */
fun statsAndAchievementsRecapStream(
    observeStats: ObserveStatsUseCase,
    observeAchievements: ObserveAchievementsUseCase,
    clock: Clock,
    zone: TimeZone,
): WeeklyRecapStream = WeeklyRecapStream {
    val today: LocalDate = clock.now().toLocalDateTime(zone).date
    val weekStart = startOfIsoWeek(today)
    val weekStartEpochMs = weekStart.atStartOfDayEpochMs(zone)
    val weekEndEpochMs = weekStart.plus(DatePeriod(days = 7)).atStartOfDayEpochMs(zone)
    val weekLabel = weekStart.toString()

    combine(
        observeStats(historicEnabled = flowOf(false), epoch = flowOf(0)),
        observeAchievements(),
    ) { statsResult, achievementsResult ->
        when (statsResult) {
            is Result.Err -> WeeklyRecapResult.Failed
            is Result.Ok -> {
                val stats: StatsSnapshot = statsResult.value
                val achievements: AchievementsSnapshot? = (achievementsResult as? Result.Ok)?.value
                WeeklyRecapResult.Ready(
                    assembleWeeklyRecap(
                        stats = stats,
                        achievements = achievements,
                        weekLabel = weekLabel,
                        weekWindowStartEpochMs = weekStartEpochMs,
                        weekWindowEndEpochMs = weekEndEpochMs,
                    ),
                )
            }
        }
    }
}

private fun LocalDate.atStartOfDayEpochMs(zone: TimeZone): Long =
    LocalDateTime(this, LocalTime(0, 0)).toInstant(zone).toEpochMilliseconds()

/** Monday-anchored start of the ISO week containing [date]. */
internal fun startOfIsoWeek(date: LocalDate): LocalDate {
    val offset = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> 0
        DayOfWeek.TUESDAY -> 1
        DayOfWeek.WEDNESDAY -> 2
        DayOfWeek.THURSDAY -> 3
        DayOfWeek.FRIDAY -> 4
        DayOfWeek.SATURDAY -> 5
        DayOfWeek.SUNDAY -> 6
    }
    return date.minus(DatePeriod(days = offset))
}
