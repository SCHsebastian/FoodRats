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

/** Outcome of a native photo pick (camera capture or photo-library selection). */
sealed interface PhotoPickResult {
    /**
     * Encoded image bytes, orientation-normalized (EXIF rotation already applied). [metadata] is
     * `null` for [PhotoSource.Camera] picks and best-effort (possibly all-null fields) for
     * [PhotoSource.Gallery] picks.
     */
    class Picked(val bytes: ByteArray, val source: PhotoSource, val metadata: PhotoMetadata? = null) : PhotoPickResult {
        override fun toString(): String = "Picked(bytes=${bytes.size}b, source=$source, metadata=$metadata)"
    }

    /** The user dismissed the camera/picker without choosing a photo. */
    data object Cancelled : PhotoPickResult

    /** The platform picker failed (unreadable image, no camera, …). */
    class Failed(val message: String?) : PhotoPickResult
}

/** Handle for launching the platform-native camera or photo library. */
interface PhotoPicker {
    fun launchCamera()
    fun launchGallery()
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
