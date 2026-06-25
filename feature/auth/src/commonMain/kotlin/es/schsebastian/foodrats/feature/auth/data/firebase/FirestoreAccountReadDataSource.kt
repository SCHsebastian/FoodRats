package es.schsebastian.foodrats.feature.auth.data.firebase

import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.image.ImageUrlPort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

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
    private val session: SessionProvider,
) : AccountReadPort {
    override fun observe(id: AccountId): Flow<Account?> = flow {
        val snapshots = source.snapshots(id.value)
        emitAll(
            combine(snapshots, activeCrew.current, session.current) { dto, crewId, sess ->
                val account = dto?.toAccount() ?: return@combine null
                // `account.avatarUrl` holds the avatar PATH at this layer (see AccountMapper).
                account.copy(
                    avatarUrl = resolveAvatar(account.avatarUrl, crewId, isSelf = sess?.accountId == id),
                )
            }
                .distinctUntilChanged()
        )
    }

    private suspend fun resolveAvatar(path: String?, crewId: CrewId?, isSelf: Boolean): String? {
        if (path == null) return null
        return when {
            // In a crew: any member's avatar resolves through the crew-scoped signed-URL mint.
            crewId != null -> imageUrls.resolve(crewId, listOf(path)).getOrNull()?.get(path)
            // No active crew yet (just signed up / left the only crew): still resolve the CALLER'S
            // OWN avatar so it isn't blank in their own top bar/profile. Others' avatars need a crew.
            isSelf -> imageUrls.resolveOwnAvatar(path).getOrNull()
            else -> null
        }
    }
}
