package es.schsebastian.foodrats.feature.crew.data.firebase

import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.storageMetadata
import es.schsebastian.foodrats.core.domain.model.CrewId

/**
 * The banner-Storage seam the [es.schsebastian.foodrats.feature.crew.data.repository.FirebaseCrewRepository]
 * orchestrates over. Exists (mirroring [CrewDataSource]) so the repository's setBanner/removeBanner
 * error-classification can be unit-tested with a behavioral fake instead of live Firebase Storage.
 * The concrete [CrewBannerStorageDataSource] is the production implementation.
 */
interface CrewBannerStorage {
    /**
     * Compresses [bytes] under the Storage byte cap and uploads them; returns the content-versioned
     * object path. Throws [BannerImageUnreadableException] / [BannerImageTooLargeException] when the
     * image cannot be compressed (+ raw backend throwables the repository classifies).
     */
    suspend fun upload(crewId: CrewId, bytes: ByteArray): String

    /** Deletes the banner object at [path]; NOT_FOUND-tolerant, other exceptions propagate. */
    suspend fun delete(path: String)
}

/**
 * Uploads, deletes, and resolve-URL for a crew hero/banner image.
 *
 * Mirrors [es.schsebastian.foodrats.feature.auth.data.firebase.AvatarStorageDataSource] exactly —
 * the same content-versioned object PATH, the same "store path, sign URL at read time" posture, and
 * the same NOT_FOUND-tolerant delete-by-path.
 *
 * Path: `crew_banners/{crewId}/{token}.jpg`, where `token` is a content hash of the (resized) bytes.
 * Read URLs are NOT minted here; they go through `ImageUrlPort.resolve(crewId, [path])` at read
 * time in the caller (repository or port binding), keeping the read-signing concern at the data layer.
 */
class CrewBannerStorageDataSource(private val storage: FirebaseStorage) : CrewBannerStorage {

    /**
     * Shrinks [bytes] under the Storage byte cap (see [compressBannerForUpload]) and uploads the
     * result to a CONTENT-VERSIONED object PATH — `crew_banners/{crewId}/{token}.jpg`, where `token`
     * is derived from the (compressed) byte content — then returns that path (NOT a download URL).
     *
     * Why versioned (and not the old fixed `crew_banners/{crewId}/banner.jpg`): a fixed, overwritten
     * path meant `bannerPath` never changed, so [es.schsebastian.foodrats.core.data.image.FirebaseImageUrlResolver]
     * had to treat the banner as MUTABLE and clamp its signed URL to a short (~10-min) client TTL,
     * re-minting a new URL — and re-downloading byte-identical bytes — every ~10 minutes on every
     * banner surface. A new image now yields a new path → the resolver persists its long-lived signed
     * URL → Coil serves the cached bytes for the URL's whole TTL. Re-uploading the *same* image is a
     * genuine no-op (identical token ⇒ identical path). Matches the avatar/plate id convention
     * (`avatars/{uid}/{token}.jpg`, `MealId.forDayToken`, `bytes.contentHashCode().toUInt().toString(16)`).
     *
     * The compression is load-bearing, not just an optimization: the Storage rule caps the object
     * at 2 MB and requires `image/jpeg`. The picker hands us the raw, full-resolution gallery photo
     * (commonly several MB, possibly a PNG); uploading it verbatim is rejected with
     * PERMISSION_DENIED — the "banner does not get uploaded" bug. A compression failure is a TYPED
     * boundary exception ([BannerImageUnreadableException] / [BannerImageTooLargeException]) the
     * repository translates, never a fall-through to the oversized originals. The token hashes the
     * SAME (compressed) bytes that are uploaded, so it faithfully tracks the stored object's content.
     *
     * The path is persisted to `crews/{crewId}.bannerPath` and resolved to a short-lived,
     * membership-checked V4 signed URL at read time via `ImageUrlPort` — same posture as avatars.
     */
    override suspend fun upload(crewId: CrewId, bytes: ByteArray): String {
        val compressed = when (val c = bytes.compressBannerForUpload()) {
            is BannerCompression.Fit -> c.bytes
            BannerCompression.Unreadable -> throw BannerImageUnreadableException
            BannerCompression.TooLarge -> throw BannerImageTooLargeException
        }
        val token = compressed.contentHashCode().toUInt().toString(16)
        val path = "crew_banners/${crewId.value}/$token.jpg"
        storage.reference(path).putData(
            data = compressed.toStorageData(),
            metadata = storageMetadata { contentType = "image/jpeg" },
        )
        return path
    }

    /**
     * Deletes the banner object at the exact [path] (the previous version after a re-upload, or the
     * current one on removal). No-ops if the object does not exist (NOT_FOUND-tolerant), so the
     * Firestore `bannerPath` nullification still proceeds even when the Storage object is already
     * absent. Any other exception (network, auth) propagates to the caller for mapping.
     *
     * Takes the explicit object [path] (not just the crew id) because the path is now content-versioned
     * — the object to delete is whatever `crews/{crewId}.bannerPath` currently points at, which also
     * covers a legacy fixed `crew_banners/{crewId}/banner.jpg` for crews predating versioning.
     */
    override suspend fun delete(path: String) {
        try {
            storage.reference(path).delete()
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

// Vendor-adapter boundary exceptions the repository translates into typed CrewError.Banner leaves —
// same posture as the Firestore datasource's CodeUnknownException et al.
internal object BannerImageUnreadableException : RuntimeException() { private fun readResolve(): Any = BannerImageUnreadableException }
internal object BannerImageTooLargeException : RuntimeException() { private fun readResolve(): Any = BannerImageTooLargeException }
