package es.schsebastian.foodrats.feature.meal.presentation.compose

import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.meal.domain.error.MealError

data class ComposePlateState(
    val dish: String = "",
    val selectedTags: Set<String> = emptySet(),
    val error: MealError? = null,
    val selectedSlot: MealSlot = MealSlot.Lunch,
    val takenSlots: Set<MealSlot> = emptySet(),
    val photoBytes: ByteArray? = null,
) : MviState {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ComposePlateState) return false
        return dish == other.dish &&
            selectedTags == other.selectedTags &&
            error == other.error &&
            selectedSlot == other.selectedSlot &&
            takenSlots == other.takenSlots &&
            (photoBytes?.contentEquals(other.photoBytes) ?: (other.photoBytes == null))
    }

    override fun hashCode(): Int {
        var result = dish.hashCode()
        result = 31 * result + selectedTags.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + selectedSlot.hashCode()
        result = 31 * result + takenSlots.hashCode()
        result = 31 * result + (photoBytes?.contentHashCode() ?: 0)
        return result
    }
}

sealed interface ComposePlateIntent : MviIntent {
    data class DishChanged(val value: String) : ComposePlateIntent
    data class TagToggled(val tag: String) : ComposePlateIntent
    data class SelectSlot(val slot: MealSlot) : ComposePlateIntent
    data object Continue : ComposePlateIntent
}

sealed interface ComposePlateEffect : MviEffect { data object NavigateToPublish : ComposePlateEffect }
