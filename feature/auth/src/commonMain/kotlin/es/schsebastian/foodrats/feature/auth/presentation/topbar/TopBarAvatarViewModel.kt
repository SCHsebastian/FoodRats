package es.schsebastian.foodrats.feature.auth.presentation.topbar

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class TopBarAvatarState(
    val avatarUrl: String? = null,
    val initials: String = "?",
) : MviState

sealed interface TopBarAvatarIntent : MviIntent
sealed interface TopBarAvatarEffect : MviEffect

/**
 * Lightweight VM that drives the leading avatar in `MainScaffold`'s top app bar.
 * Live-subscribes to the canonical [AccountReadPort] so renames/avatar uploads from
 * [es.schsebastian.foodrats.feature.auth.presentation.profile.ProfileScreen] reflect in
 * the bar immediately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TopBarAvatarViewModel(
    accountRead: AccountReadPort,
    session: SessionProvider,
) : MviViewModel<TopBarAvatarState, TopBarAvatarIntent, TopBarAvatarEffect>(TopBarAvatarState()) {

    init {
        viewModelScope.launch {
            session.current
                .filterNotNull()
                .map { it.accountId }
                .distinctUntilChanged()
                .flatMapLatest { id -> accountRead.observe(id).filterNotNull() }
                .onEach { account ->
                    update {
                        it.copy(
                            avatarUrl = account.avatarUrl,
                            initials = account.displayName.take(2).uppercase().ifBlank { "?" },
                        )
                    }
                }
                .collect {}
        }
    }

    override suspend fun handle(intent: TopBarAvatarIntent) = Unit
}
