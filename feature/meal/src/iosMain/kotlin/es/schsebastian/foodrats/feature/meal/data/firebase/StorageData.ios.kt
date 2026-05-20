package es.schsebastian.foodrats.feature.meal.data.firebase

import dev.gitlive.firebase.storage.Data
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create

// GitLive Firebase Storage 2.1.0 declares Data as `actual class Data(val data: NSData)`
// on iOS — a wrapper class, not a typealias for NSData. Constructing via `Data(nsData)`
// (instead of casting) is the supported bridge; the prior `nsData as Data` cast threw
// ClassCastException at runtime, which the publish error mapper labeled as
// PhotoUploadFailed → "No se ha podido subir la foto" with no underlying cause shown.
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun ByteArray.toStorageData(): Data {
    val nsData: NSData =
        if (isEmpty()) NSData()
        else usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
        }
    return Data(nsData)
}
