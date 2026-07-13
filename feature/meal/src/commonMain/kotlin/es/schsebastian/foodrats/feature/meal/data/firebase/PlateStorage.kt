package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.feature.meal.domain.model.Plate

/**
 * The single plate-photo upload operation the meal repository orchestrates, expressed
 * as a thin data-layer port over [PlateStorageDataSource]. Lets the repository's publish
 * orchestration (and its upload-failure mapping) be verified in `commonTest` without a
 * live Firebase Storage bucket.
 *
 * Data-layer-private: never leaves `data/firebase/`.
 */
internal interface PlateStorage {
    /**
     * Uploads one photo of an ordered multi-photo meal and returns its deterministic Storage
     * object path — resolved to a signed URL at read time. [index] is the 0-based position in
     * [es.schsebastian.foodrats.feature.meal.domain.model.MealDraft.plates]: `0` uploads to the
     * legacy single-photo path (`crews/{crewId}/meals/{mealId}.jpg`), `n >= 1` uploads to
     * `crews/{crewId}/meals/{mealId}_p{n}.jpg`.
     */
    suspend fun upload(crewId: CrewId, mealId: String, index: Int, plate: Plate): String

    /**
     * Deletes the plate photo at the deterministic upload path for [crewId]/[mealId]/[index]
     * (see [upload]).
     *
     * Used for best-effort cleanup when the publish Firestore write fails after a successful
     * upload, so the orphaned blob doesn't linger (a cost + privacy leak). The repository
     * swallows any failure here — cleanup must never mask the original publish error.
     */
    suspend fun delete(crewId: CrewId, mealId: String, index: Int)
}
