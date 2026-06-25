package es.schsebastian.foodrats.feature.crew.presentation.picker

import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew

data class CrewPickerState(
    val crews: List<Crew> = emptyList(),
    /** True until the first crews emission (or error) arrives — drives the initial-load skeleton. */
    val isLoading: Boolean = true,
    val isCreating: Boolean = false,
    val isJoining: Boolean = false,
    val createInput: String = "",
    val joinInput: String = "",
    val error: CrewError? = null,
    val showCreateForm: Boolean = false,
    val showJoinForm: Boolean = false,
) : MviState

sealed interface CrewPickerIntent : MviIntent {
    data object ToggleCreateForm : CrewPickerIntent
    data object ToggleJoinForm : CrewPickerIntent
    data class CreateInputChanged(val value: String) : CrewPickerIntent
    data class JoinInputChanged(val value: String) : CrewPickerIntent
    data object SubmitCreate : CrewPickerIntent
    data object SubmitJoin : CrewPickerIntent
    data class PickCrew(val crewId: CrewId) : CrewPickerIntent
    data object DismissError : CrewPickerIntent
}

sealed interface CrewPickerEffect : MviEffect {
    data class CrewSelected(val crewId: CrewId) : CrewPickerEffect
    /** A join request was filed — show a "waiting for owner approval" confirmation; no navigation. */
    data object JoinRequested : CrewPickerEffect
}
