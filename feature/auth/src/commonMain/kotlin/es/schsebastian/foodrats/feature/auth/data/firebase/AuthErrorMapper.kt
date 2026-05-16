package es.schsebastian.foodrats.feature.auth.data.firebase

import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError

class AuthErrorMapper(private val crashReporter: CrashReporter) {
    fun mapFirebase(t: Throwable): AuthError {
        val m = t.message.orEmpty().lowercase()
        return when {
            "network" in m       -> AuthError.GoogleSignIn.NetworkUnavailable
            "disabled" in m      -> AuthError.Firebase.AccountDisabled
            "token" in m         -> AuthError.Firebase.TokenExpired
            else -> { crashReporter.recordNonFatal(t, "auth-unmapped"); AuthError.Firebase.Unavailable }
        }
    }
}
