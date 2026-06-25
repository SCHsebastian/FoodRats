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

class UpdateMyAvatarUseCaseTest {
    private val accountId = (AccountId.of("u1") as Result.Ok).value
    private val crewId = (CrewId.of("c1") as Result.Ok).value
    private val session = Session(accountId, crewId)

    @Test
    fun rejects_empty_bytes() = runTest {
        val r = UpdateMyAvatarUseCase(
            FakeAccountWritePort(),
            FixedSessionProvider(session),
        ).invoke(ByteArray(0))
        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Validation.EmptyBytes, r.error)
    }

    @Test
    fun signed_out_returns_session_error() = runTest {
        val r = UpdateMyAvatarUseCase(
            FakeAccountWritePort(),
            FixedSessionProvider(null),
        ).invoke(ByteArray(100) { 1 })
        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Session.SignedOut, r.error)
    }

    @Test
    fun success_uploads_and_returns_unit() = runTest {
        val acc = FakeAccountWritePort()
        val uc = UpdateMyAvatarUseCase(acc, FixedSessionProvider(session))

        val r = uc.invoke(ByteArray(100) { 1 })
        assertTrue(r is Result.Ok)
        assertEquals(1, acc.avatarUploads.size)
        assertEquals(accountId, acc.avatarUploads[0].first)
    }

    @Test
    fun upload_failure_propagates() = runTest {
        val acc = FakeAccountWritePort().also { it.nextAvatarError = AccountWriteError.Backend.Unavailable }
        val uc = UpdateMyAvatarUseCase(acc, FixedSessionProvider(session))

        val r = uc.invoke(ByteArray(100) { 1 })
        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Backend.Unavailable, r.error)
    }
}
