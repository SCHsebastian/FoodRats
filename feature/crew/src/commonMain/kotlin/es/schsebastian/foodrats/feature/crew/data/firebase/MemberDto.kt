package es.schsebastian.foodrats.feature.crew.data.firebase

import kotlinx.serialization.Serializable

@Serializable
data class MemberDto(
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val joinedAtEpochMs: Long? = null,
)
