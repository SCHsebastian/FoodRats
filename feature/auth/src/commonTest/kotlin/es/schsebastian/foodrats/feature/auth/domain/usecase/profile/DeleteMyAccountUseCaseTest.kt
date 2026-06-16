package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.account.AccountDeletionError
import es.schsebastian.foodrats.core.domain.account.AccountDeletionPort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.testdoubles.FixedSessionProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeleteMyAccountUseCaseTest {
    private val accountId = (AccountId.of("u1") as Result.Ok).value
    private val crewId = (CrewId.of("c1") as Result.Ok).value
    private val signedIn = Session(accountId, crewId)
    private val phrase = "DELETE Ana"

    private class FakeAccountDeletionPort(
        private val result: Result<Unit, AccountDeletionError>,
    ) : AccountDeletionPort {
        val calls: MutableList<Pair<AccountId, String>> = mutableListOf()
        override suspend fun requestDeletion(
            accountId: AccountId,
            confirmation: String,
        ): Result<Unit, AccountDeletionError> {
            calls += accountId to confirmation
            return result
        }
    }

    @Test
    fun phrase_mismatch_returns_error() = runTest {
        val deletion = FakeAccountDeletionPort(Result.success(Unit))
        val uc = DeleteMyAccountUseCase(FixedSessionProvider(signedIn), deletion)

        val r = uc.invoke(confirmation = "DELETE An", expected = phrase)

        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Delete.PhraseMismatch, r.error)
        // Defense-in-depth: the port is never reached on a phrase mismatch.
        assertTrue(deletion.calls.isEmpty())
    }

    @Test
    fun signed_out_returns_session_error() = runTest {
        val deletion = FakeAccountDeletionPort(Result.success(Unit))
        val uc = DeleteMyAccountUseCase(FixedSessionProvider(session = null), deletion)

        val r = uc.invoke(confirmation = phrase, expected = phrase)

        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Session.SignedOut, r.error)
        assertTrue(deletion.calls.isEmpty())
    }

    @Test
    fun success_calls_deletion_port() = runTest {
        val deletion = FakeAccountDeletionPort(Result.success(Unit))
        val uc = DeleteMyAccountUseCase(FixedSessionProvider(signedIn), deletion)

        val r = uc.invoke(confirmation = phrase, expected = phrase)

        assertTrue(r is Result.Ok)
        // The session's account id and the validated phrase are forwarded verbatim.
        assertEquals(listOf(accountId to phrase), deletion.calls)
    }

    @Test
    fun deletion_error_propagates() = runTest {
        val deletion = FakeAccountDeletionPort(
            Result.failure(AccountDeletionError.Deletion.OwnerReassignFailed),
        )
        val uc = DeleteMyAccountUseCase(FixedSessionProvider(signedIn), deletion)

        val r = uc.invoke(confirmation = phrase, expected = phrase)

        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Delete.OwnerReassignFailed, r.error)
        assertEquals(listOf(accountId to phrase), deletion.calls)
    }
}
