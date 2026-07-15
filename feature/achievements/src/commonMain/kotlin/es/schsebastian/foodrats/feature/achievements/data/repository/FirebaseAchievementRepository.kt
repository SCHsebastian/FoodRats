package es.schsebastian.foodrats.feature.achievements.data.repository

import es.schsebastian.foodrats.core.domain.achievement.AchievementProgressError
import es.schsebastian.foodrats.core.domain.achievement.AchievementProgressPort
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.achievements.data.firebase.AchievementErrorMapper
import es.schsebastian.foodrats.feature.achievements.data.firebase.AchievementUnlockStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Firestore implementation of [AchievementProgressPort] (spec §6.2). Deliberately thin: it persists
 * and streams unlock timestamps only. Earning is client-derived — the pure `AchievementEvaluator`
 * runs in the feature and the ViewModel overlays this port's persisted map onto the evaluation
 * (spec §6.3/§7); reconcile/persist-on-unlock therefore lives in the presentation layer, NOT here,
 * so the evaluator stays pure and the repository keeps exactly one I/O boundary per method.
 *
 * Boundary convention (CLAUDE.md): the streaming read [observeUnlocks] is a cold [Flow] with NO
 * `withContext` (matching the `MealReadPort` Firestore impls); only the one-shot write
 * [recordUnlocks] carries the single `withContext(dispatchers.io)`.
 */
internal class FirebaseAchievementRepository(
    private val dataSource: AchievementUnlockStore,
    private val dispatchers: DispatcherProvider,
    private val errorMapper: AchievementErrorMapper,
) : AchievementProgressPort {

    override fun observeUnlocks(
        accountId: AccountId,
    ): Flow<Result<Map<String, Long>, AchievementProgressError>> =
        dataSource.observeUnlocks(accountId.value)
            .map<Map<String, Long>, Result<Map<String, Long>, AchievementProgressError>> {
                Result.success(it)
            }
            .distinctUntilChanged()
            .catch { emit(Result.failure(errorMapper.map(it))) }

    override suspend fun recordUnlocks(
        accountId: AccountId,
        newlyUnlocked: Map<String, Long>,
    ): Result<Unit, AchievementProgressError> {
        if (newlyUnlocked.isEmpty()) return Result.success(Unit)
        return withContext(dispatchers.io) {
            runCatching { dataSource.recordUnlocks(accountId.value, newlyUnlocked) }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { Result.failure(errorMapper.map(it)) },
            )
        }
    }
}
