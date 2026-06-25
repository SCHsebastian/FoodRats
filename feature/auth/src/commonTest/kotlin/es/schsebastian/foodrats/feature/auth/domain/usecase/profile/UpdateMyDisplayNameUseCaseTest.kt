package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.account.AccountWriteError
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.testdoubles.FakeAccountWritePort
import es.schsebastian.foodrats.feature.auth.testdoubles.FakeConnectivityPort
import es.schsebastian.foodrats.feature.auth.testdoubles.FixedSessionProvider
import es.schsebastian.foodrats.feature.auth.testdoubles.RecordingOutboxPort
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateMyDisplayNameUseCaseTest {
    private val accountId = (AccountId.of("u1") as Result.Ok).value
    private val crewId = (CrewId.of("c1") as Result.Ok).value
    private val signedIn = Session(accountId, crewId)

    @Test
    fun rejects_blank_name() = runTest {
        val r = build(signedIn).uc.invoke("   ")
        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Validation.DisplayNameBlank, r.error)
    }

    @Test
    fun rejects_too_long_name() = runTest {
        val r = build(signedIn).uc.invoke("x".repeat(41))
        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Validation.DisplayNameTooLong, r.error)
    }

    @Test
    fun signed_out_returns_session_error() = runTest {
        val r = build(session = null).uc.invoke("Alice")
        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Session.SignedOut, r.error)
    }

    @Test
    fun success_writes_canonical_account() = runTest {
        val f = build(signedIn)
        val r = f.uc.invoke("Alice")
        assertTrue(r is Result.Ok)
        assertEquals(listOf(accountId to "Alice"), f.acc.displayNameCalls)
        assertTrue(f.outbox.enqueued.isEmpty())
    }

    @Test
    fun offline_enqueues_without_writing() = runTest {
        val f = build(signedIn, online = false)
        val r = f.uc.invoke("Alice")
        assertTrue(r is Result.Ok)
        assertTrue(f.acc.displayNameCalls.isEmpty())
        assertEquals(1, f.outbox.enqueued.size)
        val cmd = f.outbox.enqueued.single()
        assertTrue(cmd is PendingCommand.SetDisplayName)
        assertEquals(accountId, cmd.accountId)
        assertEquals("Alice", cmd.displayName.value)
    }

    @Test
    fun online_backend_failure_falls_back_to_enqueue() = runTest {
        val f = build(signedIn)
        f.acc.nextDisplayNameError = AccountWriteError.Backend.Unavailable
        val r = f.uc.invoke("Alice")
        // Offline-first: a connectivity-class failure queues and succeeds (replayed on reconnect).
        assertTrue(r is Result.Ok)
        assertEquals(1, f.outbox.enqueued.size)
        assertTrue(f.outbox.enqueued.single() is PendingCommand.SetDisplayName)
    }

    @Test
    fun online_permission_denied_propagates_without_enqueue() = runTest {
        val f = build(signedIn)
        f.acc.nextDisplayNameError = AccountWriteError.Backend.PermissionDenied
        val r = f.uc.invoke("Alice")
        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Backend.Unavailable, r.error)
        assertTrue(f.outbox.enqueued.isEmpty())
    }

    private class Fixture(
        val uc: UpdateMyDisplayNameUseCase,
        val acc: FakeAccountWritePort,
        val outbox: RecordingOutboxPort,
    )

    private fun build(session: Session?, online: Boolean = true): Fixture {
        val acc = FakeAccountWritePort()
        val outbox = RecordingOutboxPort()
        return Fixture(
            UpdateMyDisplayNameUseCase(acc, FixedSessionProvider(session), FakeConnectivityPort(online), outbox),
            acc,
            outbox,
        )
    }
}
