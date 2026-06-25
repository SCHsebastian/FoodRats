package es.schsebastian.foodrats.core.domain.account

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * The signed-in user's block list (UGC compliance §5 — "ability to block abusive users").
 *
 * [observeBlocked] is the single live source feed / meal-detail / stats subscribe to in order to
 * exclude blocked authors' content client-side; the port is declared here in `:core:domain` so those
 * consumers never gain a dependency on the implementing feature. Implemented in `:feature:moderation`
 * over `accounts/{owner}/blocks/{blocked}` (owner-private).
 *
 * Mirrors the [AccountReadPort] / `MealReadPort` style: a `Flow` for live reads, suspending writes that
 * return a typed [Result]. Block is one-directional and local — it hides the blocked user's content
 * from the owner; it does not notify or restrict the blocked user.
 */
interface BlockedAccountsPort {
    /** Live set of account ids [owner] has blocked. Emits `emptySet()` when nothing is blocked. */
    fun observeBlocked(owner: AccountId): Flow<Set<AccountId>>

    /** Adds [target] to [owner]'s block list. Blocking yourself fails with [BlockError.Write.SelfBlock]. */
    suspend fun block(owner: AccountId, target: AccountId): Result<Unit, BlockError>

    /** Removes [target] from [owner]'s block list. Unblocking a non-blocked account is a no-op success. */
    suspend fun unblock(owner: AccountId, target: AccountId): Result<Unit, BlockError>
}
