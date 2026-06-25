package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.account.AccountWriteError
import es.schsebastian.foodrats.core.domain.account.Bio
import es.schsebastian.foodrats.core.domain.model.AccountId
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateMyBioUseCaseTest {

    private val accountId = (AccountId.of("u1") as Result.Ok).value
    private val session = FixedSessionProvider(Session(accountId = accountId, activeCrewId = null))

    private fun build(
        write: FakeAccountWritePort = FakeAccountWritePort(),
        outbox: RecordingOutboxPort = RecordingOutboxPort(),
        online: Boolean = true,
    ) = UpdateMyBioUseCase(write, session, FakeConnectivityPort(online), outbox)

    @Test fun valid_bio_persists_and_returns_ok() = runTest {
        val write = FakeAccountWritePort()
        val result = build(write)("Home cook from Barcelona")

        assertIs<Result.Ok<Unit>>(result)
        assertEquals(1, write.bioCalls.size)
        val (calledId, calledBio) = write.bioCalls.first()
        assertEquals(accountId, calledId)
        assertEquals("Home cook from Barcelona", calledBio?.value)
    }

    @Test fun blank_bio_persists_null_and_returns_ok() = runTest {
        val write = FakeAccountWritePort()
        val result = build(write)("   ")   // whitespace-only = clear

        assertIs<Result.Ok<Unit>>(result)
        assertEquals(1, write.bioCalls.size)
        assertNull(write.bioCalls.first().second)  // null = clear
    }

    @Test fun bio_exceeds_cap_returns_bio_too_long_without_calling_repo() = runTest {
        val write = FakeAccountWritePort()
        val result = build(write)("a".repeat(Bio.MAX_LENGTH + 1))

        assertIs<Result.Err<ProfileError>>(result)
        assertEquals(ProfileError.Validation.BioTooLong, result.error)
        assertEquals(0, write.bioCalls.size)
    }

    @Test fun bio_exactly_at_cap_persists_ok() = runTest {
        val write = FakeAccountWritePort()
        val atCap = "b".repeat(Bio.MAX_LENGTH)
        val result = build(write)(atCap)

        assertIs<Result.Ok<Unit>>(result)
        assertEquals(atCap, write.bioCalls.first().second?.value)
    }

    @Test fun online_backend_failure_falls_back_to_enqueue() = runTest {
        val write = FakeAccountWritePort().also { it.nextBioError = AccountWriteError.Backend.Unavailable }
        val outbox = RecordingOutboxPort()
        val result = build(write, outbox)("Nice bio")

        // Offline-first: a connectivity-class failure queues and succeeds (replayed on reconnect).
        assertIs<Result.Ok<Unit>>(result)
        assertEquals(1, outbox.enqueued.size)
        assertTrue(outbox.enqueued.single() is PendingCommand.SetBio)
    }

    @Test fun offline_enqueues_without_writing() = runTest {
        val write = FakeAccountWritePort()
        val outbox = RecordingOutboxPort()
        val result = build(write, outbox, online = false)("Nice bio")

        assertIs<Result.Ok<Unit>>(result)
        assertEquals(0, write.bioCalls.size)
        val cmd = outbox.enqueued.single()
        assertTrue(cmd is PendingCommand.SetBio)
        assertEquals(accountId, cmd.accountId)
        assertEquals("Nice bio", cmd.bio?.value)
    }

    @Test fun bio_is_trimmed_before_persisting() = runTest {
        val write = FakeAccountWritePort()
        val result = build(write)("  Trimmed bio  ")

        assertIs<Result.Ok<Unit>>(result)
        assertEquals("Trimmed bio", write.bioCalls.first().second?.value)
    }
}
