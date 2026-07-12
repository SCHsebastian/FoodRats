@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package es.schsebastian.foodrats.core.presentation.photopicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImageOrientation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

/**
 * iOS photo picker: `UIImagePickerController` for camera capture,
 * `PHPickerViewController` for the photo library (out-of-process — no permission needed).
 *
 * JPEG re-encode (with EXIF-orientation baking) happens off the main thread; [onResult]
 * is always delivered on the main queue.
 */
@Composable
actual fun rememberPhotoPicker(onResult: (PhotoPickResult) -> Unit): PhotoPicker {
    val currentOnResult by rememberUpdatedState(onResult)
    return remember { IosPhotoPicker { result -> currentOnResult(result) } }
}

private class IosPhotoPicker(
    private val deliver: (PhotoPickResult) -> Unit,
) : PhotoPicker {

    // UIKit `delegate` properties are WEAK references — these strong fields keep the
    // delegates alive for the lifetime of the remembered picker, otherwise the
    // callbacks would silently never fire.
    private val cameraDelegate = CameraCaptureDelegate(deliver)
    private val galleryDelegate = GalleryPickDelegate(deliver)

    override fun launchCamera() {
        val camera = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        if (!UIImagePickerController.isSourceTypeAvailable(camera)) {
            deliver(PhotoPickResult.Failed("Camera not available"))
            return
        }
        val controller = UIImagePickerController()
        controller.sourceType = camera
        controller.delegate = cameraDelegate
        present(controller)
    }

    override fun launchGallery() {
        val configuration = PHPickerConfiguration().apply {
            selectionLimit = 1L
            filter = PHPickerFilter.imagesFilter
        }
        val controller = PHPickerViewController(configuration = configuration)
        controller.delegate = galleryDelegate
        present(controller)
    }

    private fun present(controller: UIViewController) {
        val presenter = topViewController()
        if (presenter == null) {
            deliver(PhotoPickResult.Failed("No presenting view controller"))
            return
        }
        presenter.presentViewController(controller, animated = true, completion = null)
    }

    private fun topViewController(): UIViewController? {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        return top
    }
}

private class CameraCaptureDelegate(
    private val deliverOnMain: (PhotoPickResult) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        if (image == null) {
            deliverOnMain(PhotoPickResult.Failed("No image captured"))
            return
        }
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0UL)) {
            val bytes = image.toNormalizedJpegBytes()
            dispatch_async(dispatch_get_main_queue()) {
                deliverOnMain(
                    if (bytes == null) PhotoPickResult.Failed("Unreadable image")
                    else PhotoPickResult.Picked(bytes),
                )
            }
        }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        deliverOnMain(PhotoPickResult.Cancelled)
    }
}

private class GalleryPickDelegate(
    private val deliverOnMain: (PhotoPickResult) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val picked = didFinishPicking.firstOrNull() as? PHPickerResult
        if (picked == null) {
            deliverOnMain(PhotoPickResult.Cancelled)
            return
        }
        picked.itemProvider.loadDataRepresentationForTypeIdentifier(
            typeIdentifier = "public.image",
        ) { data, error ->
            // Fires on a background queue — decode + JPEG re-encode here, hop to
            // the main queue only to deliver the result.
            val bytes = data?.let { nsData -> UIImage(data = nsData).toNormalizedJpegBytes() }
            dispatch_async(dispatch_get_main_queue()) {
                deliverOnMain(
                    if (bytes != null) PhotoPickResult.Picked(bytes)
                    else PhotoPickResult.Failed(error?.localizedDescription),
                )
            }
        }
    }
}

private const val JPEG_QUALITY = 0.92

/**
 * Bakes EXIF orientation into the pixels (redraw when not already `.Up`) and re-encodes
 * as JPEG. Returns `null` when the image is undecodable (zero-size — the K/N binding
 * types `UIImage(data:)` as non-null) or the JPEG encode fails.
 */
private fun UIImage.toNormalizedJpegBytes(): ByteArray? {
    val w = size.useContents { width }
    val h = size.useContents { height }
    if (w <= 0.0 || h <= 0.0) return null

    val normalized = if (imageOrientation == UIImageOrientation.UIImageOrientationUp) {
        this
    } else {
        UIGraphicsBeginImageContextWithOptions(size = size, opaque = false, scale = scale)
        drawInRect(CGRectMake(0.0, 0.0, w, h))
        val redrawn = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        redrawn ?: this
    }

    val jpeg = UIImageJPEGRepresentation(normalized, JPEG_QUALITY) ?: return null
    val bytes = jpeg.toByteArray()
    return if (bytes.isEmpty()) null else bytes
}

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}
