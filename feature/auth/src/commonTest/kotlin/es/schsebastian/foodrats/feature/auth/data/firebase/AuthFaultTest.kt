package es.schsebastian.foodrats.feature.auth.data.firebase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Locks the single message-inspection seam for the auth data layer. */
class AuthFaultTest {

    @Test fun email_already_in_use_classifies_for_all_shapes() {
        assertEquals(AuthFault.EmailAlreadyInUse, RuntimeException("email-already-in-use").toAuthFault())
        assertEquals(AuthFault.EmailAlreadyInUse, RuntimeException("email already in use").toAuthFault())
        assertEquals(AuthFault.EmailAlreadyInUse, RuntimeException("emailAlreadyInUse").toAuthFault())
    }

    @Test fun weak_password_classifies() {
        assertEquals(AuthFault.WeakPassword, RuntimeException("weak-password").toAuthFault())
    }

    @Test fun invalid_email_classifies() {
        assertEquals(AuthFault.InvalidEmail, RuntimeException("invalid-email").toAuthFault())
        assertEquals(AuthFault.InvalidEmail, RuntimeException("The email address is badly formatted.").toAuthFault())
    }

    @Test fun wrong_credentials_conflates_all_shapes() {
        assertEquals(AuthFault.WrongCredentials, RuntimeException("INVALID_LOGIN_CREDENTIALS").toAuthFault())
        assertEquals(AuthFault.WrongCredentials, RuntimeException("wrong-password").toAuthFault())
        assertEquals(AuthFault.WrongCredentials, RuntimeException("user-not-found").toAuthFault())
    }

    @Test fun disabled_classifies() {
        assertEquals(AuthFault.AccountDisabled, RuntimeException("The user account has been disabled").toAuthFault())
    }

    @Test fun token_classifies() {
        assertEquals(AuthFault.TokenExpired, RuntimeException("token expired").toAuthFault())
    }

    @Test fun network_classifies() {
        assertEquals(AuthFault.Network, RuntimeException("A network error occurred").toAuthFault())
    }

    // Precedence: email/password-specific codes are checked before the generic
    // network/token buckets, so a "weak-password ... network" message stays WeakPassword.
    @Test fun specific_code_wins_over_generic_network() {
        assertEquals(AuthFault.WeakPassword, RuntimeException("weak-password (no network needed)").toAuthFault())
    }

    @Test fun unrecognized_is_unknown_carrying_cause() {
        val t = RuntimeException("totally novel auth failure")
        val fault = t.toAuthFault()
        assertTrue(fault is AuthFault.Unknown)
        assertEquals(t, fault.cause)
    }
}
