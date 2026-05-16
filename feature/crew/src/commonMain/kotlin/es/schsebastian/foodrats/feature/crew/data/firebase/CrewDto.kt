package es.schsebastian.foodrats.feature.crew.data.firebase

import kotlinx.serialization.Serializable

@Serializable
data class CrewDto(
    val id: String? = null,
    val name: String? = null,
    val code: String? = null,           // mirrors the crewCodes/{code} doc ID, denormalized for read convenience
    val ownerId: String? = null,
    val createdAtEpochMs: Long? = null,
    val memberIds: List<String> = emptyList(),
    val members: Map<String, MemberDto> = emptyMap(),  // accountId -> MemberDto
)
