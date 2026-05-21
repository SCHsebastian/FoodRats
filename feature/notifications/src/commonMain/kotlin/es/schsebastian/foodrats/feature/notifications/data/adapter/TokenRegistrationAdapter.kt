package es.schsebastian.foodrats.feature.notifications.data.adapter

import es.schsebastian.foodrats.core.domain.notifications.TokenRegistrationError
import es.schsebastian.foodrats.core.domain.notifications.TokenRegistrationPort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.domain.usecase.RegisterDeviceTokenUseCase

/**
 * Adapter that exposes [RegisterDeviceTokenUseCase] through the cross-feature
 * [TokenRegistrationPort]. Maps the feature-local [NotificationError.Token] tree
 * down to the smaller port-level [TokenRegistrationError] tree.
 */
class TokenRegistrationAdapter(
    private val useCase: RegisterDeviceTokenUseCase,
) : TokenRegistrationPort {
    override suspend fun registerCurrentDeviceToken(): Result<Unit, TokenRegistrationError> =
        when (val r = useCase()) {
            is Result.Ok  -> Result.success(Unit)
            is Result.Err -> Result.failure(r.error.toPortError())
        }

    private fun NotificationError.Token.toPortError(): TokenRegistrationError = when (this) {
        NotificationError.Token.Unavailable   -> TokenRegistrationError.Unavailable
        NotificationError.Token.PersistFailed -> TokenRegistrationError.Unavailable
    }
}
