package es.schsebastian.foodrats.feature.notifications.data.firebase

import dev.gitlive.firebase.firestore.FirebaseFirestore
import es.schsebastian.foodrats.core.domain.model.AccountId

class DeviceTokenFirestoreDataSource(private val firestore: FirebaseFirestore) {

    /** Writes accounts/{uid}/devices/{token} = DeviceTokenDto. Token doubles as doc ID so rotations are idempotent. */
    suspend fun upsert(accountId: AccountId, dto: DeviceTokenDto) {
        val tokenValue = dto.token ?: return
        firestore
            .collection("accounts")
            .document(accountId.value)
            .collection("devices")
            .document(tokenValue)
            .set(dto)
    }

    /** Deletes accounts/{uid}/devices/{token}. Token is the doc ID. Deleting a missing doc is a no-op. */
    suspend fun delete(accountId: AccountId, token: String) {
        firestore
            .collection("accounts")
            .document(accountId.value)
            .collection("devices")
            .document(token)
            .delete()
    }
}
