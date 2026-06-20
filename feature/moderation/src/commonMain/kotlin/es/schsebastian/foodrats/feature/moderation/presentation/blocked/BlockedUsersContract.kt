package es.schsebastian.foodrats.feature.moderation.presentation.blocked

import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.account.BlockError
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState

data class BlockedUsersState(
    val loading: Boolean = true,
    /** The signed-in account, used to default the block-list owner for unblock writes. */
    val myAccountId: AccountId? = null,
    /** Ordered list of currently-blocked account ids. */
    val blockedIds: List<AccountId> = emptyList(),
    /**
     * Live identities resolved from `AccountReadPort.observeMany`. A `null` value means the account
     * doc is missing or deleted — the screen renders the localized deleted-user fallback.
     */
    val identities: Map<AccountId, Account?> = emptyMap(),
    /** Accounts whose unblock write is in flight; their row renders disabled + a spinner. */
    val unblockingIds: Set<AccountId> = emptySet(),
    val error: BlockError? = null,
) : MviState

sealed interface BlockedUsersIntent : MviIntent {
    data class Unblock(val accountId: AccountId) : BlockedUsersIntent
    data object DismissError : BlockedUsersIntent
}

sealed interface BlockedUsersEffect : MviEffect
