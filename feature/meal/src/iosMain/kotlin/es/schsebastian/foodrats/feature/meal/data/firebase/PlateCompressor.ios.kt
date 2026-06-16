package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

/**
 * iOS plate compressor: decode `UIImage` → downscale to [PlateCompression.scaledSize] →
 * re-encode via `UIImageJPEGRepresentation` at [PlateCompression.JPEG_QUALITY] / 100.
 *
 * Best-effort: returns the original bytes when the source can't be decoded, already fits, or the
 * re-encode wouldn't shrink it — a compression failure must never block a publish.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual class PlateCompressor actual constructor() {

    actual fun compress(source: ByteArray): ByteArray = runCatching {
        if (source.isEmpty()) return source
        // The K/N binding types UIImage(data:) as non-null; an undecodable image yields a
        // zero-size UIImage, which the dimension guard below treats as "leave it alone".
        val image = UIImage(data = source.toNSData())
        val srcW = image.size.useContents { width }.toInt()
        val srcH = image.size.useContents { height }.toInt()
        if (srcW <= 0 || srcH <= 0) return source

        val target = PlateCompression.scaledSize(srcW, srcH)
        val quality = PlateCompression.JPEG_QUALITY / 100.0

        val rendered = if (target.width == srcW && target.height == srcH) {
            image
        } else {
            UIGraphicsBeginImageContextWithOptions(
                size = CGSizeMake(target.width.toDouble(), target.height.toDouble()),
                opaque = true,
                scale = 1.0,
            )
            image.drawInRect(CGRectMake(0.0, 0.0, target.width.toDouble(), target.height.toDouble()))
            val scaled = UIGraphicsGetImageFromCurrentImageContext()
            UIGraphicsEndImageContext()
            scaled ?: image
        }

        val jpeg = UIImageJPEGRepresentation(rendered, quality) ?: return source
        val bytes = jpeg.toByteArray()
        if (bytes.isNotEmpty() && bytes.size < source.size) bytes else source
    }.getOrElse { t ->
        FrLog.w("PlateCompress", t) { "compress failed, uploading original: ${t.message}" }
        source
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}
