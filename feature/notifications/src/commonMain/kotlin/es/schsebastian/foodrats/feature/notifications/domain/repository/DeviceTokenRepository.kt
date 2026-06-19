package es.schsebastian.foodrats.feature.notifications.domain.repository

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.domain.model.DeviceToken

interface DeviceTokenRepository {
    suspend fun upsert(accountId: AccountId, token: DeviceToken): Result<Unit, NotificationError.Token>

    /** Removes accounts/{accountId}/devices/{token}. Idempotent — deleting a missing doc is a no-op. */
    suspend fun delete(accountId: AccountId, token: DeviceToken): Result<Unit, NotificationError.Token>
}
