package es.schsebastian.foodrats.core.presentation.photopicker

import androidx.compose.runtime.Composable

/** Outcome of a native photo pick (camera capture or photo-library selection). */
sealed interface PhotoPickResult {
    /** Encoded image bytes, orientation-normalized (EXIF rotation already applied). */
    class Picked(val bytes: ByteArray) : PhotoPickResult

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
