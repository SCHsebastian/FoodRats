package es.schsebastian.foodrats.feature.crew.data.firebase

import dev.gitlive.firebase.storage.FirebaseStorage
import es.schsebastian.foodrats.core.domain.model.AccountId

class AvatarStorageDataSource(private val storage: FirebaseStorage) {

    /**
     * Uploads JPEG bytes to `avatars/{uid}.jpg`, overwriting any previous file, and returns
     * the public download URL. The URL embeds a Firebase access token so it remains valid
     * after future overwrites (the token survives object overwrite; the path stays stable).
     */
    suspend fun upload(accountId: AccountId, bytes: ByteArray): String {
        val ref = storage.reference("avatars/${accountId.value}.jpg")
        ref.putData(bytes.toStorageData())
        return ref.getDownloadUrl()
    }
}
