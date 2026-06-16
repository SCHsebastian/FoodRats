package es.schsebastian.foodrats.feature.achievements.domain

import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementSignals
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * Resolves the raw crew meal window into the pure [AchievementSignals] the evaluator consumes — the
 * derived streak / best-cook signals that aren't directly on a meal (spec §7).
 *
 * The streak + best-cook algorithms are **re-implemented here** (not imported from `:feature:stats`)
 * because features may not depend on other features. They mirror the stats `PersonalStreak` /
 * `CrewStreak` / `ComputeWindow` shapes (`COOK_AWARD_MIN_PLATES = 3`); kept pure (no Clock/Flow) so
 * it is unit-testable against a hand-built meal list.
 */
class AchievementSignalsBuilder {

    fun build(
        accountId: AccountId,
        crewMeals: List<MealWithRatings>,
        today: LocalDate,
    ): AchievementSignals {
        val justMeals = crewMeals.map { it.meal }
        val memberIds = justMeals.map { it.author.accountId }.distinct()
        return AchievementSignals(
            accountId = accountId,
            crewMeals = crewMeals,
            personalStreakDays = personalStreak(crewMeals, accountId, today),
            crewStreakDays = crewStreak(crewMeals, memberIds, today),
            bestCookAccountId = bestCook(crewMeals),
        )
    }

    /** Consecutive days up to [today] on which [accountId] posted at least one plate. */
    private fun personalStreak(
        meals: List<MealWithRatings>,
        accountId: AccountId,
        today: LocalDate,
    ): Int {
        val myDayKeys = meals
            .filter { it.meal.author.accountId == accountId }
            .map { it.meal.day.toKey() }
            .toSet()
        var streak = 0
        var cursor = today
        while (cursor.toString() in myDayKeys) {
            streak += 1
            cursor = cursor.minus(DatePeriod(days = 1))
        }
        return streak
    }

    /** Consecutive days up to [today] on which EVERY crew member posted. */
    private fun crewStreak(
        meals: List<MealWithRatings>,
        memberIds: List<AccountId>,
        today: LocalDate,
    ): Int {
        if (memberIds.isEmpty()) return 0
        val postedKeys: Set<Pair<AccountId, String>> = meals
            .map { it.meal.author.accountId to it.meal.day.toKey() }
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
        return streak
    }

    /**
     * Highest average score over the window (min [COOK_AWARD_MIN_PLATES] rated plates), or null when
     * no member qualifies. Tie-break mirrors stats: higher average, then more plates.
     */
    private fun bestCook(meals: List<MealWithRatings>): AccountId? {
        val byAuthor = meals.groupBy { it.meal.author.accountId }
        return byAuthor.entries
            .mapNotNull { (id, list) ->
                if (list.size < COOK_AWARD_MIN_PLATES) return@mapNotNull null
                val averages = list.mapNotNull { it.averageScore }
                if (averages.isEmpty()) null else Triple(id, averages.average(), list.size)
            }
            .maxWithOrNull(
                compareBy<Triple<AccountId, Double, Int>> { it.second }.thenBy { it.third },
            )
            ?.first
    }

    private companion object {
        const val COOK_AWARD_MIN_PLATES = 3
    }
}
