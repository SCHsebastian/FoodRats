package es.schsebastian.foodrats.core.domain.account

import es.schsebastian.foodrats.core.domain.model.AccountId
import kotlinx.coroutines.flow.Flow

/**
 * Live identity for an account. Emits `null` when the doc is missing or has been deleted —
 * consumers should render a "Deleted user" placeholder rather than skipping the row.
 *
 * Implementations subscribe to the canonical `accounts/{id}` record; renames and avatar
 * uploads propagate to every open observer.
 */
interface AccountReadPort {
    fun observe(id: AccountId): Flow<Account?>
}
