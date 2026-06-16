package es.schsebastian.foodrats.feature.auth.data.firebase

import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.image.ImageUrlPort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.getOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

/**
 * The single avatar-resolution point: the account DTO carries the avatar Storage PATH, which
 * this datasource resolves to a membership-checked signed URL (via [ImageUrlPort], scoped to
 * the [ActiveCrewProvider] crew) before any consumer sees the [Account]. Resolving here means
 * every avatar surface — feed, stats, comments, profile, crew list, top bar — gets signed
 * URLs for free, with no per-ViewModel wiring and ViewModels staying I/O-free.
 */
class FirestoreAccountReadDataSource(
    private val source: AccountSnapshotSource,
    private val imageUrls: ImageUrlPort,
    private val activeCrew: ActiveCrewProvider,
) : AccountReadPort {
    override fun observe(id: AccountId): Flow<Account?> = flow {
        val snapshots = source.snapshots(id.value)
        emitAll(
            combine(snapshots, activeCrew.current) { dto, crewId -> dto to crewId }
                .map { (dto, crewId) ->
                    val account = dto?.toAccount() ?: return@map null
                    // `account.avatarUrl` holds the avatar PATH at this layer (see AccountMapper).
                    account.copy(avatarUrl = resolveAvatar(account.avatarUrl, crewId))
                }
                .distinctUntilChanged()
        )
    }

    private suspend fun resolveAvatar(path: String?, crewId: CrewId?): String? {
        if (path == null || crewId == null) return null
        return imageUrls.resolve(crewId, listOf(path)).getOrNull()?.get(path)
    }
}
