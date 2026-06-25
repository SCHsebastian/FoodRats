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

class RemoveMyAvatarUseCaseTest {
    private val accountId = (AccountId.of("u1") as Result.Ok).value
    private val crewId = (CrewId.of("c1") as Result.Ok).value
    private val session = Session(accountId, crewId)

    @Test
    fun signed_out_returns_session_error() = runTest {
        val r = RemoveMyAvatarUseCase(
            FakeAccountWritePort(),
            FixedSessionProvider(null),
        ).invoke()
        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Session.SignedOut, r.error)
    }

    @Test
    fun success_returns_unit() = runTest {
        val port = FakeAccountWritePort()
        val r = RemoveMyAvatarUseCase(port, FixedSessionProvider(session)).invoke()
        assertTrue(r is Result.Ok)
        assertEquals(1, port.avatarRemovals.size)
        assertEquals(accountId, port.avatarRemovals[0])
    }

    @Test
    fun storage_failure_maps_to_avatar_remove_failed() = runTest {
        val port = FakeAccountWritePort().also {
            it.nextRemoveAvatarError = AccountWriteError.Backend.Unavailable
        }
        val r = RemoveMyAvatarUseCase(port, FixedSessionProvider(session)).invoke()
        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Avatar.RemoveFailed, r.error)
    }
}
