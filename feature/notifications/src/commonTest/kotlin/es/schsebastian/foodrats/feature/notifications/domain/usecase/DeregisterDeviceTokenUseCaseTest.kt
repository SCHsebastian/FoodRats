package es.schsebastian.foodrats.feature.notifications.domain.usecase

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.domain.model.DeviceToken
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// Reuses FakeRepo / FakeTokenProvider / FakeSession from RegisterDeviceTokenUseCaseTest (same package).
class DeregisterDeviceTokenUseCaseTest {

    private val me = (AccountId.of("uid-me") as Result.Ok).value

    @Test fun deletes_token_for_current_account_when_signed_in() = runTest {
        val repo = FakeRepo()
        val uc = DeregisterDeviceTokenUseCase(FakeTokenProvider(DeviceToken("t-1")), repo, FakeSession(Session(me, null)))
        val r = uc()
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(listOf(me to DeviceToken("t-1")), repo.deletes)
    }

    @Test fun returns_Token_Unavailable_when_provider_has_no_token() = runTest {
        val uc = DeregisterDeviceTokenUseCase(FakeTokenProvider(null), FakeRepo(), FakeSession(Session(me, null)))
        assertEquals(Result.failure(NotificationError.Token.Unavailable), uc())
    }

    @Test fun returns_Token_Unavailable_when_not_signed_in() = runTest {
        val uc = DeregisterDeviceTokenUseCase(FakeTokenProvider(DeviceToken("t")), FakeRepo(), FakeSession(null))
        assertEquals(Result.failure(NotificationError.Token.Unavailable), uc())
    }

    @Test fun propagates_repo_error() = runTest {
        val repo = FakeRepo().apply { nextResult = Result.failure(NotificationError.Token.PersistFailed) }
        val uc = DeregisterDeviceTokenUseCase(FakeTokenProvider(DeviceToken("t")), repo, FakeSession(Session(me, null)))
        assertEquals(Result.failure(NotificationError.Token.PersistFailed), uc())
    }
}
