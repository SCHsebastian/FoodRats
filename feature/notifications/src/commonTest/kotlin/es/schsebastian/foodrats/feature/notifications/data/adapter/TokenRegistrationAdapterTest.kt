package es.schsebastian.foodrats.feature.notifications.data.adapter

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.notifications.TokenRegistrationError
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.domain.model.DeviceToken
import es.schsebastian.foodrats.feature.notifications.domain.repository.DeviceTokenRepository
import es.schsebastian.foodrats.feature.notifications.domain.repository.EffectiveLanguageTag
import es.schsebastian.foodrats.feature.notifications.domain.repository.FcmTokenProvider
import es.schsebastian.foodrats.feature.notifications.domain.usecase.DeregisterDeviceTokenUseCase
import es.schsebastian.foodrats.feature.notifications.domain.usecase.RegisterDeviceTokenUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class FakeRepo(
    var nextResult: Result<Unit, NotificationError.Token> = Result.success(Unit),
) : DeviceTokenRepository {
    var deleted = false
    override suspend fun upsert(
        accountId: AccountId,
        token: DeviceToken,
        languageTag: String?,
    ): Result<Unit, NotificationError.Token> = nextResult
    override suspend fun delete(accountId: AccountId, token: DeviceToken): Result<Unit, NotificationError.Token> {
        deleted = true
        return nextResult
    }
}

private class FakeTokenProvider(initial: DeviceToken?) : FcmTokenProvider {
    override val token: Flow<DeviceToken?> = MutableStateFlow(initial)
}

private class FakeSession(private val s: Session?) : SessionProvider {
    override val current: Flow<Session?> = MutableStateFlow(s)
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        s?.let { Result.success(it) } ?: Result.failure(SessionError.NotSignedIn)
}

class TokenRegistrationAdapterTest {

    private val me = (AccountId.of("uid-me") as Result.Ok).value

    private fun adapter(
        provider: FakeTokenProvider,
        repo: FakeRepo,
        session: FakeSession,
    ) = TokenRegistrationAdapter(
        register = RegisterDeviceTokenUseCase(provider, repo, session, EffectiveLanguageTag { "en" }),
        deregister = DeregisterDeviceTokenUseCase(provider, repo, session),
    )

    @Test fun returns_Ok_when_use_case_succeeds() = runTest {
        val r = adapter(FakeTokenProvider(DeviceToken("t-1")), FakeRepo(), FakeSession(Session(me, null)))
            .registerCurrentDeviceToken()
        assertIs<Result.Ok<Unit>>(r)
    }

    @Test fun maps_Token_Unavailable_to_port_Unavailable() = runTest {
        // No token -> use case returns Token.Unavailable.
        assertEquals(
            Result.failure(TokenRegistrationError.Unavailable),
            adapter(FakeTokenProvider(null), FakeRepo(), FakeSession(Session(me, null)))
                .registerCurrentDeviceToken(),
        )
    }

    @Test fun maps_Token_PersistFailed_to_port_Unavailable() = runTest {
        assertEquals(
            Result.failure(TokenRegistrationError.Unavailable),
            adapter(
                FakeTokenProvider(DeviceToken("t-2")),
                FakeRepo(nextResult = Result.failure(NotificationError.Token.PersistFailed)),
                FakeSession(Session(me, null)),
            ).registerCurrentDeviceToken(),
        )
    }

    @Test fun deregister_returns_Ok_and_hits_repo_delete() = runTest {
        val repo = FakeRepo()
        val r = adapter(FakeTokenProvider(DeviceToken("t-1")), repo, FakeSession(Session(me, null)))
            .deregisterCurrentDeviceToken()
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(true, repo.deleted)
    }

    @Test fun deregister_maps_Token_Unavailable_to_port_Unavailable() = runTest {
        assertEquals(
            Result.failure(TokenRegistrationError.Unavailable),
            adapter(FakeTokenProvider(DeviceToken("t")), FakeRepo(), FakeSession(null))
                .deregisterCurrentDeviceToken(),
        )
    }
}
