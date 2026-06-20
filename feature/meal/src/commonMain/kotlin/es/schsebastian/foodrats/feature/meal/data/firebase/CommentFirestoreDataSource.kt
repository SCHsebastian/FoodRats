package es.schsebastian.foodrats.feature.meal.data.firebase

import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.FirebaseFirestore
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The only Firestore-touching implementation of [CommentFirestore]. Targets the
 * `crews/{crewId}/meals/{mealId}/comments` subcollection, ordered by `createdAtEpochMs` ascending.
 */
internal class CommentFirestoreDataSource(
    private val firestore: FirebaseFirestore,
) : CommentFirestore {

    override fun observe(crewId: CrewId, mealId: MealId): Flow<List<CommentDto>> =
        firestore.collection("crews").document(crewId.value)
            .collection("meals").document(mealId.value)
            .collection("comments")
            .orderBy("createdAtEpochMs", Direction.ASCENDING)
            .snapshots
            .map { snap -> snap.documents.map { d -> d.data<CommentDto>().copy(id = d.id) } }

    override suspend fun create(crewId: CrewId, mealId: MealId, dto: CommentDto) {
        firestore.collection("crews").document(crewId.value)
            .collection("meals").document(mealId.value)
            .collection("comments").document(dto.id!!)
            .set(dto)
    }

    override suspend fun delete(crewId: CrewId, mealId: MealId, commentId: String) {
        firestore.collection("crews").document(crewId.value)
            .collection("meals").document(mealId.value)
            .collection("comments").document(commentId)
            .delete()
    }
}
