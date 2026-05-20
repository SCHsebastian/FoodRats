package es.schsebastian.foodrats.feature.crew.data.firebase

import dev.gitlive.firebase.firestore.FirebaseFirestore
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId

/**
 * Abstracts the two-document write that must happen atomically when a member renames
 * themselves: `accounts/{uid}.displayName` (canonical) and
 * `crews/{crewId}.members.{uid}.displayName` (denormalized crew view).
 *
 * Keeping both in a single Firestore batch commit prevents the records from diverging if
 * one write succeeds and the other fails.
 */
interface CrewMemberWriter {
    suspend fun renameAndPropagate(
        crewId: CrewId,
        accountId: AccountId,
        newDisplayName: String,
    )
}

/**
 * Production implementation using a Firestore batch so both documents are written
 * atomically.
 */
class FirestoreCrewMemberWriter(
    private val firestore: FirebaseFirestore,
) : CrewMemberWriter {

    override suspend fun renameAndPropagate(
        crewId: CrewId,
        accountId: AccountId,
        newDisplayName: String,
    ) {
        firestore.batch().apply {
            update(
                firestore.collection("accounts").document(accountId.value),
                "displayName" to newDisplayName,
            )
            update(
                firestore.collection("crews").document(crewId.value),
                "members.${accountId.value}.displayName" to newDisplayName,
            )
        }.commit()
    }
}
