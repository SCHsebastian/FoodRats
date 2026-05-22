package es.schsebastian.foodrats.feature.auth.presentation.profile

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.preferences.AppLocale
import es.schsebastian.foodrats.core.domain.preferences.LocalePort
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferencePort
import es.schsebastian.foodrats.core.domain.preferences.ThemeMode
import es.schsebastian.foodrats.core.domain.preferences.ThemeModePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.session.SignOutPort
import es.schsebastian.foodrats.core.i18n.StringKey
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.core.domain.notifications.NotificationPermissionPort
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.DeleteMyAccountUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.EnableNotificationsUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.SetLocaleUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.SetNotificationsEnabledUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.SetThemeModeUseCase
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

    // Preferences
    val themeMode: ThemeMode = ThemeMode.System,
    val themePickerOpen: Boolean = false,
    val themeError: StringKey? = null,
    val locale: AppLocale = AppLocale.System,
    val localePickerOpen: Boolean = false,
    val localeError: StringKey? = null,
    val notificationsEnabled: Boolean = true,
    val notificationsError: StringKey? = null,

    // Danger Zone — delete account
    val deleteScreenOpen: Boolean = false,
    val deleteConfirmation: String = "",
    val deleteDialogOpen: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val deleteError: StringKey? = null,
) : MviState

sealed interface ProfileIntent : MviIntent {
    data class DisplayNameChanged(val value: String) : ProfileIntent
    data object SaveDisplayName : ProfileIntent
    data class AvatarPicked(val bytes: ByteArray) : ProfileIntent
    data object SignOut : ProfileIntent

    data object ThemePickerOpen : ProfileIntent
    data object ThemePickerDismiss : ProfileIntent
    data class ThemeSelected(val mode: ThemeMode) : ProfileIntent

    data object LocalePickerOpen : ProfileIntent
    data object LocalePickerDismiss : ProfileIntent
    data class LocaleSelected(val locale: AppLocale) : ProfileIntent

    data class NotificationsToggled(val enabled: Boolean) : ProfileIntent
    data object OpenNotificationSystemSettings : ProfileIntent

    data object OpenDeleteAccount : ProfileIntent
    data object CloseDeleteAccount : ProfileIntent
    data class DeleteConfirmationChanged(val value: String) : ProfileIntent
    data object RequestDeleteDialog : ProfileIntent
    data object DeleteDialogDismiss : ProfileIntent
    data object DeleteDialogConfirm : ProfileIntent
}

sealed interface ProfileEffect : MviEffect

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    accountRead: AccountReadPort,
    session: SessionProvider,
    themePort: ThemeModePort,
    localePort: LocalePort,
    notificationsPort: NotificationsPreferencePort,
    private val updateDisplayName: UpdateMyDisplayNameUseCase,
    private val updateAvatar: UpdateMyAvatarUseCase,
    private val signOut: SignOutPort,
    private val setThemeMode: SetThemeModeUseCase,
    private val setLocale: SetLocaleUseCase,
    private val setNotificationsEnabled: SetNotificationsEnabledUseCase,
    private val enableNotifications: EnableNotificationsUseCase,
    private val notificationPermission: NotificationPermissionPort,
    private val deleteMyAccount: DeleteMyAccountUseCase,
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
        viewModelScope.launch {
            themePort.mode.onEach { mode -> update { it.copy(themeMode = mode) } }.collect {}
        }
        viewModelScope.launch {
            localePort.locale.onEach { loc -> update { it.copy(locale = loc) } }.collect {}
        }
        viewModelScope.launch {
            notificationsPort.enabled.onEach { on ->
                update { it.copy(notificationsEnabled = on) }
            }.collect {}
        }
    }

    override suspend fun handle(intent: ProfileIntent) {
        when (intent) {
            is ProfileIntent.DisplayNameChanged ->
                update { it.copy(editingDisplayName = intent.value, saveDisplayNameError = null) }

            ProfileIntent.SaveDisplayName -> doSaveDisplayName()
            is ProfileIntent.AvatarPicked -> doUploadAvatar(intent.bytes)
            ProfileIntent.SignOut -> doSignOut()

            ProfileIntent.ThemePickerOpen ->
                update { it.copy(themePickerOpen = true, themeError = null) }
            ProfileIntent.ThemePickerDismiss ->
                update { it.copy(themePickerOpen = false) }
            is ProfileIntent.ThemeSelected -> doSetTheme(intent.mode)

            ProfileIntent.LocalePickerOpen ->
                update { it.copy(localePickerOpen = true, localeError = null) }
            ProfileIntent.LocalePickerDismiss ->
                update { it.copy(localePickerOpen = false) }
            is ProfileIntent.LocaleSelected -> doSetLocale(intent.locale)

            is ProfileIntent.NotificationsToggled -> doSetNotifications(intent.enabled)
            ProfileIntent.OpenNotificationSystemSettings -> notificationPermission.openSystemSettings()

            ProfileIntent.OpenDeleteAccount ->
                update {
                    it.copy(
                        deleteScreenOpen = true,
                        deleteConfirmation = "",
                        deleteDialogOpen = false,
                        deleteError = null,
                    )
                }
            ProfileIntent.CloseDeleteAccount ->
                update {
                    it.copy(
                        deleteScreenOpen = false,
                        deleteConfirmation = "",
                        deleteDialogOpen = false,
                        deleteError = null,
                    )
                }
            is ProfileIntent.DeleteConfirmationChanged ->
                update { it.copy(deleteConfirmation = intent.value, deleteError = null) }
            ProfileIntent.RequestDeleteDialog ->
                update { it.copy(deleteDialogOpen = true) }
            ProfileIntent.DeleteDialogDismiss ->
                update { it.copy(deleteDialogOpen = false) }
            ProfileIntent.DeleteDialogConfirm -> doDeleteAccount()
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

    private suspend fun doSetTheme(mode: ThemeMode) {
        when (val r = setThemeMode(mode)) {
            is Result.Ok -> update { it.copy(themePickerOpen = false, themeError = null) }
            is Result.Err -> update { it.copy(themePickerOpen = false, themeError = r.error.toStringKey()) }
        }
    }

    private suspend fun doSetLocale(locale: AppLocale) {
        when (val r = setLocale(locale)) {
            is Result.Ok -> update { it.copy(localePickerOpen = false, localeError = null) }
            is Result.Err -> update { it.copy(localePickerOpen = false, localeError = r.error.toStringKey()) }
        }
    }

    private suspend fun doSetNotifications(enabled: Boolean) {
        // Optimistic: update state immediately, revert on error. Enable path goes through
        // the OS permission gate; disable just persists the pref.
        val prev = currentState.notificationsEnabled
        update { it.copy(notificationsEnabled = enabled, notificationsError = null) }
        val r = if (enabled) enableNotifications() else setNotificationsEnabled(false)
        when (r) {
            is Result.Ok  -> Unit
            is Result.Err -> update {
                it.copy(
                    // Roll the switch back to its pre-toggle position so the UI matches what
                    // we actually persisted (the enable use case sets the pref to false when
                    // the OS denies, so for the ON→Denied case `prev` is also false — but
                    // making the rollback explicit keeps the surface easy to read).
                    notificationsEnabled = if (enabled) false else prev,
                    notificationsError = r.error.toStringKey(),
                )
            }
        }
    }

    private suspend fun doDeleteAccount() {
        val expected = expectedDeletePhrase()
        update { it.copy(isDeletingAccount = true, deleteDialogOpen = false, deleteError = null) }
        val r = deleteMyAccount(currentState.deleteConfirmation, expected)
        update {
            when (r) {
                is Result.Ok -> it.copy(
                    isDeletingAccount = false,
                    deleteScreenOpen = false,
                    deleteConfirmation = "",
                    deleteError = null,
                )
                is Result.Err -> it.copy(
                    isDeletingAccount = false,
                    deleteError = r.error.toStringKey(),
                )
            }
        }
    }

    /**
     * Phrase the user must type to confirm deletion. Built from the current
     * display name; falls back to "DELETE" if no account is loaded yet (the UI
     * disables the field in that case).
     */
    fun expectedDeletePhrase(): String {
        val name = currentState.account?.displayName?.trim().orEmpty()
        return if (name.isEmpty()) DELETE_VERB else "$DELETE_VERB $name"
    }

    companion object {
        /**
         * The English imperative verb used in the confirmation phrase. The
         * Spanish locale builds "BORRAR <name>" via the same template
         * resource (the localized template lives in strings.xml); we keep the
         * source-of-truth comparison in English for now to avoid a circular
         * dependency on the resolver in the VM layer.
         */
        const val DELETE_VERB = "DELETE"
    }
}
