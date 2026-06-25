package es.schsebastian.foodrats.feature.notifications.domain.repository

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.domain.model.DeviceToken

interface DeviceTokenRepository {
    /**
     * Writes accounts/{accountId}/devices/{token}. [languageTag] is the device's effective UI
     * language (bare BCP-47, e.g. "en"/"es"); the server reads it to localize the OS notification
     * block. Null leaves the field absent (server then falls back to English).
     */
    suspend fun upsert(
        accountId: AccountId,
        token: DeviceToken,
        languageTag: String?,
    ): Result<Unit, NotificationError.Token>

    /** Removes accounts/{accountId}/devices/{token}. Idempotent — deleting a missing doc is a no-op. */
    suspend fun delete(accountId: AccountId, token: DeviceToken): Result<Unit, NotificationError.Token>
}
