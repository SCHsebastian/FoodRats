package es.schsebastian.foodrats.feature.meal.presentation.capture

import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.meal.i18n.MealStringKey

data class CaptureMealState(
    val isCapturing: Boolean = false,
    /** Non-null when starting the draft / saving the photo failed; surfaced as a banner. */
    val error: MealStringKey? = null,
) : MviState

sealed interface CaptureMealIntent : MviIntent {
    data object Start : CaptureMealIntent
    data class PhotoTaken(val bytes: ByteArray) : CaptureMealIntent {
        override fun equals(other: Any?) = other is PhotoTaken && bytes.contentEquals(other.bytes)
        override fun hashCode() = bytes.contentHashCode()
        // Size-only: MviViewModel logs every intent via toString(); never render photo bytes as text.
        override fun toString() = "PhotoTaken(bytes=${bytes.size}B)"
    }
    data object OpenSettings : CaptureMealIntent
}

sealed interface CaptureMealEffect : MviEffect {
    data object NavigateToCompose : CaptureMealEffect
    data object OpenAppSettings : CaptureMealEffect
}
