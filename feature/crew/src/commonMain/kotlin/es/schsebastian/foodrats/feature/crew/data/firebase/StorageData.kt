package es.schsebastian.foodrats.feature.crew.data.firebase

import dev.gitlive.firebase.storage.Data

/**
 * Bridges a [ByteArray] to GitLive's expect/actual `Data` type for Firebase Storage 2.1.0.
 * Android: typealias to `ByteArray`. iOS: wraps as `NSData`.
 */
internal expect fun ByteArray.toStorageData(): Data
