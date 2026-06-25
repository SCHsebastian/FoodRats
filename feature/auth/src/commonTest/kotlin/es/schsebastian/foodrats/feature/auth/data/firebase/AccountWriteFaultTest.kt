package es.schsebastian.foodrats.feature.auth.data.firebase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Locks the single message-inspection seam ([toAccountWriteFault]) that classifies raw
 * Firestore/Storage throwables — the mirror of `AuthFaultTest`. A wording change in the SDK that
 * breaks classification fails HERE, not silently in production by mislabelling a permission/session
 * failure as a retryable backend outage.
 */
class AccountWriteFaultTest {

    private fun fault(message: String) = Throwable(message).toAccountWriteFault()

    @Test fun permission_denied_codes_classify() {
        assertEquals(AccountWriteFault.PermissionDenied, fault("PERMISSION_DENIED: Missing or insufficient permissions"))
        assertEquals(AccountWriteFault.PermissionDenied, fault("permission-denied"))
    }

    @Test fun unauthenticated_and_token_codes_classify() {
        assertEquals(AccountWriteFault.Unauthenticated, fault("UNAUTHENTICATED"))
        assertEquals(AccountWriteFault.Unauthenticated, fault("auth token is expired"))
    }

    @Test fun connectivity_codes_classify_as_network() {
        assertEquals(AccountWriteFault.Network, fault("UNAVAILABLE: failed to connect"))
        assertEquals(AccountWriteFault.Network, fault("network error"))
        assertEquals(AccountWriteFault.Network, fault("deadline exceeded"))
    }

    @Test fun unrecognized_message_is_unknown() {
        assertIs<AccountWriteFault.Unknown>(fault("some brand new error"))
    }

    @Test fun permission_is_checked_before_network_so_it_does_not_fall_through() {
        // A message that mentions both must classify as the more specific (permission) fault.
        assertEquals(AccountWriteFault.PermissionDenied, fault("permission-denied while offline"))
    }
}
