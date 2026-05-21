package es.schsebastian.foodrats.feature.meal.presentation.compose

import es.schsebastian.foodrats.core.domain.location.Coordinates
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.meal.domain.error.MealError

data class ComposePlateState(
    val dish: String = "",
    val descriptionInput: String = "",
    val descriptionTooLong: Boolean = false,
    val error: MealError? = null,
    val selectedSlot: MealSlot = MealSlot.Lunch,
    val takenSlots: Set<MealSlot> = emptySet(),
    val photoBytes: ByteArray? = null,
    val coordinates: Coordinates? = null,
    val locating: Boolean = false,
    val showConfirm: Boolean = false,
    val canContinue: Boolean = false,
) : MviState {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ComposePlateState) return false
        return dish == other.dish &&
            descriptionInput == other.descriptionInput &&
            descriptionTooLong == other.descriptionTooLong &&
            error == other.error &&
            selectedSlot == other.selectedSlot &&
            takenSlots == other.takenSlots &&
            coordinates == other.coordinates &&
            locating == other.locating &&
            showConfirm == other.showConfirm &&
            canContinue == other.canContinue &&
            (photoBytes?.contentEquals(other.photoBytes) ?: (other.photoBytes == null))
    }

    override fun hashCode(): Int {
        var result = dish.hashCode()
        result = 31 * result + descriptionInput.hashCode()
        result = 31 * result + descriptionTooLong.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + selectedSlot.hashCode()
        result = 31 * result + takenSlots.hashCode()
        result = 31 * result + (coordinates?.hashCode() ?: 0)
        result = 31 * result + locating.hashCode()
        result = 31 * result + showConfirm.hashCode()
        result = 31 * result + canContinue.hashCode()
        result = 31 * result + (photoBytes?.contentHashCode() ?: 0)
        return result
    }
}

sealed interface ComposePlateIntent : MviIntent {
    data class DishChanged(val value: String) : ComposePlateIntent
    data class DescriptionChanged(val value: String) : ComposePlateIntent
    data class SelectSlot(val slot: MealSlot) : ComposePlateIntent
    data object RequestLocation : ComposePlateIntent
    data object ClearLocation : ComposePlateIntent
    data object RequestConfirm : ComposePlateIntent
    data object DismissConfirm : ComposePlateIntent
    data object ConfirmPublish : ComposePlateIntent
}

sealed interface ComposePlateEffect : MviEffect {
    /**
     * The upload has been enqueued on the background coordinator. The
     * presentation layer should navigate back to the feed; the upload runs
     * out-of-band and surfaces via `MealUploadProgressPort`.
     */
    data object UploadEnqueued : ComposePlateEffect
}
