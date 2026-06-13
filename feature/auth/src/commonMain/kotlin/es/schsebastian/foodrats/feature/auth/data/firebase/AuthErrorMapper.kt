package es.schsebastian.foodrats.feature.auth.data.firebase

import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError

/**
 * Maps a raw Firebase Auth throwable to a typed [AuthError] by first classifying it into
 * an [AuthFault] (the single message-inspection seam) and then matching on the fault
 * **type**. This mapper never inspects `t.message` directly — see [AuthFault].
 */
class AuthErrorMapper(private val crashReporter: CrashReporter) {
    fun mapFirebase(t: Throwable): AuthError = when (val fault = t.toAuthFault()) {
        AuthFault.EmailAlreadyInUse -> AuthError.EmailPassword.EmailAlreadyInUse
        AuthFault.WeakPassword      -> AuthError.EmailPassword.WeakPassword
        AuthFault.InvalidEmail      -> AuthError.EmailPassword.InvalidEmail
        AuthFault.WrongCredentials  -> AuthError.EmailPassword.WrongCredentials
        AuthFault.AccountDisabled   -> AuthError.Firebase.AccountDisabled
        AuthFault.TokenExpired      -> AuthError.Firebase.TokenExpired
        AuthFault.Network           -> AuthError.GoogleSignIn.NetworkUnavailable
        is AuthFault.Unknown -> {
            crashReporter.recordNonFatal(fault.cause, "auth-unmapped")
            AuthError.Firebase.Unavailable
        }
    }
}
