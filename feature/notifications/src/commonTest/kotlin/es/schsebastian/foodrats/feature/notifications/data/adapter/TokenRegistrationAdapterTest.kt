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
import es.schsebastian.foodrats.feature.notifications.domain.repository.FcmTokenProvider
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
    override suspend fun upsert(accountId: AccountId, token: DeviceToken): Result<Unit, NotificationError.Token> =
        nextResult
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

    @Test fun returns_Ok_when_use_case_succeeds() = runTest {
        val useCase = RegisterDeviceTokenUseCase(
            FakeTokenProvider(DeviceToken("t-1")),
            FakeRepo(),
            FakeSession(Session(me, null)),
        )
        val adapter = TokenRegistrationAdapter(useCase)
        val r = adapter.registerCurrentDeviceToken()
        assertIs<Result.Ok<Unit>>(r)
    }

    @Test fun maps_Token_Unavailable_to_port_Unavailable() = runTest {
        // No token -> use case returns Token.Unavailable.
        val useCase = RegisterDeviceTokenUseCase(
            FakeTokenProvider(null),
            FakeRepo(),
            FakeSession(Session(me, null)),
        )
        val adapter = TokenRegistrationAdapter(useCase)
        assertEquals(
            Result.failure(TokenRegistrationError.Unavailable),
            adapter.registerCurrentDeviceToken(),
        )
    }

    @Test fun maps_Token_PersistFailed_to_port_Unavailable() = runTest {
        val useCase = RegisterDeviceTokenUseCase(
            FakeTokenProvider(DeviceToken("t-2")),
            FakeRepo(nextResult = Result.failure(NotificationError.Token.PersistFailed)),
            FakeSession(Session(me, null)),
        )
        val adapter = TokenRegistrationAdapter(useCase)
        assertEquals(
            Result.failure(TokenRegistrationError.Unavailable),
            adapter.registerCurrentDeviceToken(),
        )
    }
}
