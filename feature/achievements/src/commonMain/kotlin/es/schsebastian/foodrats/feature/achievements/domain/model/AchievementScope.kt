package es.schsebastian.foodrats.feature.achievements.domain.model

/**
 * Whether a criterion is evaluated over the current member's own meals ([Personal]) or over the
 * whole crew ([Crew]).
 *
 * Modeled as an enum (not a sealed-interface error) because it is a closed presentation dimension
 * with no payload — matching `MealSlot`. The criterion declares its scope so the evaluator never
 * needs scope branching outside the taxonomy.
 */
enum class AchievementScope { Personal, Crew }
