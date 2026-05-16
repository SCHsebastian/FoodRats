package es.schsebastian.foodrats.feature.meal.data.firebase

import dev.gitlive.firebase.storage.Data

// On Android, Data is a typealias for ByteArray, so no conversion needed.
actual fun ByteArray.toStorageData(): Data = this
