package es.schsebastian.foodrats.feature.meal.presentation.capture

import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState

data class CaptureMealState(
    val isCapturing: Boolean = false,
) : MviState

sealed interface CaptureMealIntent : MviIntent {
    data object Start : CaptureMealIntent
    data class PhotoTaken(val bytes: ByteArray) : CaptureMealIntent {
        override fun equals(other: Any?) = other is PhotoTaken && bytes.contentEquals(other.bytes)
        override fun hashCode() = bytes.contentHashCode()
    }
    data object OpenSettings : CaptureMealIntent
}

sealed interface CaptureMealEffect : MviEffect {
    data object NavigateToCompose : CaptureMealEffect
    data object OpenAppSettings : CaptureMealEffect
}
