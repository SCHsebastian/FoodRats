package es.schsebastian.foodrats.feature.auth.data.google

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError

actual class GoogleAuthClient(
    private val contextProvider: () -> Context,
    private val serverClientId: String,
) {
    actual suspend fun signIn(): Result<GoogleIdToken, AuthError.GoogleSignIn> {
        val ctx = contextProvider()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setServerClientId(serverClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .build()
        return try {
            val response = CredentialManager.create(ctx).getCredential(ctx, request)
            val cred = GoogleIdTokenCredential.createFrom(response.credential.data)
            Result.success(GoogleIdToken(cred.idToken))
        } catch (t: Throwable) {
            Result.failure(when (t) {
                is GetCredentialCancellationException -> AuthError.GoogleSignIn.UserCancelled
                is NoCredentialException              -> AuthError.GoogleSignIn.NoGoogleAccountsOnDevice
                is GoogleIdTokenParsingException      -> AuthError.GoogleSignIn.UnknownClientFailure
                else                                   -> AuthError.GoogleSignIn.UnknownClientFailure
            })
        }
    }

    actual suspend fun signOut() {
        CredentialManager.create(contextProvider()).clearCredentialState(ClearCredentialStateRequest())
    }
}
