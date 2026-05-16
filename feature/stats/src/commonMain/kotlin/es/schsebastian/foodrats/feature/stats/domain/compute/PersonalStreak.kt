package es.schsebastian.foodrats.feature.stats.domain.compute

import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.feature.stats.domain.model.Streak
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

fun computePersonalStreak(
    meals: List<Meal>,
    accountId: AccountId,
    today: LocalDate,
    zone: TimeZone,
): Streak {
    val myDayKeys = meals
        .filter { it.author.accountId == accountId && it.day.zone == zone }
        .map { it.day.toKey() }
        .toSet()
    var streak = 0
    var cursor = today
    while (cursor.toString() in myDayKeys) {
        streak += 1
        cursor = cursor.minus(DatePeriod(days = 1))
    }
    return Streak(streak)
}
