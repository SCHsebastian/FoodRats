package es.schsebastian.foodrats.feature.auth.data.firebase

import es.schsebastian.foodrats.core.domain.telemetry.NoopCrashReporter
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthErrorMapperTest {
    private val mapper = AuthErrorMapper(NoopCrashReporter)

    @Test fun email_already_in_use_maps() {
        assertEquals(
            AuthError.EmailPassword.EmailAlreadyInUse,
            mapper.mapFirebase(RuntimeException("email-already-in-use")),
        )
    }

    @Test fun weak_password_maps() {
        assertEquals(
            AuthError.EmailPassword.WeakPassword,
            mapper.mapFirebase(RuntimeException("weak-password")),
        )
    }

    @Test fun invalid_email_maps() {
        assertEquals(
            AuthError.EmailPassword.InvalidEmail,
            mapper.mapFirebase(RuntimeException("invalid-email")),
        )
    }

    @Test fun wrong_credentials_maps() {
        assertEquals(
            AuthError.EmailPassword.WrongCredentials,
            mapper.mapFirebase(RuntimeException("INVALID_LOGIN_CREDENTIALS")),
        )
    }

    @Test fun disabled_maps_to_account_disabled() {
        assertEquals(
            AuthError.Firebase.AccountDisabled,
            mapper.mapFirebase(RuntimeException("account has been disabled")),
        )
    }

    @Test fun token_maps_to_token_expired() {
        assertEquals(
            AuthError.Firebase.TokenExpired,
            mapper.mapFirebase(RuntimeException("token expired")),
        )
    }

    @Test fun network_maps_to_network_unavailable() {
        assertEquals(
            AuthError.GoogleSignIn.NetworkUnavailable,
            mapper.mapFirebase(RuntimeException("network error")),
        )
    }

    @Test fun unknown_maps_to_unavailable() {
        assertEquals(
            AuthError.Firebase.Unavailable,
            mapper.mapFirebase(RuntimeException("something unexpected")),
        )
    }
}
