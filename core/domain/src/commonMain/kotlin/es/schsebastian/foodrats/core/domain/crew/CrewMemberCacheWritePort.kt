package es.schsebastian.foodrats.core.domain.crew

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result

/**
 * Writes the denormalized member fields in `crews/{crewId}.members.{uid}`. The canonical
 * identity lives in `accounts/{uid}` (see `AccountWritePort`); this port writes the
 * read-optimized cache so the crew members map stays in sync for member-list reads.
 *
 * Cache writes are best-effort from the caller's perspective: a failure here does not
 * compromise the canonical write, only the consistency of the denormalized snapshot.
 */
interface CrewMemberCacheWritePort {
    suspend fun setDisplayName(
        crewId: CrewId,
        accountId: AccountId,
        name: String,
    ): Result<Unit, CrewMemberCacheWriteError>

    suspend fun setAvatarUrl(
        crewId: CrewId,
        accountId: AccountId,
        url: String,
    ): Result<Unit, CrewMemberCacheWriteError>
}

sealed interface CrewMemberCacheWriteError {
    sealed interface Backend : CrewMemberCacheWriteError {
        data object Unavailable : Backend
    }
}
