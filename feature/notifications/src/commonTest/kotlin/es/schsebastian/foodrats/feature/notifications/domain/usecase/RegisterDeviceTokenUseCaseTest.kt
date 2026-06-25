package es.schsebastian.foodrats.feature.notifications.domain.usecase

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.domain.model.DeviceToken
import es.schsebastian.foodrats.feature.notifications.domain.repository.DeviceTokenRepository
import es.schsebastian.foodrats.feature.notifications.domain.repository.EffectiveLanguageTag
import es.schsebastian.foodrats.feature.notifications.domain.repository.FcmTokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FakeRepo : DeviceTokenRepository {
    val upserts = mutableListOf<Pair<AccountId, DeviceToken>>()
    val upsertTags = mutableListOf<String?>()
    val deletes = mutableListOf<Pair<AccountId, DeviceToken>>()
    var nextResult: Result<Unit, NotificationError.Token> = Result.success(Unit)
    override suspend fun upsert(
        accountId: AccountId,
        token: DeviceToken,
        languageTag: String?,
    ): Result<Unit, NotificationError.Token> {
        upserts += accountId to token
        upsertTags += languageTag
        return nextResult
    }
    override suspend fun delete(accountId: AccountId, token: DeviceToken): Result<Unit, NotificationError.Token> {
        deletes += accountId to token
        return nextResult
    }
}
class FakeTokenProvider(initial: DeviceToken?) : FcmTokenProvider {
    val state = MutableStateFlow(initial)
    override val token: Flow<DeviceToken?> = state
}
class FakeSession(val s: Session?) : SessionProvider {
    override val current: Flow<Session?> = MutableStateFlow(s)
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        s?.let { Result.success(it) } ?: Result.failure(SessionError.NotSignedIn)
}

class RegisterDeviceTokenUseCaseTest {

    private val me = (AccountId.of("uid-me") as Result.Ok).value
    private fun tag(value: String) = EffectiveLanguageTag { value }

    @Test fun upserts_token_with_language_tag_when_signed_in() = runTest {
        val repo = FakeRepo()
        val provider = FakeTokenProvider(DeviceToken("t-1"))
        val uc = RegisterDeviceTokenUseCase(provider, repo, FakeSession(Session(me, null)), tag("es"))
        val r = uc()
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(listOf(me to DeviceToken("t-1")), repo.upserts)
        assertEquals(listOf<String?>("es"), repo.upsertTags)
    }

    @Test fun returns_Token_Unavailable_when_provider_has_no_token() = runTest {
        val repo = FakeRepo()
        val uc = RegisterDeviceTokenUseCase(FakeTokenProvider(null), repo, FakeSession(Session(me, null)), tag("en"))
        assertEquals(Result.failure(NotificationError.Token.Unavailable), uc())
    }

    @Test fun returns_Token_Unavailable_when_not_signed_in() = runTest {
        val repo = FakeRepo()
        val uc = RegisterDeviceTokenUseCase(FakeTokenProvider(DeviceToken("t")), repo, FakeSession(null), tag("en"))
        assertEquals(Result.failure(NotificationError.Token.Unavailable), uc())
    }

    @Test fun propagates_repo_error() = runTest {
        val repo = FakeRepo().apply { nextResult = Result.failure(NotificationError.Token.PersistFailed) }
        val uc = RegisterDeviceTokenUseCase(FakeTokenProvider(DeviceToken("t")), repo, FakeSession(Session(me, null)), tag("en"))
        assertEquals(Result.failure(NotificationError.Token.PersistFailed), uc())
    }
}
