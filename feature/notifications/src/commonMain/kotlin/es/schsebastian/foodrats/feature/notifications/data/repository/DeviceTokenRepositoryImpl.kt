package es.schsebastian.foodrats.feature.notifications.data.repository

import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.notifications.data.firebase.DeviceTokenDto
import es.schsebastian.foodrats.feature.notifications.data.firebase.DeviceTokenFirestoreDataSource
import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.domain.model.DeviceToken
import es.schsebastian.foodrats.feature.notifications.domain.repository.DeviceTokenRepository
import kotlinx.coroutines.withContext

internal class DeviceTokenRepositoryImpl(
    private val dataSource: DeviceTokenFirestoreDataSource,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock,
    private val platformLabel: String,    // injected per platform via Koin: "android" / "ios"
) : DeviceTokenRepository {

    override suspend fun upsert(accountId: AccountId, token: DeviceToken): Result<Unit, NotificationError.Token> =
        withContext(dispatchers.io) {
            runCatching {
                dataSource.upsert(
                    accountId = accountId,
                    dto = DeviceTokenDto(
                        token = token.value,
                        platform = platformLabel,
                        updatedAtEpochMs = clock.now().toEpochMilliseconds(),
                    ),
                )
            }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { Result.failure(NotificationError.Token.PersistFailed) },
            )
        }
}
