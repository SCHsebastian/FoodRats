package es.schsebastian.foodrats.feature.meal.data.firebase

import dev.gitlive.firebase.firestore.FirebaseFirestore
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The only Firestore-touching implementation of [ReactionFirestore]. Targets the
 * `crews/{crewId}/meals/{mealId}/reactions/{uid}` subcollection; the doc ID is the reactor uid.
 */
internal class ReactionFirestoreDataSource(
    private val firestore: FirebaseFirestore,
) : ReactionFirestore {

    private fun reactions(crewId: CrewId, mealId: MealId) =
        firestore.collection("crews").document(crewId.value)
            .collection("meals").document(mealId.value)
            .collection("reactions")

    override fun observe(crewId: CrewId, mealId: MealId): Flow<List<ReactionDto>> =
        reactions(crewId, mealId).snapshots
            .map { snap -> snap.documents.map { d -> d.data<ReactionDto>().copy(reactorId = d.id) } }

    override suspend fun reactionOf(crewId: CrewId, mealId: MealId, reactorUid: String): ReactionDto? {
        val snap = reactions(crewId, mealId).document(reactorUid).get()
        return if (snap.exists) snap.data<ReactionDto>().copy(reactorId = reactorUid) else null
    }

    override suspend fun mealExists(crewId: CrewId, mealId: MealId): Boolean =
        firestore.collection("crews").document(crewId.value)
            .collection("meals").document(mealId.value)
            .get()
            .exists

    override suspend fun put(crewId: CrewId, mealId: MealId, dto: ReactionDto) {
        val reactorId = requireNotNull(dto.reactorId) { "ReactionDto.reactorId must not be null when persisting a reaction" }
        reactions(crewId, mealId).document(reactorId).set(dto)
    }

    override suspend fun remove(crewId: CrewId, mealId: MealId, reactorUid: String) {
        reactions(crewId, mealId).document(reactorUid).delete()
    }
}
