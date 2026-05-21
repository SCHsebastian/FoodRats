package es.schsebastian.foodrats.feature.auth.data.firebase

import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.storageMetadata
import es.schsebastian.foodrats.core.domain.model.AccountId

class AvatarStorageDataSource(private val storage: FirebaseStorage) {

    /**
     * Uploads JPEG bytes to `avatars/{uid}.jpg`, overwriting any previous file, and returns
     * the public download URL. The URL embeds a Firebase access token so it remains valid
     * after future overwrites (the token survives object overwrite; the path stays stable).
     *
     * `contentType = "image/jpeg"` is required by the Storage security rule
     * (`contentType.matches('image/jpe?g')`). Android infers from `.jpg`; iOS does not.
     */
    suspend fun upload(accountId: AccountId, bytes: ByteArray): String {
        val ref = storage.reference("avatars/${accountId.value}.jpg")
        ref.putData(
            data = bytes.toStorageData(),
            metadata = storageMetadata { contentType = "image/jpeg" },
        )
        return ref.getDownloadUrl()
    }
}
