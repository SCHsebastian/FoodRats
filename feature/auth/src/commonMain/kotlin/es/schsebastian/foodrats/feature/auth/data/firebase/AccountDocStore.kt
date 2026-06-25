package es.schsebastian.foodrats.feature.auth.data.firebase

import dev.gitlive.firebase.firestore.FirebaseFirestore
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import kotlinx.coroutines.withContext

interface AccountDocStore {
    suspend fun read(uid: String): AccountDto?
    suspend fun upsert(uid: String, dto: AccountDto)
}

class FirestoreAccountDocStore(
    private val firestore: FirebaseFirestore,
    private val dispatchers: DispatcherProvider,
) : AccountDocStore {
    // Each public method owns its IO boundary (CLAUDE.md rule) so the contract is self-protecting:
    // a future caller that forgets to wrap can't run Firestore I/O on the caller's dispatcher.
    override suspend fun read(uid: String): AccountDto? = withContext(dispatchers.io) {
        val snap = firestore.collection("accounts").document(uid).get()
        if (snap.exists) snap.data<AccountDto>() else null
    }

    override suspend fun upsert(uid: String, dto: AccountDto): Unit = withContext(dispatchers.io) {
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
