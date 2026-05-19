package es.schsebastian.foodrats.feature.meal.data.firebase

import dev.gitlive.firebase.firestore.FirebaseFirestore
import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MealRatingsFirestoreDataSource(private val firestore: FirebaseFirestore) {

    /** Streams the ratings subcollection of a single meal. Pairs each DTO with its rater UID (doc ID). */
    fun observe(crewId: CrewId, mealId: String): Flow<List<Pair<String, MealRatingDto>>> =
        firestore.collection("crews").document(crewId.value)
            .collection("meals").document(mealId)
            .collection("ratings")
            .snapshots
            .map { snap -> snap.documents.map { it.id to it.data<MealRatingDto>() } }

    /** Writes one rating doc keyed by raterUid. Throws on permission-denied / already-exists. */
    suspend fun write(crewId: CrewId, mealId: String, raterUid: String, dto: MealRatingDto) {
        firestore.collection("crews").document(crewId.value)
            .collection("meals").document(mealId)
            .collection("ratings").document(raterUid)
            .set(dto)
    }
}
