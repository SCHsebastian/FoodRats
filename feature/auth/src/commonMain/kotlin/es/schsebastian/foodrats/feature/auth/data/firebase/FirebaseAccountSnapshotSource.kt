package es.schsebastian.foodrats.feature.auth.data.firebase

import dev.gitlive.firebase.firestore.FirebaseFirestore
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
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
                    // On sign-out the auth token is revoked while this `accounts/{uid}`
                    // listener is still attached, so Firestore fires PERMISSION_DENIED into
                    // the flow. Without this `.catch` the exception escapes `stateIn`'s
                    // sharing coroutine into `scope` and — on iOS/Native — an uncaught
                    // coroutine exception terminates the process (SIGABRT). Mapping it to a
                    // null DTO mirrors "doc missing" and downstream renders gracefully.
                    // (Same hazard documented in FirebaseMealRepository.crewStream.)
                    .catch { t ->
                        FrLog.w("AccountSnapshot", t) { "accounts/$uid snapshot throw: ${t.message}" }
                        emit(null)
                    }
                    .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)
            }
        }
}
