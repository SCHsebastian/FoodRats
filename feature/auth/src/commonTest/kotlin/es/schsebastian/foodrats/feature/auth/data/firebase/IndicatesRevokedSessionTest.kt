package es.schsebastian.foodrats.feature.auth.data.firebase

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks the revoked-vs-transient classification used by the session-expiry handling
 * ([FirebaseAuthDataSource.sessions] cold-start guard + [FirebaseAuthDataSource.revalidateSession]).
 * Getting this wrong in either direction is a bug: a false negative leaves a revoked user stuck on
 * authenticated screens / hanging on Splash; a false positive signs a valid user out on a network blip.
 */
class IndicatesRevokedSessionTest {

    private fun err(message: String): Throwable = RuntimeException(message)

    @Test
    fun permission_and_auth_revocation_messages_are_revoked() {
        val revoked = listOf(
            "PERMISSION_DENIED: Missing or insufficient permissions.",
            "permission-denied",
            "7 PERMISSION_DENIED: permission denied",
            "16 UNAUTHENTICATED: Request had invalid authentication credentials",
            "The user account has been disabled by an administrator. [ user-disabled ]",
            "There is no user record corresponding to this identifier. user-not-found",
            "The user's credential is no longer valid. user-token-expired",
            "The user's token has been revoked.",
            "invalid-user-token",
        )
        for (m in revoked) {
            assertTrue(err(m).indicatesRevokedSession(), "expected REVOKED for: $m")
        }
    }

    @Test
    fun transient_connectivity_messages_are_not_revoked() {
        val transient = listOf(
            "UNAVAILABLE: failed to connect to all addresses",
            "14 UNAVAILABLE: network is unreachable",
            "DEADLINE_EXCEEDED: deadline exceeded after 9.999s",
            "request timed out",
            "A network error (such as timeout, interrupted connection or unreachable host) has occurred.",
            "Failed to get document because the client is offline.",
        )
        for (m in transient) {
            assertFalse(err(m).indicatesRevokedSession(), "expected TRANSIENT (keep session) for: $m")
        }
    }

    @Test
    fun unknown_or_empty_messages_are_not_revoked() {
        // Default-safe: an unclassifiable failure must NOT sign the user out.
        assertFalse(err("").indicatesRevokedSession())
        assertFalse(err("something unexpected happened").indicatesRevokedSession())
        assertFalse(RuntimeException().indicatesRevokedSession())
    }

    @Test
    fun transient_wins_when_a_message_mentions_both() {
        // A network-flavoured message must never be read as revocation even if it also contains a
        // revocation-ish word — transient is checked first and short-circuits.
        assertFalse(
            err("network error while checking permission").indicatesRevokedSession(),
            "transient connectivity must win over an incidental permission mention",
        )
    }
}
