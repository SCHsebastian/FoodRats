package es.schsebastian.foodrats.feature.notifications.domain.usecase

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.domain.repository.DeviceTokenRepository
import es.schsebastian.foodrats.feature.notifications.domain.repository.FcmTokenProvider
import kotlinx.coroutines.flow.first

class RegisterDeviceTokenUseCase(
    private val provider: FcmTokenProvider,
    private val repository: DeviceTokenRepository,
    private val session: SessionProvider,
) {
    suspend operator fun invoke(): Result<Unit, NotificationError.Token> {
        val token = provider.token.first()
            ?: return Result.failure(NotificationError.Token.Unavailable)
        val account = session.current.first()?.accountId
            ?: return Result.failure(NotificationError.Token.Unavailable)
        return repository.upsert(account, token)
    }
}
