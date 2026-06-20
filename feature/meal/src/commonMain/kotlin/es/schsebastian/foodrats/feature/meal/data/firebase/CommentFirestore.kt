package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.coroutines.flow.Flow

/**
 * The Firestore operations the comment repository orchestrates, expressed as a thin data-layer
 * port over [CommentFirestoreDataSource] (mirrors [ReactionFirestore]/[MealFirestore]). The concrete
 * data source is the only Firestore-touching implementation; this interface exists so the
 * repository's vendor-translation + listener-lifecycle (observe) + create/delete orchestration is
 * verifiable in `commonTest` with a behavioral fake.
 *
 * Data-layer-private: never leaves `data/firebase/`. The repository depends on this, not on the
 * concrete class, so a backend swap re-implements the interface and changes one Koin binding.
 *
 * The comment subcollection is `crews/{crewId}/meals/{mealId}/comments`, ordered by
 * `createdAtEpochMs` ascending.
 */
internal interface CommentFirestore {

    /** Live list of every comment doc on a meal, oldest first. Re-emits on any add/delete. */
    fun observe(crewId: CrewId, mealId: MealId): Flow<List<CommentDto>>

    /** Writes a new comment doc at the client-minted [CommentDto.id] (offline-replay idempotency). */
    suspend fun create(crewId: CrewId, mealId: MealId, dto: CommentDto)

    /** Deletes the comment doc with the given ID. */
    suspend fun delete(crewId: CrewId, mealId: MealId, commentId: String)
}
