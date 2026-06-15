package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.coroutines.flow.Flow

/**
 * The Firestore operations the reaction repository orchestrates, expressed as a thin data-layer
 * port over [ReactionFirestoreDataSource] (mirrors [MealFirestore]). The concrete data source is
 * the only Firestore-touching implementation; this interface exists so the repository's
 * vendor-translation + toggle orchestration is verifiable in `commonTest` with a behavioral fake.
 *
 * Data-layer-private: never leaves `data/firebase/`. The repository depends on this, not on the
 * concrete class, so a backend swap re-implements the interface and changes one Koin binding.
 *
 * The reaction subcollection is `crews/{crewId}/meals/{mealId}/reactions/{uid}`, the doc ID being
 * the reactor uid — which authoritatively enforces one reaction per member.
 */
internal interface ReactionFirestore {

    /** Live list of every reaction doc on a meal. Re-emits on any add/remove. */
    fun observe(crewId: CrewId, mealId: MealId): Flow<List<ReactionDto>>

    /** The reactor's reaction doc, or `null` if they haven't reacted. */
    suspend fun reactionOf(crewId: CrewId, mealId: MealId, reactorUid: String): ReactionDto?

    /** Whether the meal document exists (a delete may have raced the toggle). */
    suspend fun mealExists(crewId: CrewId, mealId: MealId): Boolean

    /** Creates/overwrites the reactor's reaction doc (doc ID == [ReactionDto.reactorId]). */
    suspend fun put(crewId: CrewId, mealId: MealId, dto: ReactionDto)

    /** Deletes the reactor's reaction doc. */
    suspend fun remove(crewId: CrewId, mealId: MealId, reactorUid: String)
}
