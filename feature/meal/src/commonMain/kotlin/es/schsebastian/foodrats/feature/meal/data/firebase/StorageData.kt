package es.schsebastian.foodrats.feature.meal.data.firebase

import dev.gitlive.firebase.storage.Data

/**
 * Creates the platform-specific [Data] type required by GitLive Firebase Storage 2.1.0
 * from a [ByteArray]. Actual implementations:
 * - Android: `ByteArray` (typealias)
 * - iOS: `NSData` (via `byteArrayToNSData`)
 */
expect fun ByteArray.toStorageData(): Data
