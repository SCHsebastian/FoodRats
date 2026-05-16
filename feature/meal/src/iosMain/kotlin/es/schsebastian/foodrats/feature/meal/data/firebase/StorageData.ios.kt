package es.schsebastian.foodrats.feature.meal.data.firebase

import dev.gitlive.firebase.storage.Data
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create

// On iOS, Data is a typealias for NSData.
@OptIn(ExperimentalForeignApi::class)
actual fun ByteArray.toStorageData(): Data {
    val nsData: NSData =
        if (isEmpty()) NSData()
        else usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
        }
    @Suppress("UNCHECKED_CAST")
    return nsData as Data
}
