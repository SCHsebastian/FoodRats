package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.coroutines.flow.Flow

/**
 * The Firestore operations the meal repository orchestrates, expressed as a thin
 * data-layer port over [MealFirestoreDataSource]. The concrete data source is the
 * only Firestore-touching implementation; this interface exists so the repository's
 * vendor-translation + orchestration (publish/rate/delete) is verifiable in
 * `commonTest` with a behavioral fake — the exact seam the planned Firebase→own-server
 * swap depends on.
 *
 * Data-layer-private: never leaves `data/firebase/`. The repository depends on this,
 * not on the concrete class, so a backend swap re-implements the interface and changes
 * one Koin binding.
 */
internal interface MealFirestore {

    fun observeForRange(crewId: CrewId, from: MealDay, to: MealDay): Flow<List<MealDto>>

    /**
     * The ids of every meal this author has published in [crewId] on [dayKey]. Powers both the
     * daily-cap count (`size`) and the idempotency check (membership test against the deterministic
     * `MealId.forDayToken(...)`). One Firestore read.
     */
    suspend fun existingMealIds(crewId: CrewId, authorId: AccountId, dayKey: String): Set<String>

    suspend fun deleteMeal(crewId: CrewId, mealId: String)

    suspend fun write(dto: MealDto, docId: String)

    suspend fun rateMeal(
        crewId: CrewId,
        mealId: String,
        raterUid: String,
        score: Int,
        nowEpochMs: Long,
    ): RateOutcome

    /** Outcome of the [rateMeal] transaction. */
    sealed interface RateOutcome {
        data object Ok : RateOutcome
        data object MealNotFound : RateOutcome
        data object SelfRating : RateOutcome
        data object AlreadyRated : RateOutcome
    }
}
