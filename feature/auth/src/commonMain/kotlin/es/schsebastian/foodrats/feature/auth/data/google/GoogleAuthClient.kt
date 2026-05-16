package es.schsebastian.foodrats.feature.auth.data.google

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError

expect class GoogleAuthClient {
    suspend fun signIn(): Result<GoogleIdToken, AuthError.GoogleSignIn>
    suspend fun signOut()
}
