package es.schsebastian.foodrats.feature.auth.data.firebase

import es.schsebastian.foodrats.core.domain.session.LocalDataEraser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks the revoked-session device scrub (security #3): a server-side revocation signs out via
 * [FirebaseAuthDataSource] — bypassing `AuthSignOutPort`, the voluntary funnel that wipes
 * account-scoped local data — so [RevokedSessionCleanup] must run the wipe itself, best-effort,
 * without ever letting a failure escape into the session flow.
 */
class RevokedSessionCleanupTest {

    private val calls = mutableListOf<String>()

    private fun sut(
        signOut: suspend () -> Unit = { calls += "signOut" },
        eraser: LocalDataEraser = LocalDataEraser { calls += "erase" },
    ) = RevokedSessionCleanup(signOut = signOut, localDataEraser = eraser)

    @Test
    fun signs_out_then_erases_local_account_data() = runTest {
        sut().endRevokedSession()
        // Order matters: sign out first so authStateChanged nulls the session before the
        // erase's DataStore writes re-trigger the session combine.
        assertEquals(listOf("signOut", "erase"), calls)
    }

    @Test
    fun erases_local_account_data_even_when_sign_out_throws() = runTest {
        sut(signOut = { calls += "signOut"; error("no credential provider") }).endRevokedSession()
        assertEquals(listOf("signOut", "erase"), calls)
    }

    @Test
    fun swallows_eraser_failure_so_the_session_flow_stays_alive() = runTest {
        // Must return normally — a throw here would escape into sessions()'s map and kill the
        // hot session flow (the original cold-start-hang class of bug).
        sut(eraser = LocalDataEraser { calls += "erase"; error("datastore io") }).endRevokedSession()
        assertEquals(listOf("signOut", "erase"), calls)
    }
}
