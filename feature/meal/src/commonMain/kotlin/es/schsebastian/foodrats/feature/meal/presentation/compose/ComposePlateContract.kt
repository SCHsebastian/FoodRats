package es.schsebastian.foodrats.feature.meal.presentation.compose

import es.schsebastian.foodrats.core.domain.crew.CrewSummary
import es.schsebastian.foodrats.core.domain.location.Coordinates
import es.schsebastian.foodrats.core.domain.meal.ClassifierError
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.meal.domain.error.MealError

data class ComposePlateState(
    val dish: String = "",
    /** True when the dish title exceeds `DishName.MAX_LEN`; gates `canContinue` and lights the counter. */
    val dishTooLong: Boolean = false,
    val descriptionInput: String = "",
    val descriptionTooLong: Boolean = false,
    /**
     * UGC compliance §3 HARD-BLOCK: the on-device text filter flagged the description. Gates
     * `canContinue`/publish — an objectionable description cannot be published.
     */
    val descriptionWarning: Boolean = false,
    /**
     * UGC compliance §3 HARD-BLOCK: the on-device text filter flagged the dish title. Gates
     * `canContinue`/publish — an objectionable dish title cannot be published.
     */
    val dishWarning: Boolean = false,
    val error: MealError? = null,
    /** Optional "meal moment" label — `null` means none chosen (the user may skip it). */
    val selectedSlot: MealSlot? = null,
    /** True when every selected crew is at the daily cap; gates `canContinue` and shows a banner. */
    val dailyLimitReached: Boolean = false,
    val photoBytes: ByteArray? = null,
    val coordinates: Coordinates? = null,
    val locating: Boolean = false,
    val showConfirm: Boolean = false,
    val canContinue: Boolean = false,
    // On-device classification is advisory: it never gates `canContinue`.
    val classifying: Boolean = false,
    val draftIngredients: List<IngredientSlug> = emptyList(),
    val detectedIngredients: List<IngredientSlug> = emptyList(),
    val classifierError: ClassifierError? = null,
    // Publish audience. `availableCrews` is every crew the author belongs to; the picker
    // is only shown when there's more than one (otherwise the single crew is implicit).
    // `selectedCrewIds` is the chosen subset the plate fans out to — defaults to all.
    val availableCrews: List<CrewSummary> = emptyList(),
    val selectedCrewIds: Set<CrewId> = emptySet(),
) : MviState {
    /** True when the publish audience is shown as a picker (more than one crew to choose from). */
    val showCrewPicker: Boolean get() = availableCrews.size > 1

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ComposePlateState) return false
        return dish == other.dish &&
            dishTooLong == other.dishTooLong &&
            descriptionInput == other.descriptionInput &&
            descriptionTooLong == other.descriptionTooLong &&
            descriptionWarning == other.descriptionWarning &&
            dishWarning == other.dishWarning &&
            error == other.error &&
            selectedSlot == other.selectedSlot &&
            dailyLimitReached == other.dailyLimitReached &&
            coordinates == other.coordinates &&
            locating == other.locating &&
            showConfirm == other.showConfirm &&
            canContinue == other.canContinue &&
            classifying == other.classifying &&
            draftIngredients == other.draftIngredients &&
            detectedIngredients == other.detectedIngredients &&
            classifierError == other.classifierError &&
            availableCrews == other.availableCrews &&
            selectedCrewIds == other.selectedCrewIds &&
            (photoBytes?.contentEquals(other.photoBytes) ?: (other.photoBytes == null))
    }

    override fun hashCode(): Int {
        var result = dish.hashCode()
        result = 31 * result + dishTooLong.hashCode()
        result = 31 * result + descriptionInput.hashCode()
        result = 31 * result + descriptionTooLong.hashCode()
        result = 31 * result + descriptionWarning.hashCode()
        result = 31 * result + dishWarning.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + (selectedSlot?.hashCode() ?: 0)
        result = 31 * result + dailyLimitReached.hashCode()
        result = 31 * result + (coordinates?.hashCode() ?: 0)
        result = 31 * result + locating.hashCode()
        result = 31 * result + showConfirm.hashCode()
        result = 31 * result + canContinue.hashCode()
        result = 31 * result + classifying.hashCode()
        result = 31 * result + draftIngredients.hashCode()
        result = 31 * result + detectedIngredients.hashCode()
        result = 31 * result + (classifierError?.hashCode() ?: 0)
        result = 31 * result + availableCrews.hashCode()
        result = 31 * result + selectedCrewIds.hashCode()
        result = 31 * result + (photoBytes?.contentHashCode() ?: 0)
        return result
    }

    // Size-only photoBytes: MviViewModel logs every state via toString(); never render
    // photo bytes as text (a multi-MB byte dump OOM-crashed the crew banner flow).
    override fun toString(): String =
        "ComposePlateState(dish=$dish, dishTooLong=$dishTooLong, descriptionInput=$descriptionInput, " +
            "descriptionTooLong=$descriptionTooLong, descriptionWarning=$descriptionWarning, " +
            "dishWarning=$dishWarning, error=$error, selectedSlot=$selectedSlot, " +
            "dailyLimitReached=$dailyLimitReached, photoBytes=${photoBytes?.size?.let { "${it}B" }}, " +
            "coordinates=$coordinates, locating=$locating, showConfirm=$showConfirm, " +
            "canContinue=$canContinue, classifying=$classifying, draftIngredients=$draftIngredients, " +
            "detectedIngredients=$detectedIngredients, classifierError=$classifierError, " +
            "availableCrews=$availableCrews, selectedCrewIds=$selectedCrewIds)"
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

    /** Adds/removes one crew from the publish audience (keeps at least one selected). */
    data class CrewToggled(val crewId: CrewId) : ComposePlateIntent
    /** Selects every crew the author belongs to (the "All" shortcut). */
    data object AllCrewsSelected : ComposePlateIntent
}

sealed interface ComposePlateEffect : MviEffect {
    /**
     * The upload has been enqueued on the background coordinator. The
     * presentation layer should navigate back to the feed; the upload runs
     * out-of-band and surfaces via `MealUploadProgressPort`.
     */
    data object UploadEnqueued : ComposePlateEffect
}
