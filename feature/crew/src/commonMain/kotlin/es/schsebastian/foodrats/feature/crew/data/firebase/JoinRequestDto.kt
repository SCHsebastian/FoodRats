package es.schsebastian.foodrats.feature.crew.data.firebase

import kotlinx.serialization.Serializable

/**
 * Firestore shape of a pending join request at `crews/{crewId}/joinRequests/{accountId}`. The doc
 * id IS the requester's account id; [accountId] mirrors it (the create rule pins them equal). Both
 * fields are nullable with defaults so a malformed/legacy doc deserializes rather than throwing —
 * the mapper drops anything incomplete.
 */
@Serializable
data class JoinRequestDto(
    val accountId: String? = null,
    val requestedAtEpochMs: Long? = null,
)
