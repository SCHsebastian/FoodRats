package es.schsebastian.foodrats.feature.meal.data.firebase

import dev.gitlive.firebase.firestore.FirebaseFirestore
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class MealFirestoreDataSource(private val firestore: FirebaseFirestore) : MealFirestore {

    /** Returns a new auto-generated document ID within the crew's meals collection. */
    fun newId(crewId: CrewId): String =
        firestore.collection("crews").document(crewId.value).collection("meals").document.id

    /** Writes (or overwrites) a MealDto document to Firestore using the given document ID. */
    override suspend fun write(dto: MealDto, docId: String) {
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
    override fun observeForRange(crewId: CrewId, from: MealDay, to: MealDay): Flow<List<MealDto>> =
        firestore.collection("crews").document(crewId.value).collection("meals")
            .where {
                ("dayKey" greaterThanOrEqualTo from.toKey()) and
                    ("dayKey" lessThanOrEqualTo to.toKey())
            }
            .snapshots
            .map { snap -> snap.documents.map { it.data<MealDto>() } }

    /** The ids of every meal this author published in the crew on [dayKey]. One Firestore read. */
    override suspend fun existingMealIds(
        crewId: CrewId,
        authorId: AccountId,
        dayKey: String,
    ): Set<String> {
        val snaps = firestore.collection("crews").document(crewId.value).collection("meals")
            .where {
                ("authorId" equalTo authorId.value) and
                    ("dayKey" equalTo dayKey)
            }
            .get()
            .documents
        return snaps.map { it.id }.toSet()
    }

    /** Deletes a meal document. Subcollections (comments, ratings) are swept by the
     *  onMealDeleted Cloud Function, since Firestore deletes do not cascade. */
    override suspend fun deleteMeal(crewId: CrewId, mealId: String) {
        firestore.collection("crews").document(crewId.value)
            .collection("meals").document(mealId)
            .delete()
    }

    /** Returns the MealDto for the given mealId, or null if not found. */
    suspend fun readById(crewId: CrewId, mealId: String): MealDto? {
        val snap = firestore.collection("crews").document(crewId.value)
            .collection("meals").document(mealId).get()
        return if (snap.exists) snap.data<MealDto>() else null
    }

    /**
     * Records a peer vote atomically. Returns one of:
     *  - `RateOutcome.Ok` on success
     *  - `RateOutcome.MealNotFound` if the meal document doesn't exist
     *  - `RateOutcome.SelfRating` if the rater is the meal's author
     *  - `RateOutcome.AlreadyRated` if the rater has already used their single vote CHANGE
     *
     * A rater may overwrite their score exactly ONCE: a first vote writes `edited = false`, the
     * one allowed change overwrites it with `edited = true`, and any further attempt (entry already
     * `edited`) returns `AlreadyRated`. Voting-window enforcement and authorization are handled by
     * Firestore security rules; the transaction here covers the read-modify-write cycle for the
     * ratings map plus its denormalized `ratingSum` / `voterCount` aggregates (recomputed from the
     * resulting map, so a change adjusts the sum by the score delta correctly).
     */
    override suspend fun rateMeal(
        crewId: CrewId,
        mealId: String,
        raterUid: String,
        score: Int,
        nowEpochMs: Long,
    ): MealFirestore.RateOutcome {
        val ref = firestore.collection("crews").document(crewId.value)
            .collection("meals").document(mealId)
        return firestore.runTransaction {
            val snap = get(ref)
            if (!snap.exists) return@runTransaction MealFirestore.RateOutcome.MealNotFound
            val dto = snap.data<MealDto>()
            if (raterUid == dto.authorId) return@runTransaction MealFirestore.RateOutcome.SelfRating
            val existing = dto.ratings[raterUid]
            // One change max: a fresh vote (no entry) or the single allowed change (existing entry
            // not yet edited) is accepted; once `edited` is set, the entry is final.
            if (existing != null && existing.edited) return@runTransaction MealFirestore.RateOutcome.AlreadyRated
            // Idempotent re-rate: a replayed command (or a "change" to the identical score) is a
            // no-op — return Ok WITHOUT writing so it never consumes the one allowed change. This
            // keeps an outbox retry of the first vote from flipping `edited` to true behind the
            // user's back.
            if (existing != null && existing.score == score) return@runTransaction MealFirestore.RateOutcome.Ok
            val newRatings = dto.ratings + (raterUid to RatingEntryDto(
                score = score,
                atMs = nowEpochMs,
                edited = existing != null,
            ))
            val newSum = newRatings.values.sumOf { it.score }
            update(ref, mapOf(
                "ratings" to newRatings,
                "ratingSum" to newSum,
                "voterCount" to newRatings.size,
            ))
            MealFirestore.RateOutcome.Ok
        }
    }
}
