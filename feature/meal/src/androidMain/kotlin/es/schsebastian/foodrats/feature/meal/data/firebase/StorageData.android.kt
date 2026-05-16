package es.schsebastian.foodrats.feature.meal.data.firebase

import dev.gitlive.firebase.storage.Data

// On Android, Data wraps a ByteArray in a dev.gitlive.firebase.storage.Data value class.
actual fun ByteArray.toStorageData(): Data = Data(this)
