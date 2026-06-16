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
    // Owner-settable crew policy: when true, a meal's author identity is masked from a
    // crewmate until they've cast their own score. Defaults false; old docs without the
    // field deserialize to false (pre-launch — no migration).
    val blindVoting: Boolean = false,
)
