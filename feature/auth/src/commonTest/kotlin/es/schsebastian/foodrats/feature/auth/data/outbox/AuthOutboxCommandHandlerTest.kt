package es.schsebastian.foodrats.feature.auth.data.outbox

import es.schsebastian.foodrats.core.domain.account.AccountWriteError
import es.schsebastian.foodrats.core.domain.account.Bio
import es.schsebastian.foodrats.core.domain.account.DisplayName
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.outbox.OutboxExecuteResult
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.feature.auth.testdoubles.FakeAccountWritePort
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthOutboxCommandHandlerTest {
    private val acc = (AccountId.of("u1") as Result.Ok).value
    private val crew = (es.schsebastian.foodrats.core.domain.model.CrewId.of("c1") as Result.Ok).value
    private val setName = PendingCommand.SetDisplayName(acc, DisplayName.of("Alice").getOrNull()!!)
    private val setBio = PendingCommand.SetBio(acc, Bio.of("hi").getOrNull())

    private fun handler(write: FakeAccountWritePort = FakeAccountWritePort()) =
        AuthOutboxCommandHandler(write) to write

    @Test fun handles_only_profile_text_commands() {
        val (h, _) = handler()
        assertTrue(h.handles(setName))
        assertTrue(h.handles(setBio))
        assertFalse(h.handles(PendingCommand.LeaveCrew(crew, acc)))
        assertFalse(h.handles(PendingCommand.RenameCrew(crew, acc, "x")))
    }

    @Test fun set_display_name_success_writes_and_returns_success() = runTest {
        val (h, write) = handler()
        assertEquals(OutboxExecuteResult.Success, h.execute(setName))
        assertEquals(listOf(acc to "Alice"), write.displayNameCalls)
    }

    @Test fun set_bio_success_returns_success() = runTest {
        val (h, write) = handler()
        assertEquals(OutboxExecuteResult.Success, h.execute(setBio))
        assertEquals(1, write.bioCalls.size)
    }

    @Test fun network_failure_is_retryable() = runTest {
        val (h, write) = handler()
        write.nextDisplayNameError = AccountWriteError.Backend.Network
        assertIs<OutboxExecuteResult.Retryable>(h.execute(setName))
    }

    @Test fun session_expired_is_terminal() = runTest {
        val (h, write) = handler()
        write.nextBioError = AccountWriteError.Session.Expired
        assertIs<OutboxExecuteResult.Terminal>(h.execute(setBio))
    }

    @Test fun permission_denied_is_terminal() = runTest {
        val (h, write) = handler()
        write.nextDisplayNameError = AccountWriteError.Backend.PermissionDenied
        assertIs<OutboxExecuteResult.Terminal>(h.execute(setName))
    }
}
