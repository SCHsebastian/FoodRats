package es.schsebastian.foodrats.feature.auth.data.firebase

import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FirebaseAccountSnapshotSource(
    private val firestore: FirebaseFirestore,
    private val scope: CoroutineScope,
) : AccountSnapshotSource {
    private val cacheLock = Mutex()
    private val cache = mutableMapOf<String, StateFlow<AccountDto?>>()

    override suspend fun snapshots(uid: String): StateFlow<AccountDto?> =
        cacheLock.withLock {
            cache.getOrPut(uid) {
                firestore.collection("accounts").document(uid).snapshots
                    .map { snap -> if (snap.exists) snap.data<AccountDto>() else null }
                    .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)
            }
        }
}
