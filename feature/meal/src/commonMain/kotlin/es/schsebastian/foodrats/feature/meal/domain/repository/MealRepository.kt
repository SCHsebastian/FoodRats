package es.schsebastian.foodrats.feature.meal.domain.repository

import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealDeletePort
import es.schsebastian.foodrats.core.domain.meal.MealDraftIngredientsPort
import es.schsebastian.foodrats.core.domain.meal.MealRatingPort
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.meal.MealSlot
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
    suspend fun hasMealForSlot(crewId: CrewId, day: MealDay, slot: MealSlot): Result<Boolean, MealError.Read>
    suspend fun takenSlotsFor(crewId: CrewId, day: MealDay): Result<Set<MealSlot>, MealError.Read>

    /**
     * For each crew in [crewIds], the slots the author has already posted on [day].
     * Lets the composer disable a slot only when it's taken in *every* selected crew
     * (the audience-aware "already posted" rule). One Firestore read per crew.
     */
    suspend fun takenSlotsPerCrew(
        crewIds: Set<CrewId>,
        day: MealDay,
    ): Result<Map<CrewId, Set<MealSlot>>, MealError.Read>
}
