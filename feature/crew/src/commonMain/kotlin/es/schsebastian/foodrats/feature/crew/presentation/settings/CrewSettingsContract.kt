package es.schsebastian.foodrats.feature.crew.presentation.settings

import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew

data class CrewSettingsState(
    val crew: Crew? = null,
    val isOwner: Boolean = false,
    val myAccountId: AccountId? = null,
    val editingCrewName: String = "",
    val isSavingCrewName: Boolean = false,
    val isLeaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isSavingBlindVoting: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    /**
     * Members whose owner-initiated removal is in flight. The row stays in the list (the live crew
     * flow drops it only once the write lands) but renders disabled + a spinner while present here.
     */
    val removingMemberIds: Set<AccountId> = emptySet(),
    val error: CrewError? = null,
    /**
     * Live identities resolved from `AccountReadPort.observeMany`. Keys are the current
     * crew's member ids; a `null` value means the account doc is missing or deleted.
     */
    val identities: Map<AccountId, Account?> = emptyMap(),
) : MviState

sealed interface CrewSettingsIntent : MviIntent {
    data class CrewNameChanged(val value: String) : CrewSettingsIntent
    data object SaveCrewName : CrewSettingsIntent
    data class ToggleBlindVoting(val enabled: Boolean) : CrewSettingsIntent
    data object SwitchCrew : CrewSettingsIntent
    data object Leave : CrewSettingsIntent
    data object RequestDelete : CrewSettingsIntent
    data object ConfirmDelete : CrewSettingsIntent
    data object CancelDelete : CrewSettingsIntent
    data class RemoveMemberConfirmed(val accountId: AccountId) : CrewSettingsIntent
    data object DismissError : CrewSettingsIntent
}

sealed interface CrewSettingsEffect : MviEffect {
    data object NavigateToCrewPicker : CrewSettingsEffect
    data object Left : CrewSettingsEffect
    data object Deleted : CrewSettingsEffect

    /**
     * A member was removed successfully — the screen shows a confirmation snackbar. Carries the
     * removed member's live [displayName] when known (`null` ⇒ a deleted/unresolved account, for
     * which the screen substitutes the localized deleted-user fallback) — i18n stays in the UI layer.
     */
    data class MemberRemoved(val displayName: String?) : CrewSettingsEffect
}
