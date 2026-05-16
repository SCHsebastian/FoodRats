package es.schsebastian.foodrats.auth

import android.content.Context
import es.schsebastian.foodrats.feature.auth.data.google.GoogleAuthClient

object GoogleAuthClientAndroidFactory {
    fun create(applicationContext: Context, serverClientId: String): GoogleAuthClient =
        GoogleAuthClient(
            contextProvider = { applicationContext },
            serverClientId = serverClientId,
        )
}
