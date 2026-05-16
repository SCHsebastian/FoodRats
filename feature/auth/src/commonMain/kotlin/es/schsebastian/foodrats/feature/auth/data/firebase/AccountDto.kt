package es.schsebastian.foodrats.feature.auth.data.firebase

import kotlinx.serialization.Serializable

@Serializable
data class AccountDto(
    val id: String? = null,
    val handle: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val createdAtEpochMs: Long? = null,
)
