package es.schsebastian.foodrats.feature.achievements.domain

import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.feature.achievements.domain.model.Achievement
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementCriterion
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementProgress
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementSignals
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementStatus

/**
 * The pure achievements engine: same inputs → same statuses. No I/O, no Clock, no Flow. The caller
 * resolves the [AchievementSignals] from ports and overlays persisted unlock timestamps afterward
 * (spec §5.5, §6.3).
 */
class AchievementEvaluator {

    /**
     * Evaluates [catalog] against [signals]. Returns one [AchievementStatus] per row with
     * `unlockedAtEpochMs = null` — the caller overlays persisted unlock dates (spec §6.3).
     */
    fun evaluate(
        catalog: List<Achievement>,
        signals: AchievementSignals,
    ): List<AchievementStatus> {
        // Personal criteria score against the member's OWN plates only; a crew-mate's plates never
        // advance a Personal criterion.
        val mine = signals.crewMeals.filter { it.meal.author.accountId == signals.accountId }
        return catalog.map { achievement ->
            AchievementStatus(
                achievement = achievement,
                progress = progressFor(achievement.criterion, signals, mine),
                unlockedAtEpochMs = null,
            )
        }
    }

    private fun progressFor(
        criterion: AchievementCriterion,
        s: AchievementSignals,
        mine: List<MealWithRatings>,
    ): AchievementProgress {
        return when (criterion) {
            AchievementCriterion.FirstPlate ->
                AchievementProgress(if (mine.isNotEmpty()) 1 else 0, 1)

            is AchievementCriterion.MealCount ->
                AchievementProgress(mine.size, criterion.target)

            is AchievementCriterion.IngredientVariety ->
                // user-CONFIRMED only — detectedIngredients (AI, advisory) are excluded.
                AchievementProgress(
                    mine.flatMap { it.meal.ingredients }.distinct().size,
                    criterion.target,
                )

            is AchievementCriterion.PersonalStreak ->
                AchievementProgress(s.personalStreakDays, criterion.days)

            is AchievementCriterion.CrewStreak ->
                AchievementProgress(s.crewStreakDays, criterion.days)

            is AchievementCriterion.EarlyBird ->
                AchievementProgress(mine.count { it.meal.slot == MealSlot.Breakfast }, criterion.target)

            is AchievementCriterion.NightOwl ->
                AchievementProgress(mine.count { it.meal.slot == MealSlot.Dinner }, criterion.target)

            AchievementCriterion.BestCook ->
                AchievementProgress(if (s.bestCookAccountId == s.accountId) 1 else 0, 1)

            is AchievementCriterion.CuisineVariety ->
                // Distinct cuisines stamped on the member's OWN plates at publish (roadmap §2.2).
                // Counts only confirmed `Meal.cuisine` — never re-derived from ingredients/AI. Reads
                // 0 for meals published before stamping shipped (cuisine == null). The catalog ships
                // no CuisineVariety row yet (spec §9/§15) — this arm is live for any future row.
                AchievementProgress(
                    mine.mapNotNull { it.meal.cuisine }.distinct().size,
                    criterion.target,
                )
        }
    }
}
