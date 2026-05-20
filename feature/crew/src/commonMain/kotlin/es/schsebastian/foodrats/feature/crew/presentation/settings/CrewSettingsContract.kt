package es.schsebastian.foodrats.feature.crew.presentation.settings

import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew

data class CrewSettingsState(
    val crew: Crew? = null,
    val isOwner: Boolean = false,
    val editingCrewName: String = "",
    val editingMyDisplayName: String = "",
    val myAvatarUrl: String? = null,
    val isSavingCrewName: Boolean = false,
    val isSavingMyDisplayName: Boolean = false,
    val isUpdatingAvatar: Boolean = false,
    val isLeaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isSigningOut: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val error: CrewError? = null,
    val signOutError: SessionError? = null,
) : MviState

sealed interface CrewSettingsIntent : MviIntent {
    data class CrewNameChanged(val value: String) : CrewSettingsIntent
    data object SaveCrewName : CrewSettingsIntent
    data class MyDisplayNameChanged(val value: String) : CrewSettingsIntent
    data object SaveMyDisplayName : CrewSettingsIntent
    data class UpdateMyAvatar(val bytes: ByteArray) : CrewSettingsIntent {
        override fun equals(other: Any?): Boolean =
            this === other || (other is UpdateMyAvatar && bytes.contentEquals(other.bytes))
        override fun hashCode(): Int = bytes.contentHashCode()
    }
    data object SwitchCrew : CrewSettingsIntent
    data object Leave : CrewSettingsIntent
    data object SignOut : CrewSettingsIntent
    data object RequestDelete : CrewSettingsIntent
    data object ConfirmDelete : CrewSettingsIntent
    data object CancelDelete : CrewSettingsIntent
    data object DismissError : CrewSettingsIntent
}

sealed interface CrewSettingsEffect : MviEffect {
    data object NavigateToCrewPicker : CrewSettingsEffect
    data object Left : CrewSettingsEffect
    data object Deleted : CrewSettingsEffect
    data object SignedOut : CrewSettingsEffect
}
