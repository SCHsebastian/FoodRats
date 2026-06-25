package es.schsebastian.foodrats.feature.auth.data.firebase

import es.schsebastian.foodrats.core.domain.account.DataExportError
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks [toDataExportError] (M4): an `unauthenticated` server error must route to re-auth
 * ([DataExportError.Session.Unauthenticated]) rather than being conflated with a retryable
 * [DataExportError.Backend.Unavailable]. Mirrors `FirebaseAccountDeletionPortTest`.
 */
class FirebaseDataExportPortTest {

    private fun map(message: String) = Throwable(message).toDataExportError()

    @Test fun unauthenticated_maps_to_session_unauthenticated() {
        assertEquals(DataExportError.Session.Unauthenticated, map("UNAUTHENTICATED: token invalid"))
    }

    @Test fun internal_error_maps_to_backend_unavailable() {
        assertEquals(DataExportError.Backend.Unavailable, map("internal: archive failed"))
    }

    @Test fun unknown_error_maps_to_backend_unavailable() {
        assertEquals(DataExportError.Backend.Unavailable, map("something else"))
    }
}
