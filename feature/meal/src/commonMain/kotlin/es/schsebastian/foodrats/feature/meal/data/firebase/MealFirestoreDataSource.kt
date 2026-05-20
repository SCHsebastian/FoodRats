package es.schsebastian.foodrats.feature.meal.data.firebase

import dev.gitlive.firebase.firestore.FirebaseFirestore
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MealFirestoreDataSource(private val firestore: FirebaseFirestore) {

    /** Returns a new auto-generated document ID within the crew's meals collection. */
    fun newId(crewId: CrewId): String =
        firestore.collection("crews").document(crewId.value).collection("meals").document.id

    /** Writes (or overwrites) a MealDto document to Firestore using the given document ID. */
    suspend fun write(dto: MealDto, docId: String = dto.id!!) {
        firestore
            .collection("crews")
            .document(dto.crewId!!)
            .collection("meals")
            .document(docId)
            .set(dto)
    }

    /** Streams all meals for a crew on a given day. */
    fun observeForDay(crewId: CrewId, day: MealDay): Flow<List<MealDto>> =
        firestore.collection("crews").document(crewId.value).collection("meals")
            .where { "dayKey" equalTo day.toKey() }
            .snapshots
            .map { snap -> snap.documents.map { it.data<MealDto>() } }

    /**
     * Streams all meals for a crew with `dayKey` in [from..to] (inclusive both ends).
     * `dayKey` is `YYYY-MM-DD`, so lexicographic compare matches chronological order;
     * a single-field range query needs no composite index.
     */
    fun observeForRange(crewId: CrewId, from: MealDay, to: MealDay): Flow<List<MealDto>> =
        firestore.collection("crews").document(crewId.value).collection("meals")
            .where {
                ("dayKey" greaterThanOrEqualTo from.toKey()) and
                    ("dayKey" lessThanOrEqualTo to.toKey())
            }
            .snapshots
            .map { snap -> snap.documents.map { it.data<MealDto>() } }

    /** Returns true if a meal document exists for the given crew/author/day/slot combination. */
    suspend fun mealExists(
        crewId: CrewId,
        authorId: AccountId,
        dayKey: String,
        slot: MealSlot,
    ): Boolean {
        val docId = "${crewId.value}_${authorId.value}_${dayKey}_${slot.key()}"
        return firestore
            .collection("crews")
            .document(crewId.value)
            .collection("meals")
            .document(docId)
            .get()
            .exists
    }

    /** Returns the set of slots already taken for the given crew/author/day. */
    suspend fun takenSlots(
        crewId: CrewId,
        authorId: AccountId,
        dayKey: String,
    ): Set<MealSlot> = MealSlot.entries
        .filter { mealExists(crewId, authorId, dayKey, it) }
        .toSet()

    /** Returns the MealDto for the given mealId, or null if not found. */
    suspend fun readById(crewId: CrewId, mealId: String): MealDto? {
        val snap = firestore.collection("crews").document(crewId.value)
            .collection("meals").document(mealId).get()
        return if (snap.exists) snap.data<MealDto>() else null
    }
}
