package es.schsebastian.foodrats.feature.moderation.data.repository

import es.schsebastian.foodrats.core.domain.account.BlockError
import es.schsebastian.foodrats.core.domain.account.BlockedAccountsPort
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.moderation.data.firebase.BlockDataSource
import es.schsebastian.foodrats.feature.moderation.data.firebase.BlockErrorMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Firestore-backed [BlockedAccountsPort] (UGC compliance §5). Owns the dispatcher boundary (exactly one
 * `withContext(dispatchers.io)` per public write) and the vendor-throwable → typed-[BlockError]
 * translation via [BlockErrorMapper]. Mirrors `FirebaseCrewRepository`.
 *
 * [observeBlocked] maps the raw uid set to [AccountId]s on the IO dispatcher; an upstream throw (e.g. a
 * sign-out PERMISSION_DENIED) is swallowed to `emptySet()` so the feed/stat exclusion observers stay
 * alive (a failed read defaults to "nothing blocked", the safe direction — content is shown, not hidden).
 */
internal class BlockRepository(
    private val dataSource: BlockDataSource,
    private val dispatchers: DispatcherProvider,
    private val errorMapper: BlockErrorMapper,
    private val clock: Clock,
) : BlockedAccountsPort {

    override fun observeBlocked(owner: AccountId): Flow<Set<AccountId>> =
        dataSource.observeBlocked(owner.value)
            .map { uids -> uids.mapNotNull { (AccountId.of(it) as? Result.Ok)?.value }.toSet() }
            .catch { emit(emptySet()) }
            .distinctUntilChanged()
            .flowOn(dispatchers.io)

    override suspend fun block(owner: AccountId, target: AccountId): Result<Unit, BlockError> {
        if (owner == target) return Result.failure(BlockError.Write.SelfBlock)
        return withContext(dispatchers.io) {
            runCatching { dataSource.block(owner.value, target.value, clock.now().toEpochMilliseconds()) }
                .fold(
                    onSuccess = { Result.success(Unit) },
                    onFailure = { Result.failure(errorMapper.map(it)) },
                )
        }
    }

    override suspend fun unblock(owner: AccountId, target: AccountId): Result<Unit, BlockError> =
        withContext(dispatchers.io) {
            runCatching { dataSource.unblock(owner.value, target.value) }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { Result.failure(errorMapper.map(it)) },
            )
        }
}
