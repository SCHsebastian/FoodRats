package es.schsebastian.foodrats.feature.auth.data.firebase

import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.model.AccountId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Thin seam that yields the latest `accounts/{uid}` DTO snapshot (null when the doc is
 * missing). Concrete Firebase implementation lives next door — splitting it lets unit
 * tests run on the JVM/iOS Sim without spinning up Firebase.
 */
interface AccountSnapshotSource {
    suspend fun snapshots(uid: String): StateFlow<AccountDto?>
}

class FirestoreAccountReadDataSource(
    private val source: AccountSnapshotSource,
) : AccountReadPort {
    override fun observe(id: AccountId): Flow<Account?> = flow {
        emitAll(
            source.snapshots(id.value)
                .map { dto -> dto?.toAccount() }
                .distinctUntilChanged()
        )
    }
}
