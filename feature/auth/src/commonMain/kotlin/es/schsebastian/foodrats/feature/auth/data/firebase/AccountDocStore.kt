package es.schsebastian.foodrats.feature.auth.data.firebase

import dev.gitlive.firebase.firestore.FirebaseFirestore
import es.schsebastian.foodrats.core.domain.time.Clock

interface AccountDocStore {
    suspend fun read(uid: String): AccountDto?
    suspend fun upsert(uid: String, dto: AccountDto)
}

class FirestoreAccountDocStore(
    private val firestore: FirebaseFirestore,
) : AccountDocStore {
    override suspend fun read(uid: String): AccountDto? {
        val snap = firestore.collection("accounts").document(uid).get()
        return if (snap.exists) snap.data<AccountDto>() else null
    }

    override suspend fun upsert(uid: String, dto: AccountDto) {
        firestore.collection("accounts").document(uid).set(dto, merge = true)
    }
}

internal class AccountDocSelfHealer(
    private val store: AccountDocStore,
    private val clock: Clock,
    private val googleName: () -> String,
) {
    suspend fun ensureAccountDoc(uid: String): AccountDto {
        val name = googleName()
        val existing = store.read(uid)
        val needsCreate = existing == null
        val needsHeal = existing != null &&
            (existing.displayName.isNullOrBlank() || existing.displayName == "Rat")
        if (!needsCreate && !needsHeal) return checkNotNull(existing)

        val dto = (existing ?: AccountDto(
            id = uid,
            handle = uid.take(8),
            createdAtEpochMs = clock.now().toEpochMilliseconds(),
        )).copy(displayName = name)
        store.upsert(uid, dto)
        return dto
    }
}
