package es.schsebastian.foodrats.feature.meal.data.firebase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Locks the single message-inspection seam for the meal data layer. */
class FirebaseFaultTest {

    @Test fun already_exists_wins_over_everything() {
        assertEquals(FirebaseFault.AlreadyExists, RuntimeException("ALREADY_EXISTS").toFirebaseFault())
        assertEquals(FirebaseFault.AlreadyExists, RuntimeException("already-exists: dup").toFirebaseFault())
    }

    @Test fun unauthenticated_classifies() {
        assertEquals(FirebaseFault.Unauthenticated, RuntimeException("UNAUTHENTICATED").toFirebaseFault())
    }

    @Test fun permission_denied_classifies_for_all_shapes() {
        assertEquals(FirebaseFault.PermissionDenied, RuntimeException("PERMISSION_DENIED").toFirebaseFault())
        assertEquals(FirebaseFault.PermissionDenied, RuntimeException("permission-denied").toFirebaseFault())
        assertEquals(FirebaseFault.PermissionDenied, RuntimeException("Missing or insufficient permissions").toFirebaseFault())
    }

    @Test fun storage_classifies() {
        assertEquals(FirebaseFault.StorageFailure, RuntimeException("Storage upload error").toFirebaseFault())
    }

    @Test fun unavailable_classifies() {
        assertEquals(FirebaseFault.Unavailable, RuntimeException("UNAVAILABLE").toFirebaseFault())
        assertEquals(FirebaseFault.Unavailable, RuntimeException("Host unreachable").toFirebaseFault())
        assertEquals(FirebaseFault.Unavailable, RuntimeException("no route to host").toFirebaseFault())
        assertEquals(FirebaseFault.Unavailable, RuntimeException("network down").toFirebaseFault())
    }

    @Test fun not_found_classifies() {
        assertEquals(FirebaseFault.NotFound, RuntimeException("NOT-FOUND").toFirebaseFault())
        assertEquals(FirebaseFault.NotFound, RuntimeException("document not found").toFirebaseFault())
    }

    @Test fun unrecognized_is_unknown_carrying_cause() {
        val t = RuntimeException("totally novel failure")
        val fault = t.toFirebaseFault()
        assertTrue(fault is FirebaseFault.Unknown)
        assertEquals(t, fault.cause)
    }
}
