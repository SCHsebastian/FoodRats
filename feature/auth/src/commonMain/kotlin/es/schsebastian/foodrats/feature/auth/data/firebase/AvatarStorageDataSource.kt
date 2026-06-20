package es.schsebastian.foodrats.feature.auth.data.firebase

import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.storageMetadata
import es.schsebastian.foodrats.core.domain.model.AccountId

class AvatarStorageDataSource(private val storage: FirebaseStorage) {

    /**
     * Uploads JPEG bytes to `avatars/{uid}.jpg`, overwriting any previous file, and returns
     * the deterministic object PATH — NOT a download URL.
     *
     * We no longer mint `getDownloadUrl()` token URLs: that `?token=…` form is world-readable
     * and bypasses Storage rules. The path is persisted to `accounts/{uid}.avatarPath` and
     * resolved to a short-lived, membership-checked V4 signed URL at read time via
     * `ImageUrlPort` (see #15 storage-read hardening).
     *
     * `contentType = "image/jpeg"` is required by the Storage security rule
     * (`contentType.matches('image/jpe?g')`). Android infers from `.jpg`; iOS does not.
     */
    suspend fun upload(accountId: AccountId, bytes: ByteArray): String {
        val path = "avatars/${accountId.value}.jpg"
        storage.reference(path).putData(
            data = bytes.toStorageData(),
            metadata = storageMetadata { contentType = "image/jpeg" },
        )
        return path
    }

    /**
     * Deletes the avatar object at `avatars/{uid}.jpg`. No-ops if the object does not
     * exist (the Firebase SDK raises `StorageException` with code NOT_FOUND; we swallow
     * it so the Firestore nullification still proceeds). Throwing any other exception
     * propagates to the caller for mapping.
     */
    suspend fun delete(accountId: AccountId) {
        try {
            storage.reference("avatars/${accountId.value}.jpg").delete()
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
