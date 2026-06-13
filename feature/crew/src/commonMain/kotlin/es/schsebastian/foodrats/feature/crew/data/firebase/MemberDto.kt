package es.schsebastian.foodrats.feature.crew.data.firebase

import kotlinx.serialization.Serializable

/**
 * Crew-doc membership entry. Identity (name + avatar) is not modelled here — it
 * lives on `accounts/{uid}` and is resolved live via `AccountReadPort`. Only the join
 * timestamp is retained. Legacy crew docs that still carry `displayName`/`avatarUrl`
 * deserialize cleanly because kotlinx-serialization tolerates unknown fields.
 */
@Serializable
data class MemberDto(
    val joinedAtEpochMs: Long? = null,
)
