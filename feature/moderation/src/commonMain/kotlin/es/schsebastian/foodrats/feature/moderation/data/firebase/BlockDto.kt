package es.schsebastian.foodrats.feature.moderation.data.firebase

import kotlinx.serialization.Serializable

/**
 * Firestore document at `accounts/{owner}/blocks/{blockedUid}` (UGC compliance §5). One doc per
 * blocked account; the document id IS the blocked account's uid, so it is not stored in the body.
 * Absence = not blocked.
 *
 * The default `0L` lets kotlinx-serialization tolerate a legacy/malformed doc that omits the field
 * (it reads back as "blocked at epoch 0" rather than failing the whole snapshot).
 */
@Serializable
data class BlockDto(val createdAtEpochMs: Long = 0L)
