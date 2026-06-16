package es.schsebastian.foodrats.feature.achievements.domain.model

import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementScope.Crew
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementScope.Personal

/**
 * The sealed taxonomy of badge criteria — the heart of the engine.
 *
 * Each leaf carries its threshold as a typed field and declares its [scope]. Adding cuisine-passport
 * / ingredient-bingo / new streak milestones means adding leaves here (and a `when` arm in
 * `AchievementEvaluator`, whose exhaustiveness forces the compile-time guard). **Never an enum** —
 * leaves carry payloads. (spec §5.2)
 */
sealed interface AchievementCriterion {
    val scope: AchievementScope

    /** First published plate. */
    data object FirstPlate : AchievementCriterion {
        override val scope = Personal
    }

    /** Personal lifetime plate count ≥ [target] (10 / 50 / 100). */
    data class MealCount(val target: Int) : AchievementCriterion {
        override val scope = Personal
    }

    /** ≥ [target] DISTINCT user-confirmed ingredient slugs across the member's plates. */
    data class IngredientVariety(val target: Int) : AchievementCriterion {
        override val scope = Personal
    }

    /** Member's current personal streak ≥ [days] (7 / 30 / 100). */
    data class PersonalStreak(val days: Int) : AchievementCriterion {
        override val scope = Personal
    }

    /** Crew's current shared streak ≥ [days] (7 / 30). */
    data class CrewStreak(val days: Int) : AchievementCriterion {
        override val scope = Crew
    }

    /** ≥ [target] plates whose slot is Breakfast. */
    data class EarlyBird(val target: Int) : AchievementCriterion {
        override val scope = Personal
    }

    /** ≥ [target] plates whose slot is Dinner. */
    data class NightOwl(val target: Int) : AchievementCriterion {
        override val scope = Personal
    }

    /** Highest average score over a window (min 3 plates), i.e. "best cook". */
    data object BestCook : AchievementCriterion {
        override val scope = Crew
    }

    /**
     * Forward-hook (spec §15): ≥ [target] distinct cuisines. Always LOCKED until the
     * cuisine-passport spec lands and supplies the cuisine signal.
     */
    data class CuisineVariety(val target: Int) : AchievementCriterion {
        override val scope = Personal
    }
}
