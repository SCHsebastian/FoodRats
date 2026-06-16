package es.schsebastian.foodrats.core.domain.achievement

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Cross-context contract for the persistence of unlocked achievements (spec §6.1). Declared in
 * [:core:domain] — not in `:feature:achievements` — for the same reason as `MealReadPort`: the
 * domain declares the port; the feature's data layer implements it (and the presentation layer
 * observes it).
 *
 * Persistence is intentionally minimal: earning is client-derived from meal history (the pure
 * achievement evaluator runs against the feature's signals); only the **unlock timestamp** is
 * stored so the "earned on" date survives across re-evaluations and devices. Locked achievements
 * have no persisted record (absence = locked).
 *
 * The unlock map is keyed by the raw [String] achievement id (the Firestore document id) rather
 * than a feature type so this port stays free of feature types — the feature maps
 * `String -> AchievementId` against its catalog on read, dropping unknown ids from a future app
 * version (the "drop-on-read" stance used elsewhere).
 */
interface AchievementProgressPort {
    /** All persisted unlocks for [accountId], keyed by the raw achievement id → unlock epoch-ms. */
    fun observeUnlocks(accountId: AccountId): Flow<Result<Map<String, Long>, AchievementProgressError>>

    /**
     * Idempotent: writes unlock timestamps for the given ids in one batch. The caller passes only
     * the ids it considers newly-unlocked; a [newlyUnlocked] that is empty is a no-op that returns
     * [Result.Ok].
     */
    suspend fun recordUnlocks(
        accountId: AccountId,
        newlyUnlocked: Map<String, Long>,
    ): Result<Unit, AchievementProgressError>
}
