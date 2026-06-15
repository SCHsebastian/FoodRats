package es.schsebastian.foodrats.feature.meal.data.firebase

import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.storageMetadata
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.feature.meal.domain.model.Plate

internal class PlateStorageDataSource(
    private val storage: FirebaseStorage,
    private val compressor: PlateCompressor = PlateCompressor(),
) : PlateStorage {

    /**
     * Uploads a Plate's photo bytes to Firebase Storage and returns the deterministic
     * object PATH (`crews/{crewId}/meals/{mealId}.jpg`) — NOT a download URL.
     *
     * We deliberately no longer mint `getDownloadUrl()` token URLs: that `?token=…` form is
     * world-readable and bypasses Storage rules entirely. The path is persisted instead and
     * resolved to a short-lived, membership-checked V4 signed URL at read time via
     * `ImageUrlPort` (see #15 storage-read hardening).
     *
     * Explicit `contentType = "image/jpeg"` is mandatory: the storage rule asserts
     * `request.resource.contentType.matches('image/jpe?g')`. Android's Firebase Storage
     * SDK infers the type from the `.jpg` path extension; iOS does not — without
     * explicit metadata iOS sends `application/octet-stream` and the rule rejects
     * with PERMISSION_DENIED, surfacing as "No tienes acceso a este grupo."
     *
     * API note: GitLive Firebase Storage 2.1.0 uses an expect/actual `Data` type:
     * - Android actual: `typealias Data = ByteArray`
     * - iOS actual: wrapper `class Data(val data: NSData)` — bridged via [toStorageData].
     */
    override suspend fun upload(crewId: CrewId, mealId: String, plate: Plate): String {
        val path = platefile(crewId, mealId)
        // On-device downscale + re-encode before upload (roadmap §5.1) to cut upload size and
        // storage cost. Best-effort: [PlateCompressor] returns the original bytes on any failure,
        // so this never blocks a publish. The blocking codec work is acceptable here — this is the
        // repository's single IO boundary (publish's `withContext(io)`), not a UI thread.
        val bytes = compressor.compress(plate.photoBytes)
        storage.reference(path).putData(
            data = bytes.toStorageData(),
            metadata = storageMetadata { contentType = "image/jpeg" },
        )
        return path
    }

    /**
     * Deletes the blob at the deterministic upload path. Called for best-effort cleanup when
     * the publish write fails after a successful upload; the repository swallows any failure.
     */
    override suspend fun delete(crewId: CrewId, mealId: String) {
        storage.reference(platefile(crewId, mealId)).delete()
    }

    private fun platefile(crewId: CrewId, mealId: String): String =
        "crews/${crewId.value}/meals/${mealId}.jpg"
}
