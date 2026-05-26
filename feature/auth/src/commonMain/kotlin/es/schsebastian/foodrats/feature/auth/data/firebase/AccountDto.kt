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
    // Reserved data-consent fields (spec §13); default 0 / null = "no consent recorded".
    val dataConsentVersion: Int = 0,
    val dataConsentGrantedAtEpochMs: Long? = null,
)
