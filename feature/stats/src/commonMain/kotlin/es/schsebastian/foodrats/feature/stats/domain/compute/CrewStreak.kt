package es.schsebastian.foodrats.feature.stats.domain.compute

import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.feature.stats.domain.model.Streak
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

fun computeCrewStreak(
    meals: List<Meal>,
    memberIds: List<AccountId>,
    today: LocalDate,
    zone: TimeZone,
): Streak {
    if (memberIds.isEmpty()) return Streak(0)
    // Set of (memberAccountId, dayKey).
    val postedKeys: Set<Pair<AccountId, String>> = meals
        .filter { it.day.zone == zone }
        .map { it.author.accountId to it.day.toKey() }
        .toSet()
    val memberSet = memberIds.toSet()
    var streak = 0
    var cursor = today
    while (true) {
        val dayKey = cursor.toString()
        val everyone = memberSet.all { it to dayKey in postedKeys }
        if (!everyone) break
        streak += 1
        cursor = cursor.minus(DatePeriod(days = 1))
    }
    return Streak(streak)
}
