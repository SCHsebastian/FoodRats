package es.schsebastian.foodrats.feature.stats.domain.model

import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import kotlinx.datetime.LocalDate

/**
 * One month of the signed-in member's own meals in the active crew, keyed by calendar day —
 * the read model behind the "My plates" monthly calendar.
 *
 * [month] is the first day of the month; [mealsByDay] holds only days with at least one meal
 * (a day absent from the map has no plates), each day's list ordered by `publishedAt`.
 */
data class MealCalendarMonth(
    val month: LocalDate,
    val mealsByDay: Map<LocalDate, List<MealWithRatings>>,
)
