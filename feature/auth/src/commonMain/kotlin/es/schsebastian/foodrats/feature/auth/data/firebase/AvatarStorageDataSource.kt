package es.schsebastian.foodrats.feature.auth.data.firebase

import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.storageMetadata
import es.schsebastian.foodrats.core.domain.model.AccountId

class AvatarStorageDataSource(private val storage: FirebaseStorage) {

    /**
     * Uploads JPEG bytes to a CONTENT-VERSIONED object PATH — `avatars/{uid}/{token}.jpg`,
     * where `token` is derived from the byte content — and returns that path (NOT a download URL).
     *
     * Why versioned (and not the old fixed `avatars/{uid}.jpg`): a fixed path made re-uploads
     * invisible until a full app restart. The Firestore `avatarPath` string never changed, so the
     * `accounts/{uid}` snapshot `StateFlow` deduped (no re-emit), the `ImageUrlPort` cache returned
     * the same signed URL for the same path, and Coil — keyed on that URL — served the stale bitmap.
     * A new image now yields a new path → the DTO changes → the snapshot re-emits → the URL cache
     * misses → a fresh signed URL → Coil re-downloads. Re-uploading the *same* image is a genuine
     * no-op (identical token ⇒ identical path). Matches the meal-plate id convention
     * (`MealId.forDayToken`, `bytes.contentHashCode().toUInt().toString(16)`).
     *
     * We don't mint `getDownloadUrl()` token URLs: that `?token=…` form is world-readable and
     * bypasses Storage rules. The path is persisted to `accounts/{uid}.avatarPath` and resolved to a
     * short-lived, membership-checked V4 signed URL at read time via `ImageUrlPort` (see #15).
     *
     * `contentType = "image/jpeg"` is required by the Storage security rule
     * (`contentType.matches('image/jpe?g')`). Android infers from `.jpg`; iOS does not.
     */
    suspend fun upload(accountId: AccountId, bytes: ByteArray): String {
        val token = bytes.contentHashCode().toUInt().toString(16)
        val path = "avatars/${accountId.value}/$token.jpg"
        storage.reference(path).putData(
            data = bytes.toStorageData(),
            metadata = storageMetadata { contentType = "image/jpeg" },
        )
        return path
    }

    /**
     * Deletes the avatar object at the exact [path] (e.g. the previous version after a re-upload, or
     * the current one on removal). No-ops if the object does not exist (the Firebase SDK raises
     * `StorageException` with code NOT_FOUND; we swallow it so a Firestore nullification still
     * proceeds). Any other exception propagates to the caller for mapping.
     */
    suspend fun delete(path: String) {
        try {
            storage.reference(path).delete()
        } catch (e: Exception) {
            // Swallow NOT_FOUND: the object is already absent, which is the goal.
            // Any other exception (network, auth) should propagate.
            if (!isNotFound(e)) throw e
        }
    }

    /** Returns true when [e] is a Firebase Storage NOT_FOUND exception. */
    private fun isNotFound(e: Exception): Boolean =
        e.message?.contains("does not exist") == true ||
            e.message?.contains("object-not-found") == true ||
            e.message?.contains("404") == true
}
