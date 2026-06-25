package es.schsebastian.foodrats.feature.moderation.data

import es.schsebastian.foodrats.feature.moderation.data.firebase.ModerationFault
import es.schsebastian.foodrats.feature.moderation.data.firebase.toModerationFault
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks the substring→[ModerationFault] mapping in [toModerationFault]. A Firebase SDK message
 * wording change would break exactly this test — intentional: the test is the tripwire so the
 * change is noticed before it reaches production.
 *
 * Testing [ModerationFault.Unavailable] (catch-all) doubles as the assertion that the function
 * doesn't throw on unknown messages.
 */
class ModerationFaultTest {

    @Test fun already_exists_substring_maps_to_AlreadyExists() {
        assertEquals(
            ModerationFault.AlreadyExists,
            RuntimeException("ALREADY_EXISTS: document already exists").toModerationFault(),
        )
    }

    @Test fun already_exists_phrase_maps_to_AlreadyExists() {
        assertEquals(
            ModerationFault.AlreadyExists,
            RuntimeException("already exists").toModerationFault(),
        )
    }

    @Test fun permission_denied_maps_to_PermissionDenied() {
        assertEquals(
            ModerationFault.PermissionDenied,
            RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions.").toModerationFault(),
        )
    }

    @Test fun permission_substring_maps_to_PermissionDenied() {
        assertEquals(
            ModerationFault.PermissionDenied,
            RuntimeException("permission error").toModerationFault(),
        )
    }

    @Test fun network_substring_maps_to_Network() {
        assertEquals(
            ModerationFault.Network,
            RuntimeException("network error").toModerationFault(),
        )
    }

    @Test fun unavailable_substring_maps_to_Network() {
        assertEquals(
            ModerationFault.Network,
            RuntimeException("UNAVAILABLE: failed to reach the server").toModerationFault(),
        )
    }

    @Test fun unknown_message_maps_to_Unavailable() {
        assertEquals(
            ModerationFault.Unavailable,
            RuntimeException("some unexpected error xyz").toModerationFault(),
        )
    }

    @Test fun null_message_maps_to_Unavailable() {
        assertEquals(
            ModerationFault.Unavailable,
            RuntimeException(null as String?).toModerationFault(),
        )
    }

    @Test fun already_exists_takes_precedence_over_permission_substring() {
        // A message containing both (e.g. "already_exists PERMISSION_DENIED") — already-exists wins
        // because it is checked first in the when block.
        assertEquals(
            ModerationFault.AlreadyExists,
            RuntimeException("already exists permission denied").toModerationFault(),
        )
    }
}
