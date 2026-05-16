package es.schsebastian.foodrats.feature.crew.data.firebase

import kotlinx.serialization.Serializable

/**
 * Stored at crewCodes/{CODE} — the doc ID IS the invite code.
 * Existence-as-uniqueness: a Firestore transaction that does
 *   tx.get(crewCodes/{CODE}) then tx.set(crewCodes/{CODE}, ...)
 * is race-safe because transactions retry on read-set conflicts.
 */
@Serializable
data class CrewCodeDto(
    val crewId: String? = null,
    val createdAtEpochMs: Long? = null,
)
