package es.schsebastian.foodrats.feature.meal.data.firebase

import dev.gitlive.firebase.storage.FirebaseStorage
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.feature.meal.domain.model.Plate

class PlateStorageDataSource(private val storage: FirebaseStorage) {

    /**
     * Uploads a Plate's photo bytes to Firebase Storage and returns the download URL.
     *
     * API note: GitLive Firebase Storage 2.1.0 common API exposes
     * `StorageReference.putData(data: ByteArray, metadata: FirebaseStorageMetadata? = null)`
     * as an `expect` function. The iOS actual converts ByteArray → NSData internally.
     */
    suspend fun upload(crewId: CrewId, mealId: String, plate: Plate): String {
        val ref = storage.reference("crews/${crewId.value}/meals/${mealId}.jpg")
        ref.putData(plate.photoBytes)
        return ref.getDownloadUrl()
    }
}
