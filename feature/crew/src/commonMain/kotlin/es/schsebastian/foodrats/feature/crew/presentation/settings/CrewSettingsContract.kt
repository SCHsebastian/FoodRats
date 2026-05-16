package es.schsebastian.foodrats.feature.crew.presentation.settings

import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew

data class CrewSettingsState(
    val crew: Crew? = null,
    val isLeaving: Boolean = false,
    val error: CrewError? = null,
) : MviState

sealed interface CrewSettingsIntent : MviIntent {
    data object Leave : CrewSettingsIntent
    data object DismissError : CrewSettingsIntent
}

sealed interface CrewSettingsEffect : MviEffect {
    data object Left : CrewSettingsEffect
}
