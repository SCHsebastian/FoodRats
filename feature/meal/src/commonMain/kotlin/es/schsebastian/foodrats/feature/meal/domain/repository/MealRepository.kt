package es.schsebastian.foodrats.feature.meal.domain.repository

import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealDeletePort
import es.schsebastian.foodrats.core.domain.meal.MealDraftIngredientsPort
import es.schsebastian.foodrats.core.domain.meal.MealRatingPort
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import kotlinx.coroutines.flow.Flow

interface MealRepository : MealReadPort, MealRatingPort, MealDeletePort, MealDraftIngredientsPort {
    suspend fun publish(draft: MealDraft): Result<Meal, MealError>
    suspend fun saveDraft(draft: MealDraft): Result<Unit, MealError>
    fun observeDraft(): Flow<MealDraft?>
    suspend fun clearDraft()

    /**
     * For each crew in [crewIds], how many meals the author has already published on [day].
     * Drives the composer's daily-cap gate ([MealPublishPolicy.MAX_MEALS_PER_CREW_PER_DAY]) and
     * the audience-aware publish check (publishing is allowed while *any* selected crew is under
     * the cap). One Firestore read per crew.
     */
    suspend fun mealCountsPerCrew(
        crewIds: Set<CrewId>,
        day: MealDay,
    ): Result<Map<CrewId, Int>, MealError.Read>
}
