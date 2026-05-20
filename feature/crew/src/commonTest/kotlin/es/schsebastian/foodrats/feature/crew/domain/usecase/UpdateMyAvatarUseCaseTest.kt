package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.test.FakeCrewRepository
import es.schsebastian.foodrats.feature.crew.domain.test.aid
import es.schsebastian.foodrats.feature.crew.domain.test.cid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class FakeSessionForAvatar(session: Session?) : SessionProvider {
    private val _session = MutableStateFlow(session)
    override val current: Flow<Session?> = _session
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        _session.value?.let { Result.success(it) } ?: Result.failure(SessionError.NotSignedIn)
}

class UpdateMyAvatarUseCaseTest {

    private val accountId = aid("acc-1")
    private val crewId = cid("crew-1")
    private val session = Session(accountId, crewId)

    @Test fun empty_bytes_fail_without_touching_repo() = runTest {
        val repo = FakeCrewRepository()
        val uc = UpdateMyAvatarUseCase(repo, FakeSessionForAvatar(session))
        val r = uc(crewId, ByteArray(0))
        assertIs<Result.Err<CrewError>>(r)
        assertEquals(null, repo.lastAvatarUpdate)
    }

    @Test fun no_session_returns_backend_unavailable() = runTest {
        val repo = FakeCrewRepository()
        val uc = UpdateMyAvatarUseCase(repo, FakeSessionForAvatar(null))
        val r = uc(crewId, byteArrayOf(1, 2, 3))
        assertEquals(Result.failure(CrewError.Backend.Unavailable), r)
    }

    @Test fun delegates_to_repo_with_caller_account() = runTest {
        val repo = FakeCrewRepository()
        repo.nextAvatarUrl = "https://cdn/avatar.jpg"
        val uc = UpdateMyAvatarUseCase(repo, FakeSessionForAvatar(session))
        val r = uc(crewId, byteArrayOf(1, 2, 3))
        assertEquals(Result.success("https://cdn/avatar.jpg"), r)
        assertEquals(Triple(crewId, accountId, 3), repo.lastAvatarUpdate)
    }
}
