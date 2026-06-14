package es.schsebastian.foodrats.feature.meal.data.firebase

import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.storageMetadata
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.feature.meal.domain.model.Plate

class PlateStorageDataSource(private val storage: FirebaseStorage) : PlateStorage {

    /**
     * Uploads a Plate's photo bytes to Firebase Storage and returns the download URL.
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
        val ref = storage.reference(platefile(crewId, mealId))
        ref.putData(
            data = plate.photoBytes.toStorageData(),
            metadata = storageMetadata { contentType = "image/jpeg" },
        )
        return ref.getDownloadUrl()
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
