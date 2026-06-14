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
}
