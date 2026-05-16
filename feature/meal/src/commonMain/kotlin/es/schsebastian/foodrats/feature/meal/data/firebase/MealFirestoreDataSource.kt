package es.schsebastian.foodrats.feature.meal.data.firebase

import dev.gitlive.firebase.firestore.FirebaseFirestore
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MealFirestoreDataSource(private val firestore: FirebaseFirestore) {

    /** Returns a new auto-generated document ID within the crew's meals collection. */
    fun newId(crewId: CrewId): String =
        firestore.collection("crews").document(crewId.value).collection("meals").document.id

    /** Writes (or overwrites) a MealDto document to Firestore. */
    suspend fun write(dto: MealDto) {
        firestore
            .collection("crews")
            .document(dto.crewId!!)
            .collection("meals")
            .document(dto.id!!)
            .set(dto)
    }

    /** Streams all meals for a crew on a given day. */
    fun observeForDay(crewId: CrewId, day: MealDay): Flow<List<MealDto>> =
        firestore.collection("crews").document(crewId.value).collection("meals")
            .where { "dayKey" equalTo day.toKey() }
            .snapshots
            .map { snap -> snap.documents.map { it.data<MealDto>() } }
}
