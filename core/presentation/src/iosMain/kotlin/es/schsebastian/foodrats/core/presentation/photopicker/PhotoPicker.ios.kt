@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package es.schsebastian.foodrats.core.presentation.photopicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGRectMake
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSCalendar
import platform.Foundation.NSData
import platform.Foundation.NSDateComponents
import platform.Foundation.timeIntervalSince1970
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.kCGImagePropertyExifDateTimeOriginal
import platform.ImageIO.kCGImagePropertyExifDictionary
import platform.ImageIO.kCGImagePropertyGPSDictionary
import platform.ImageIO.kCGImagePropertyGPSLatitude
import platform.ImageIO.kCGImagePropertyGPSLatitudeRef
import platform.ImageIO.kCGImagePropertyGPSLongitude
import platform.ImageIO.kCGImagePropertyGPSLongitudeRef
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

    override fun launchGallery(maxItems: Int) {
        // PHPickerViewControllerDelegateProtocol.picker(...) gets no signal back about which
        // selectionLimit the configuration was launched with, so the delegate needs to be told
        // up front whether this is a single- or multi-select launch.
        galleryDelegate.wantsMultiple = maxItems > 1
        val configuration = PHPickerConfiguration().apply {
            selectionLimit = maxItems.toLong()
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
                    else PhotoPickResult.Picked(bytes, PhotoSource.Camera, null),
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

    /** Set by [IosPhotoPicker.launchGallery] right before presenting — see its call site KDoc. */
    var wantsMultiple: Boolean = false

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        if (results.isEmpty()) {
            deliverOnMain(PhotoPickResult.Cancelled)
            return
        }
        val multiSelect = wantsMultiple
        // Each item provider resolves asynchronously and completions can race in ANY order —
        // index-tag the slots so the assembled list preserves the user's original selection
        // order rather than first-to-finish order.
        val loaded = arrayOfNulls<PickedPhoto>(results.size)
        var remaining = results.size
        var lastError: String? = null
        results.forEachIndexed { index, result ->
            result.itemProvider.loadDataRepresentationForTypeIdentifier(
                typeIdentifier = "public.image",
            ) { data, error ->
                // Fires on a background queue — decode + JPEG re-encode here, hop to
                // the main queue only to touch shared state / deliver the result.
                val bytes = data?.let { nsData -> UIImage(data = nsData).toNormalizedJpegBytes() }
                // Metadata MUST be read from the raw NSData, before the JPEG re-encode above
                // bakes in orientation and drops EXIF entirely.
                val metadata = data?.let { readGalleryMetadata(it) }
                dispatch_async(dispatch_get_main_queue()) {
                    // All mutation of the shared `loaded`/`remaining`/`lastError` state happens
                    // inside main-queue blocks, which GCD serializes — safe despite each item's
                    // background-queue completion racing the others.
                    if (bytes != null) {
                        loaded[index] = PickedPhoto(bytes, PhotoSource.Gallery, metadata)
                    } else {
                        lastError = error?.localizedDescription
                    }
                    remaining -= 1
                    if (remaining == 0) {
                        val photos = loaded.filterNotNull()
                        deliverOnMain(
                            when {
                                // Every item failed to decode (single- or multi-select alike).
                                photos.isEmpty() -> PhotoPickResult.Failed(lastError)
                                !multiSelect -> PhotoPickResult.Picked(photos[0].bytes, photos[0].source, photos[0].metadata)
                                else -> PhotoPickResult.PickedMultiple(photos)
                            },
                        )
                    }
                }
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

/**
 * Best-effort EXIF read from the RAW (pre-re-encode) picked [data] via ImageIO: capture time
 * (device-zone epoch millis) + GPS lat/long, when present. Photos exported by third-party apps
 * or edited in Photos frequently strip GPS — that's expected, [PhotoMetadata]'s fields are
 * nullable for exactly this. Any failure (corrupt/absent EXIF, unexpected dictionary shape)
 * yields an all-null [PhotoMetadata] rather than failing the pick.
 *
 * Memory management: `NSData`/`CFDataRef` are toll-free bridged at the ObjC/CF runtime level, but
 * this Kotlin/Native binding types them as unrelated Kotlin types (an ObjC class vs a raw C struct
 * pointer) — a plain `as CFDataRef` cast can never succeed. [CFBridgingRetain] is the sanctioned
 * bridge instead: it hands back a retained generic CF pointer (a "Create Rule" return — we own it
 * and must [CFRelease] it) that [kotlinx.cinterop.reinterpret] narrows to `CFDataRef`.
 * `CGImageSourceCreateWithData` is likewise a CF "Create Rule" return needing its own [CFRelease].
 * `CGImageSourceCopyPropertiesAtIndex` is a CF "Copy Rule" return, but [CFBridgingRelease] transfers
 * that CF retain into Kotlin/ARC-managed memory in one step, so the bridged `Map` needs no manual
 * release.
 */
private fun readGalleryMetadata(data: NSData): PhotoMetadata {
    val noMetadata = PhotoMetadata(takenAtEpochMs = null, latitude = null, longitude = null)
    val retainedData = CFBridgingRetain(data) ?: return noMetadata
    try {
        val imageSource = try {
            CGImageSourceCreateWithData(retainedData.reinterpret(), null) ?: return noMetadata
        } catch (_: Throwable) {
            return noMetadata
        }
        try {
            val propertiesRef = CGImageSourceCopyPropertiesAtIndex(imageSource, 0uL, null)
            @Suppress("UNCHECKED_CAST")
            val properties = propertiesRef?.let { CFBridgingRelease(it) as? Map<Any?, *> }
            @Suppress("UNCHECKED_CAST")
            val exif = properties?.get(kCGImagePropertyExifDictionary) as? Map<Any?, *>
            @Suppress("UNCHECKED_CAST")
            val gps = properties?.get(kCGImagePropertyGPSDictionary) as? Map<Any?, *>

            val takenAtEpochMs = (exif?.get(kCGImagePropertyExifDateTimeOriginal) as? String)
                ?.let { raw -> parseExifDateTimeToEpochMs(raw) }

            val rawLatitude = gps?.get(kCGImagePropertyGPSLatitude) as? Double
            val rawLongitude = gps?.get(kCGImagePropertyGPSLongitude) as? Double
            val latitudeRef = gps?.get(kCGImagePropertyGPSLatitudeRef) as? String
            val longitudeRef = gps?.get(kCGImagePropertyGPSLongitudeRef) as? String
            val latitude = rawLatitude?.let { if (latitudeRef == "S") -it else it }
            val longitude = rawLongitude?.let { if (longitudeRef == "W") -it else it }

            return PhotoMetadata(takenAtEpochMs, latitude, longitude)
        } catch (_: Throwable) {
            return noMetadata
        } finally {
            CFRelease(imageSource)
        }
    } finally {
        CFRelease(retainedData)
    }
}

/**
 * Parses a fixed `"yyyy:MM:dd HH:mm:ss"` EXIF datetime (the TAG_DATETIME[_ORIGINAL] format) into
 * device-zone epoch millis, WITHOUT touching `NSTimeZone` directly (this Kotlin/Native Foundation
 * binding doesn't expose `NSTimeZone.systemTimeZone`/`localTimeZone`). [NSCalendar.currentCalendar]
 * already carries the system's current time zone, so building an [NSDateComponents] and resolving
 * it via [NSCalendar.dateFromComponents] interprets the parsed fields in the device zone for free.
 * Manual field-splitting (not [platform.Foundation.NSDateFormatter]) sidesteps locale/format
 * quirks entirely, since EXIF's datetime format is fixed, not locale-dependent. Returns `null` on
 * any malformed input.
 */
private fun parseExifDateTimeToEpochMs(raw: String): Long? {
    // "yyyy:MM:dd HH:mm:ss" — exactly 19 characters, colon-separated date + space + colon-separated time.
    if (raw.length != 19) return null
    val year = raw.substring(0, 4).toLongOrNull() ?: return null
    val month = raw.substring(5, 7).toLongOrNull() ?: return null
    val day = raw.substring(8, 10).toLongOrNull() ?: return null
    val hour = raw.substring(11, 13).toLongOrNull() ?: return null
    val minute = raw.substring(14, 16).toLongOrNull() ?: return null
    val second = raw.substring(17, 19).toLongOrNull() ?: return null

    val components = NSDateComponents().apply {
        this.year = year
        this.month = month
        this.day = day
        this.hour = hour
        this.minute = minute
        this.second = second
    }
    val date = NSCalendar.currentCalendar.dateFromComponents(components) ?: return null
    return (date.timeIntervalSince1970 * 1000.0).toLong()
}

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}
