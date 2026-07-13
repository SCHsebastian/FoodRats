package es.schsebastian.foodrats.core.domain.meal

/**
 * Publishing limits shared by the composer gate, the publish use case, and the repository.
 *
 * The cap is per crew per day: each crew a plate fans out to counts its own meals independently
 * (a meal shared to 3 crews uses one "slot" in each). Slot is irrelevant to the count — several
 * meals may share a slot or carry none. Enforced client-side (Firestore rules cannot count
 * documents); the rules still pin ownership, crew membership, slot vocabulary, and the meal-id
 * shape as defence in depth.
 */
object MealPublishPolicy {
    const val MAX_MEALS_PER_CREW_PER_DAY = 10

    /** Absolute cap on [Meal.plates] — the most photos a single meal may carry. */
    const val MAX_PHOTOS_PER_MEAL = 10
}
