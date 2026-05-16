package es.schsebastian.foodrats.feature.notifications.data.firebase

import kotlinx.serialization.Serializable

@Serializable
data class DeviceTokenDto(
    val token: String? = null,
    val platform: String? = null,        // "android" | "ios"
    val updatedAtEpochMs: Long? = null,
)
