package es.schsebastian.foodrats.feature.crew.data.firebase

import dev.gitlive.firebase.storage.Data

internal actual fun ByteArray.toStorageData(): Data = Data(this)
