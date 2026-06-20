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
    // Owner-settable short tagline/house rules (≤ 120 chars). `null` means no tagline is set;
    // old crew docs without the field deserialize to null (no migration needed).
    // Null-pinned so GitLive encodeDefaults=true doesn't emit `null` and fail an affectedKeys
    // check it was not part of.
    val tagline: String? = null,
    // Owner-settable onboarding welcome message shown as a dismissible feed banner to new joiners
    // (≤ 200 chars). `null` means no message is set; old docs without the field deserialize to null
    // (no migration needed). Null-pinned for the same GitLive encodeDefaults reason as `tagline`.
    val welcomeMessage: String? = null,
)
