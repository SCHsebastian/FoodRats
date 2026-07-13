package es.schsebastian.foodrats.feature.meal.presentation.compose

import es.schsebastian.foodrats.core.domain.crew.CrewSummary
import es.schsebastian.foodrats.core.domain.location.Coordinates
import es.schsebastian.foodrats.core.domain.meal.ClassifierError
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.MealPublishPolicy
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.core.presentation.photopicker.PickedPhoto
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.Plate

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
    /**
     * Ordered photos for this draft — a live mirror of `MealDraft.plates` (the draft is the single
     * source of truth; this list is never mutated locally). [Plate] already carries content-based
     * `equals`/`hashCode` and a size-only `toString`, so this list — and therefore this whole state
     * class — never needs a manual override to stay safe (no raw photo bytes ever reach a log line).
     */
    val photos: List<Plate> = emptyList(),
    /**
     * True while an [ComposePlateIntent.AddPhotos] batch is being persisted. Mirrors
     * [es.schsebastian.foodrats.feature.meal.presentation.capture.CaptureMealState.isCapturing]:
     * each intent runs in its own coroutine (`MviViewModel.onIntent`), so an overlapping
     * `AddPhotos` dispatched while a previous batch is still writing would interleave at the
     * `UpdateMealDraftUseCase` read-then-write suspension point and silently drop a photo.
     * [ComposePlateViewModel] drops re-entries while this is true.
     */
    val isAddingPhotos: Boolean = false,
    /**
     * UI-only: which [photos] entry the hero preview/gallery-marker/action-row currently target.
     * Not persisted to the draft. Clamped into `photos.indices` whenever the list shrinks (see
     * [ComposePlateViewModel]); defaults to the first photo.
     */
    val selectedIndex: Int = 0,
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

    /** The photo the hero preview / gallery-marker / action row currently target. `null` when empty. */
    val selectedPhoto: Plate? get() = photos.getOrNull(selectedIndex)

    /** The first photo — the classifier's fixed target (index 0), independent of [selectedIndex]. */
    val primaryPhoto: Plate? get() = photos.firstOrNull()

    /** True while [photos] is under [MealPublishPolicy.MAX_PHOTOS_PER_MEAL] — drives the add-photo tile. */
    val canAddMorePhotos: Boolean get() = photos.size < MealPublishPolicy.MAX_PHOTOS_PER_MEAL
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

    /**
     * Appends [photos] (in order) from the compose screen's own camera/gallery picker, trimmed to
     * whatever remains under [MealPublishPolicy.MAX_PHOTOS_PER_MEAL]. A gallery photo carrying EXIF
     * metadata prefills slot/coordinates the same way the first capture does (only-if-unset).
     */
    data class AddPhotos(val photos: List<PickedPhoto>) : ComposePlateIntent {
        // Size-only: MviViewModel logs every intent via toString(); never render photo bytes as text.
        override fun toString() = "AddPhotos(photos=${photos.size})"
    }

    /** The compose screen's own picker failed to return a photo (surfaces a retry-able banner). */
    data object PhotoPickFailed : ComposePlateIntent

    /** Removes the photo at [index]. Out-of-bounds is a no-op; selection moves to a sane neighbor. */
    data class RemovePhotoAt(val index: Int) : ComposePlateIntent
    /** Moves the photo at [fromIndex] to [toIndex]; selection follows the moved photo. */
    data class MovePhoto(val fromIndex: Int, val toIndex: Int) : ComposePlateIntent
    /** Targets [index] as the hero preview / action-row subject. Out-of-bounds is a no-op. */
    data class SelectPhoto(val index: Int) : ComposePlateIntent
}

sealed interface ComposePlateEffect : MviEffect {
    /**
     * The upload has been enqueued on the background coordinator. The
     * presentation layer should navigate back to the feed; the upload runs
     * out-of-band and surfaces via `MealUploadProgressPort`.
     */
    data object UploadEnqueued : ComposePlateEffect
}
