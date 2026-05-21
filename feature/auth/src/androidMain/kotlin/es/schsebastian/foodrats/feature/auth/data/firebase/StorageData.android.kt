package es.schsebastian.foodrats.feature.auth.data.firebase

import dev.gitlive.firebase.storage.Data

internal actual fun ByteArray.toStorageData(): Data = Data(this)
