package es.schsebastian.foodrats.feature.crew.data.firebase

import dev.gitlive.firebase.storage.Data
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create

// GitLive Firebase Storage 2.1.0 declares Data as `actual class Data(val data: NSData)` on iOS.
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun ByteArray.toStorageData(): Data {
    val nsData: NSData =
        if (isEmpty()) NSData()
        else usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
        }
    return Data(nsData)
}
