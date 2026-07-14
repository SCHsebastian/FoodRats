package es.schsebastian.foodrats.feature.achievements.domain.model

/**
 * The sealed taxonomy of badge criteria — the heart of the engine.
 *
 * Each leaf carries its threshold as a typed field. Adding cuisine-passport / ingredient-bingo /
 * new streak milestones means adding leaves here (and a `when` arm in `AchievementEvaluator`,
 * whose exhaustiveness forces the compile-time guard). **Never an enum** — leaves carry payloads.
 * (spec §5.2)
 */
sealed interface AchievementCriterion {

    /** First published plate. */
    data object FirstPlate : AchievementCriterion

    /** Personal lifetime plate count ≥ [target] (10 / 50 / 100). */
    data class MealCount(val target: Int) : AchievementCriterion

    /** ≥ [target] DISTINCT user-confirmed ingredient slugs across the member's plates. */
    data class IngredientVariety(val target: Int) : AchievementCriterion

    /** Member's current personal streak ≥ [days] (7 / 30 / 100). */
    data class PersonalStreak(val days: Int) : AchievementCriterion

    /** Crew's current shared streak ≥ [days] (7 / 30). */
    data class CrewStreak(val days: Int) : AchievementCriterion

    /** ≥ [target] plates whose slot is Breakfast. */
    data class EarlyBird(val target: Int) : AchievementCriterion

    /** ≥ [target] plates whose slot is Dinner. */
    data class NightOwl(val target: Int) : AchievementCriterion

    /** Highest average score over a window (min 3 plates), i.e. "best cook". */
    data object BestCook : AchievementCriterion

    /**
     * Forward-hook (spec §15): ≥ [target] distinct cuisines. Always LOCKED until the
     * cuisine-passport spec lands and supplies the cuisine signal.
     */
    data class CuisineVariety(val target: Int) : AchievementCriterion
}
