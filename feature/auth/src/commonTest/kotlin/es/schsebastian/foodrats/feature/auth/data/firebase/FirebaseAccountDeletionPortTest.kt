package es.schsebastian.foodrats.feature.auth.data.firebase

import es.schsebastian.foodrats.core.domain.account.AccountDeletionError
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks the `HttpsError` code → [AccountDeletionError] mapping done by
 * [toAccountDeletionError] (the string-match over [Throwable.message] used by
 * [FirebaseAccountDeletionPort] when the `deleteAccount` callable fails).
 *
 * Mirrors `AuthFaultTest`: the mapper is an `internal` top-level extension, so it is
 * exercised directly rather than through `requestDeletion`, which would construct a live
 * `Firebase.functions(region)`.
 */
class FirebaseAccountDeletionPortTest {

    @Test fun failed_precondition_maps_to_phrase_mismatch() {
        assertEquals(
            AccountDeletionError.Validation.PhraseMismatch,
            RuntimeException("failed-precondition").toAccountDeletionError(),
        )
    }

    @Test fun failed_precondition_underscore_variant_maps_to_phrase_mismatch() {
        assertEquals(
            AccountDeletionError.Validation.PhraseMismatch,
            RuntimeException("failed_precondition").toAccountDeletionError(),
        )
    }

    @Test fun aborted_maps_to_owner_reassign_failed() {
        assertEquals(
            AccountDeletionError.Deletion.OwnerReassignFailed,
            RuntimeException("aborted").toAccountDeletionError(),
        )
    }

    @Test fun unauthenticated_maps_to_unavailable() {
        assertEquals(
            AccountDeletionError.Backend.Unavailable,
            RuntimeException("unauthenticated").toAccountDeletionError(),
        )
    }

    @Test fun unexpected_message_maps_to_unavailable() {
        assertEquals(
            AccountDeletionError.Backend.Unavailable,
            RuntimeException("something unexpected").toAccountDeletionError(),
        )
    }

    @Test fun null_message_maps_to_unavailable() {
        assertEquals(
            AccountDeletionError.Backend.Unavailable,
            RuntimeException().toAccountDeletionError(),
        )
    }

    @Test fun matching_is_case_insensitive() {
        assertEquals(
            AccountDeletionError.Validation.PhraseMismatch,
            RuntimeException("FAILED-PRECONDITION: phrase did not match").toAccountDeletionError(),
        )
        assertEquals(
            AccountDeletionError.Deletion.OwnerReassignFailed,
            RuntimeException("ABORTED").toAccountDeletionError(),
        )
    }
}
