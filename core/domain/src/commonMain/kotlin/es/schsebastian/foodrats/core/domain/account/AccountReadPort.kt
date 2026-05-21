package es.schsebastian.foodrats.core.domain.account

import es.schsebastian.foodrats.core.domain.model.AccountId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Live identity for an account. Emits `null` when the doc is missing or has been deleted —
 * consumers should render a "Deleted user" placeholder rather than skipping the row.
 *
 * Implementations subscribe to the canonical `accounts/{id}` record; renames and avatar
 * uploads propagate to every open observer.
 */
interface AccountReadPort {
    fun observe(id: AccountId): Flow<Account?>

    /**
     * Resolves many account ids at once. Missing accounts map to `null` so callers can
     * render a "Deleted user" placeholder per id.
     *
     * Default impl combines per-id observers. Fine for crew sizes (≤ 8); if a future caller
     * needs a larger fan-out, override with a batched implementation.
     */
    fun observeMany(ids: Set<AccountId>): Flow<Map<AccountId, Account?>> =
        if (ids.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(ids.map { id -> observe(id).map { id to it } }) { pairs -> pairs.toMap() }
        }
}
