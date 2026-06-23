package es.schsebastian.foodrats.feature.crew.data.firebase

import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.storageMetadata
import es.schsebastian.foodrats.core.domain.model.CrewId

/**
 * Uploads, deletes, and resolve-URL for a crew hero/banner image.
 *
 * Mirrors [es.schsebastian.foodrats.feature.auth.data.firebase.AvatarStorageDataSource] exactly —
 * the same "store path, sign URL at read time" posture, and the same NOT_FOUND-tolerant delete.
 *
 * Path: `crew_banners/{crewId}/banner.jpg` (deterministic — one object per crew).
 * Read URLs are NOT minted here; they go through `ImageUrlPort.resolve(crewId, [path])` at read
 * time in the caller (repository or port binding), keeping the read-signing concern at the data layer.
 */
class CrewBannerStorageDataSource(private val storage: FirebaseStorage) {

    /**
     * Downscales + re-encodes [bytes] to a small JPEG (see [resizeBannerForUpload]) and uploads it
     * to `crew_banners/{crewId}/banner.jpg`, overwriting any previous file, then returns the
     * deterministic object PATH — NOT a download URL.
     *
     * The compression is load-bearing, not just an optimization: the Storage rule caps the object
     * at 2 MB and requires `image/jpeg`. The picker hands us the raw, full-resolution gallery photo
     * (commonly several MB, possibly a PNG); uploading it verbatim is rejected with
     * PERMISSION_DENIED — the "banner does not get uploaded" bug. Re-encoding makes both the size
     * limit and the declared `contentType = "image/jpeg"` hold.
     *
     * The path is persisted to `crews/{crewId}.bannerPath` and resolved to a short-lived,
     * membership-checked V4 signed URL at read time via `ImageUrlPort` — same posture as avatars.
     */
    suspend fun upload(crewId: CrewId, bytes: ByteArray): String {
        val path = "crew_banners/${crewId.value}/banner.jpg"
        storage.reference(path).putData(
            data = bytes.resizeBannerForUpload().toStorageData(),
            metadata = storageMetadata { contentType = "image/jpeg" },
        )
        return path
    }

    /**
     * Deletes the banner object. No-ops if the object does not exist (NOT_FOUND-tolerant), so the
     * Firestore `bannerPath` nullification still proceeds even when the Storage object is already
     * absent. Any other exception (network, auth) propagates to the caller for mapping.
     */
    suspend fun delete(crewId: CrewId) {
        try {
            storage.reference("crew_banners/${crewId.value}/banner.jpg").delete()
        } catch (e: Exception) {
            if (!isNotFound(e)) throw e
        }
    }

    /** Returns true when [e] is a Firebase Storage NOT_FOUND exception. */
    private fun isNotFound(e: Exception): Boolean =
        e.message?.contains("does not exist") == true ||
            e.message?.contains("object-not-found") == true ||
            e.message?.contains("404") == true
}
