package es.schsebastian.foodrats.feature.meal.presentation.components

import es.schsebastian.foodrats.core.domain.location.Coordinates
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.PlateSource
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.presentation.photopicker.PhotoMetadata
import es.schsebastian.foodrats.core.presentation.photopicker.PhotoSource
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftCommand
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftUseCase
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Applies the EXIF-derived slot/coordinates suggestions to [draft] (already carrying the fresh
 * photo) via [updateDraft]. Both are gated on the corresponding field being unset, so a value the
 * user already chose is never clobbered. Every failure mode (missing metadata, an unparseable
 * timestamp, an out-of-range coordinate pair, a `saveDraft` error) is swallowed — this is advisory
 * prefill, never a blocking step.
 *
 * Shared between [es.schsebastian.foodrats.feature.meal.presentation.capture.CaptureMealViewModel]
 * (the first photo of a fresh draft) and
 * [es.schsebastian.foodrats.feature.meal.presentation.compose.ComposePlateViewModel] (photos added
 * from the compose screen's own picker) — the exact same rules apply at both entry points, so the
 * mapping table lives here once instead of being duplicated per call site.
 */
internal suspend fun applyExifPrefill(
    draft: MealDraft,
    metadata: PhotoMetadata?,
    zone: TimeZone,
    updateDraft: UpdateMealDraftUseCase,
) {
    if (metadata == null) return
    if (draft.slot == null) {
        metadata.takenAtEpochMs?.let { epochMs ->
            runCatching { Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone).hour }
                .getOrNull()
                ?.let { hour -> updateDraft(UpdateMealDraftCommand.SetSlot(MealSlot.forHour(hour))) }
        }
    }
    if (draft.coordinates == null) {
        val lat = metadata.latitude
        val lon = metadata.longitude
        if (lat != null && lon != null) {
            (Coordinates.of(lat, lon) as? Result.Ok)?.value?.let { coords ->
                updateDraft(UpdateMealDraftCommand.SetCoordinates(coords))
            }
        }
    }
}

/** Maps the picker's platform-agnostic [PhotoSource] to the domain [PlateSource] provenance marker. */
internal fun PhotoSource.toPlateSource(): PlateSource = when (this) {
    PhotoSource.Camera -> PlateSource.Camera
    PhotoSource.Gallery -> PlateSource.Gallery
}
