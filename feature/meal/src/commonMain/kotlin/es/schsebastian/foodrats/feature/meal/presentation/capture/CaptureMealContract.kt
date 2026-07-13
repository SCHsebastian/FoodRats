package es.schsebastian.foodrats.feature.meal.presentation.capture

import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.core.presentation.photopicker.PhotoMetadata
import es.schsebastian.foodrats.core.presentation.photopicker.PhotoSource
import es.schsebastian.foodrats.core.presentation.photopicker.PickedPhoto
import es.schsebastian.foodrats.feature.meal.i18n.MealStringKey

data class CaptureMealState(
    val isCapturing: Boolean = false,
    /** Non-null when starting the draft / saving the photo failed; surfaced as a banner. */
    val error: MealStringKey? = null,
) : MviState

sealed interface CaptureMealIntent : MviIntent {
    data object Start : CaptureMealIntent

    /**
     * [source]/[metadata] mirror the picker result verbatim: [source] becomes the published
     * [es.schsebastian.foodrats.core.domain.meal.PlateSource] provenance marker; [metadata] (gallery
     * picks only) drives the best-effort EXIF slot/coordinates prefill. Both default so existing
     * camera-only call sites compile unchanged.
     */
    data class PhotoTaken(
        val bytes: ByteArray,
        val source: PhotoSource = PhotoSource.Camera,
        val metadata: PhotoMetadata? = null,
    ) : CaptureMealIntent {
        override fun equals(other: Any?) =
            other is PhotoTaken && bytes.contentEquals(other.bytes) && source == other.source
        override fun hashCode() = bytes.contentHashCode() * 31 + source.hashCode()
        // Size-only: MviViewModel logs every intent via toString(); never render photo bytes as text.
        override fun toString() = "PhotoTaken(bytes=${bytes.size}B, source=$source, metadata=$metadata)"
    }

    /**
     * A multi-select gallery pick (`PhotoPickResult.PickedMultiple`), handled as ONE atomic batch
     * append in a single `handle()` coroutine — unlike dispatching [PhotoTaken] once per photo,
     * which would race the `isCapturing` reentrancy guard and drop all but the first (see
     * [CaptureMealViewModel]). [photos] carries already-resized bytes (mirrors [PhotoTaken.bytes]),
     * in the order the user selected them; the handler trims to the remaining photo capacity and
     * applies EXIF prefill from the first photo in the batch that carries metadata (mirrors
     * [PhotoTaken]'s per-photo prefill, gated the same only-if-unset way).
     */
    data class PhotosTaken(val photos: List<PickedPhoto>) : CaptureMealIntent {
        // Size-only: MviViewModel logs every intent via toString(); never render photo bytes as text.
        override fun toString() = "PhotosTaken(photos=${photos.size})"
    }
    data object OpenSettings : CaptureMealIntent
}

sealed interface CaptureMealEffect : MviEffect {
    data object NavigateToCompose : CaptureMealEffect
    data object OpenAppSettings : CaptureMealEffect
}
