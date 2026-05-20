package es.schsebastian.foodrats.feature.auth.data.firebase

import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError

class AuthErrorMapper(private val crashReporter: CrashReporter) {
    /**
     * Firebase Auth on Android / iOS throws platform-specific exceptions whose message text
     * carries the actual auth-error code (GitLive doesn't expose typed cross-platform
     * exceptions consistently). String matching is the pragmatic lowest common denominator.
     */
    fun mapFirebase(t: Throwable): AuthError {
        val m = t.message.orEmpty().lowercase()
        return when {
            // Email/password specific — check these first so they don't fall through to generic "network".
            "email-already-in-use" in m || "email already in use" in m || "emailalreadyinuse" in m ->
                AuthError.EmailPassword.EmailAlreadyInUse
            "weak-password" in m || "weak password" in m || "weakpassword" in m ->
                AuthError.EmailPassword.WeakPassword
            "invalid-email" in m || "badly formatted" in m || "malformed" in m ->
                AuthError.EmailPassword.InvalidEmail
            // Firebase deliberately conflates wrong password + unknown user into one code
            // (INVALID_LOGIN_CREDENTIALS) since 2024 — we surface a single error too.
            "invalid_login_credentials" in m || "invalid-login-credentials" in m ||
                "wrong-password" in m || "user-not-found" in m || "incorrect" in m ->
                AuthError.EmailPassword.WrongCredentials
            "disabled" in m       -> AuthError.Firebase.AccountDisabled
            "token" in m          -> AuthError.Firebase.TokenExpired
            "network" in m        -> AuthError.GoogleSignIn.NetworkUnavailable
            else -> { crashReporter.recordNonFatal(t, "auth-unmapped"); AuthError.Firebase.Unavailable }
        }
    }
}
