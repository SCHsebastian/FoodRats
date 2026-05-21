package es.schsebastian.foodrats.feature.stats.domain.compute

import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.feature.stats.domain.model.HeroStats
import kotlinx.datetime.LocalDate

fun computeHeroStats(
    meals: List<MealWithRatings>,
    accountId: AccountId,
    today: LocalDate,
): HeroStats {
    val justMeals = meals.map { it.meal }
    val todays = justMeals.filter { it.day.date == today }
    val memberIds = justMeals.map { it.author.accountId }.distinct()
    return HeroStats(
        personalStreak = computePersonalStreak(justMeals, accountId, today),
        crewStreak = computeCrewStreak(justMeals, memberIds, today),
        platesToday = todays.size,
        iPostedToday = todays.any { it.author.accountId == accountId },
    )
}
