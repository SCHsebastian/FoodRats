package es.schsebastian.foodrats.feature.moderation.presentation.blocked

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.account.BlockedAccountsPort
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Lists the signed-in user's blocked accounts (UGC compliance §5) and lets them unblock.
 *
 * Single source of truth: [BlockedAccountsPort.observeBlocked] drives `blockedIds`; the member
 * identities are resolved by feeding `state.blockedIds` into [AccountReadPort.observeMany] (derived
 * from state via `map` + `distinctUntilChanged` + `flatMapLatest` — the FeedViewModel pattern), never a
 * parallel `MutableStateFlow`. `analytics` is held for the later analytics-events phase; no leaf exists
 * for block/unblock yet, so nothing is tracked here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BlockedUsersViewModel(
    private val blocked: BlockedAccountsPort,
    private val accountRead: AccountReadPort,
    private val session: SessionProvider,
    private val analytics: AnalyticsPort = NoopAnalyticsTracker,
) : MviViewModel<BlockedUsersState, BlockedUsersIntent, BlockedUsersEffect>(BlockedUsersState()) {

    init {
        viewModelScope.launch {
            val owner = session.current.first()?.accountId
            update { it.copy(myAccountId = owner) }
            if (owner == null) {
                update { it.copy(loading = false) }
                return@launch
            }
            blocked.observeBlocked(owner).collect { ids ->
                update { it.copy(loading = false, blockedIds = ids.toList()) }
            }
        }
        viewModelScope.launch {
            state
                .map { it.blockedIds.toSet() }
                .distinctUntilChanged()
                .flatMapLatest { ids -> accountRead.observeMany(ids) }
                .collect { identities -> update { it.copy(identities = identities) } }
        }
    }

    override suspend fun handle(intent: BlockedUsersIntent) = when (intent) {
        is BlockedUsersIntent.Unblock -> doUnblock(intent.accountId)
        BlockedUsersIntent.DismissError -> update { it.copy(error = null) }
        BlockedUsersIntent.DismissUnblockSuccess -> update { it.copy(unblockSuccess = false) }
    }

    private suspend fun doUnblock(target: AccountId) {
        val owner = currentState.myAccountId ?: return
        update { it.copy(unblockingIds = it.unblockingIds + target, error = null) }
        when (val r = blocked.unblock(owner, target)) {
            // On success the live block-list flow re-emits without `target`; `blockedIds` is the
            // single source of truth for the list, so no local removal here.
            is Result.Ok -> update { it.copy(unblockingIds = it.unblockingIds - target, unblockSuccess = true) }
            is Result.Err -> update {
                it.copy(unblockingIds = it.unblockingIds - target, error = r.error)
            }
        }
    }
}
