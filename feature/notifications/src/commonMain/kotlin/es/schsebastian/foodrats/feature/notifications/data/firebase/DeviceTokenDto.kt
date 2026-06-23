package es.schsebastian.foodrats.feature.notifications.data.firebase

import kotlinx.serialization.Serializable

@Serializable
data class DeviceTokenDto(
    val token: String? = null,
    val platform: String? = null,        // "android" | "ios"
    val updatedAtEpochMs: Long? = null,
    // Effective UI language as a bare BCP-47 tag ("en", "es", …). The server reads this at FCM
    // send time and localizes the OS `notification` block per device — a backgrounded push is
    // rendered by the OS from that block, which the client never gets to localize itself.
    val languageTag: String? = null,
)
