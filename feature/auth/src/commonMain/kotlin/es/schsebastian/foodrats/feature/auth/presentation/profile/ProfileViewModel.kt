package es.schsebastian.foodrats.feature.auth.presentation.profile

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.i18n.StringKey
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.session.SignOutPort
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.UpdateMyAvatarUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.UpdateMyDisplayNameUseCase
import es.schsebastian.foodrats.feature.auth.i18n.AuthStringKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ProfileState(
    val account: Account? = null,
    val editingDisplayName: String = "",
    val isSavingDisplayName: Boolean = false,
    val saveDisplayNameError: StringKey? = null,
    val isUploadingAvatar: Boolean = false,
    val uploadAvatarError: StringKey? = null,
    val isSigningOut: Boolean = false,
    val signOutError: StringKey? = null,
) : MviState

sealed interface ProfileIntent : MviIntent {
    data class DisplayNameChanged(val value: String) : ProfileIntent
    data object SaveDisplayName : ProfileIntent
    data class AvatarPicked(val bytes: ByteArray) : ProfileIntent
    data object SignOut : ProfileIntent
}

/** No effects today — sign-out routing is handled by the root NavGraph session observer. */
sealed interface ProfileEffect : MviEffect

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    accountRead: AccountReadPort,
    session: SessionProvider,
    private val updateDisplayName: UpdateMyDisplayNameUseCase,
    private val updateAvatar: UpdateMyAvatarUseCase,
    private val signOut: SignOutPort,
) : MviViewModel<ProfileState, ProfileIntent, ProfileEffect>(ProfileState()) {

    init {
        viewModelScope.launch {
            session.current
                .filterNotNull()
                .map { it.accountId }
                .distinctUntilChanged()
                .flatMapLatest { id -> accountRead.observe(id).filterNotNull() }
                .onEach { account ->
                    update { prev ->
                        prev.copy(
                            account = account,
                            // Seed the field on first emission; never overwrite an in-progress edit.
                            editingDisplayName = if (prev.editingDisplayName.isBlank()) account.displayName else prev.editingDisplayName,
                        )
                    }
                }
                .collect {}
        }
    }

    override suspend fun handle(intent: ProfileIntent) {
        when (intent) {
            is ProfileIntent.DisplayNameChanged ->
                update { it.copy(editingDisplayName = intent.value, saveDisplayNameError = null) }

            ProfileIntent.SaveDisplayName -> doSaveDisplayName()
            is ProfileIntent.AvatarPicked -> doUploadAvatar(intent.bytes)
            ProfileIntent.SignOut -> doSignOut()
        }
    }

    private suspend fun doSaveDisplayName() {
        val name = currentState.editingDisplayName
        update { it.copy(isSavingDisplayName = true, saveDisplayNameError = null) }
        val r = updateDisplayName(name)
        update {
            when (r) {
                is Result.Ok -> it.copy(isSavingDisplayName = false, saveDisplayNameError = null)
                is Result.Err -> it.copy(isSavingDisplayName = false, saveDisplayNameError = r.error.toStringKey())
            }
        }
    }

    private suspend fun doUploadAvatar(bytes: ByteArray) {
        update { it.copy(isUploadingAvatar = true, uploadAvatarError = null) }
        val r = updateAvatar(bytes)
        update {
            when (r) {
                is Result.Ok -> it.copy(isUploadingAvatar = false, uploadAvatarError = null)
                is Result.Err -> it.copy(isUploadingAvatar = false, uploadAvatarError = r.error.toStringKey())
            }
        }
    }

    private suspend fun doSignOut() {
        update { it.copy(isSigningOut = true, signOutError = null) }
        val r = signOut.signOut()
        update {
            when (r) {
                is Result.Ok -> it.copy(isSigningOut = false, signOutError = null)
                is Result.Err -> it.copy(isSigningOut = false, signOutError = AuthStringKey.ProfileSignOutFailed)
            }
        }
    }
}
