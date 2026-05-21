package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.account.AccountWriteError
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.testdoubles.FakeAccountWritePort
import es.schsebastian.foodrats.feature.auth.testdoubles.FixedSessionProvider
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
        val r = build(signedIn).invoke("   ")
        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Validation.DisplayNameBlank, r.error)
    }

    @Test
    fun rejects_too_long_name() = runTest {
        val r = build(signedIn).invoke("x".repeat(41))
        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Validation.DisplayNameTooLong, r.error)
    }

    @Test
    fun signed_out_returns_session_error() = runTest {
        val r = build(session = null).invoke("Alice")
        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Session.SignedOut, r.error)
    }

    @Test
    fun success_writes_canonical_account() = runTest {
        val acc = FakeAccountWritePort()
        val uc = UpdateMyDisplayNameUseCase(acc, FixedSessionProvider(signedIn))

        val r = uc.invoke("Alice")
        assertTrue(r is Result.Ok)
        assertEquals(listOf(accountId to "Alice"), acc.displayNameCalls)
    }

    @Test
    fun account_write_failure_propagates() = runTest {
        val acc = FakeAccountWritePort().also { it.nextDisplayNameError = AccountWriteError.Backend.Unavailable }
        val uc = UpdateMyDisplayNameUseCase(acc, FixedSessionProvider(signedIn))

        val r = uc.invoke("Alice")
        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Backend.Unavailable, r.error)
    }

    private fun build(session: Session?): UpdateMyDisplayNameUseCase =
        UpdateMyDisplayNameUseCase(
            FakeAccountWritePort(),
            FixedSessionProvider(session),
        )
}
