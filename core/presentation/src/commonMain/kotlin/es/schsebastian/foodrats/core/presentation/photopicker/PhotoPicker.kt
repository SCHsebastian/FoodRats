package es.schsebastian.foodrats.core.presentation.photopicker

import androidx.compose.runtime.Composable

/** Which native surface produced a [PhotoPickResult.Picked] — camera capture or photo library. */
enum class PhotoSource { Camera, Gallery }

/**
 * Best-effort EXIF metadata read off the RAW picked bytes, before orientation normalization
 * re-encodes the image (re-encoding drops EXIF). Only populated for [PhotoSource.Gallery] picks —
 * a camera capture is live, so there's nothing to prefill from. Every field is nullable because
 * EXIF may be absent, partial, or redacted (the Android system photo picker strips GPS by design).
 *
 * No byte payloads here, so the default `toString` is fine (see the project's
 * never-render-photo-bytes-in-toString convention).
 */
class PhotoMetadata(
    val takenAtEpochMs: Long?,
    val latitude: Double?,
    val longitude: Double?,
)

/**
 * One photo picked from a multi-select gallery pick — see [PhotoPickResult.PickedMultiple]. Always
 * [PhotoSource.Gallery] in practice (multi-select is a gallery-only affordance; a camera capture
 * produces at most one photo).
 *
 * [equals]/[hashCode] compare [bytes] by CONTENT ([ByteArray.contentEquals]/[ByteArray.contentHashCode])
 * rather than the default `ByteArray` reference identity — a common Kotlin footgun otherwise, and
 * the reason [PhotoPickResult.PickedMultiple] (a `data class` over a `List<PickedPhoto>`) can be
 * meaningfully compared in tests. [toString] mirrors [PhotoPickResult.Picked]'s convention: size
 * only, never the raw payload (see the project's never-render-photo-bytes-in-toString convention).
 */
class PickedPhoto(val bytes: ByteArray, val source: PhotoSource, val metadata: PhotoMetadata? = null) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PickedPhoto) return false
        return bytes.contentEquals(other.bytes) && source == other.source && metadata == other.metadata
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + (metadata?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "PickedPhoto(bytes=${bytes.size}b, source=$source, metadata=$metadata)"
}

/** Outcome of a native photo pick (camera capture, single gallery selection, or multi-select). */
sealed interface PhotoPickResult {
    /**
     * Encoded image bytes, orientation-normalized (EXIF rotation already applied). [metadata] is
     * `null` for [PhotoSource.Camera] picks and best-effort (possibly all-null fields) for
     * [PhotoSource.Gallery] picks.
     */
    class Picked(val bytes: ByteArray, val source: PhotoSource, val metadata: PhotoMetadata? = null) : PhotoPickResult {
        override fun toString(): String = "Picked(bytes=${bytes.size}b, source=$source, metadata=$metadata)"
    }

    /**
     * Two or more photos selected from a multi-select gallery pick ([PhotoPicker.launchGallery]
     * with `maxItems > 1`), in the user's selection order. Convention: an empty selection is always
     * [Cancelled], never `PickedMultiple(emptyList())` — callers may treat a non-empty [photos] as
     * an invariant of this variant rather than a case to defend against.
     */
    data class PickedMultiple(val photos: List<PickedPhoto>) : PhotoPickResult

    /** The user dismissed the camera/picker without choosing a photo. */
    data object Cancelled : PhotoPickResult

    /** The platform picker failed (unreadable image, no camera, …). */
    class Failed(val message: String?) : PhotoPickResult
}

/** Handle for launching the platform-native camera or photo library. */
interface PhotoPicker {
    fun launchCamera()

    /** Equivalent to `launchGallery(maxItems = 1)` — kept for source compatibility. */
    fun launchGallery(): Unit = launchGallery(maxItems = 1)

    /**
     * Launches the platform gallery/photo-library picker, allowing up to [maxItems] selections.
     * `maxItems == 1` delivers a [PhotoPickResult.Picked] — identical to today's single-select
     * behavior. `maxItems > 1` delivers a [PhotoPickResult.PickedMultiple] with at most [maxItems]
     * photos, in the user's selection order. An empty selection is always
     * [PhotoPickResult.Cancelled], regardless of [maxItems].
     */
    fun launchGallery(maxItems: Int)
}

/**
 * Remembers a [PhotoPicker] backed by the platform's native pickers —
 * Android: `ActivityResultContracts.TakePicture` + `PickVisualMedia` (system Photo
 * Picker); iOS: `UIImagePickerController` (camera) + `PHPickerViewController` (library).
 *
 * Neither platform path requires a runtime permission from the app: the system camera
 * app owns the CAMERA permission on Android (we never declare it), and both photo
 * pickers are out-of-process. iOS camera capture prompts via `NSCameraUsageDescription`.
 *
 * [onResult] is invoked on the main thread; byte reading and orientation
 * normalization happen off the main thread inside the actual implementations.
 */
@Composable
expect fun rememberPhotoPicker(onResult: (PhotoPickResult) -> Unit): PhotoPicker
