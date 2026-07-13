package es.schsebastian.foodrats.feature.meal.data.firebase

import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.storageMetadata
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.feature.meal.domain.model.Plate

/**
 * `Cache-Control` for the immutable, content-addressed plate object (30-day max-age + `immutable`).
 * Safe ONLY because the plate path embeds the photo-hash token, so the bytes never change at a
 * given path; the fixed-path crew banner deliberately does NOT get this header.
 */
private const val IMMUTABLE_PLATE_CACHE_CONTROL = "public, max-age=2592000, immutable"

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
     * with PERMISSION_DENIED, surfacing as "No tienes acceso a esta cuadrilla."
     *
     * API note: GitLive Firebase Storage 2.1.0 uses an expect/actual `Data` type:
     * - Android actual: `typealias Data = ByteArray`
     * - iOS actual: wrapper `class Data(val data: NSData)` — bridged via [toStorageData].
     */
    override suspend fun upload(crewId: CrewId, mealId: String, index: Int, plate: Plate): String {
        val path = platefile(crewId, mealId, index)
        // On-device downscale + re-encode before upload (roadmap §5.1) to cut upload size and
        // storage cost. Best-effort: [PlateCompressor] returns the original bytes on any failure,
        // so this never blocks a publish. The blocking codec work is acceptable here — this is the
        // repository's single IO boundary (publish's `withContext(io)`), not a UI thread.
        val bytes = compressor.compress(plate.photoBytes)
        storage.reference(path).putData(
            data = bytes.toStorageData(),
            metadata = storageMetadata {
                contentType = "image/jpeg"
                // The plate path is content-addressed (`mealId` = photo-hash token) so the bytes
                // here NEVER change — mark it immutable so a memory-evicted image serves from the
                // HTTP/disk cache instead of re-downloading (IMAGE-6). The server trigger
                // (`onPlateImageFinalized`) back-fills the same header for older clients.
                cacheControl = IMMUTABLE_PLATE_CACHE_CONTROL
            },
        )
        return path
    }

    /**
     * Deletes the blob at the deterministic upload path. Called for best-effort cleanup when
     * the publish write fails after a successful upload; the repository swallows any failure.
     */
    override suspend fun delete(crewId: CrewId, mealId: String, index: Int) {
        storage.reference(platefile(crewId, mealId, index)).delete()
    }

    /** `index` 0 = the legacy single-photo path; `index` n >= 1 = the nth extra photo. */
    private fun platefile(crewId: CrewId, mealId: String, index: Int): String =
        if (index == 0) "crews/${crewId.value}/meals/${mealId}.jpg"
        else "crews/${crewId.value}/meals/${mealId}_p$index.jpg"
}
