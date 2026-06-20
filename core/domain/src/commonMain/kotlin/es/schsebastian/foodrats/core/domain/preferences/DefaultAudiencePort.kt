package es.schsebastian.foodrats.core.domain.preferences

import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Persists the user's last-chosen publish audience (set of Crew IDs) so the
 * compose-plate flow remembers "who to publish to" across sessions.
 *
 * - **Default (absent key):** `null` — no saved preference, callers default to ALL
 *   the user's current crews (the existing behaviour before this feature landed).
 * - **Non-null value:** the IDs the user most recently selected; callers should
 *   intersect with the current crew membership before applying (a crew the user
 *   has since left must not persist in the selection).
 * - The value is per-device / per-user-account (DataStore scope); it is NOT
 *   written to the account doc (it is private preference, not public profile data).
 */
interface DefaultAudiencePort {
    /**
     * Emits the saved default audience, or `null` if none has been saved.
     * Never throws; absent key emits `null`.
     */
    val defaultAudience: Flow<Set<CrewId>?>

    /**
     * Persists [crewIds] as the new default audience. An empty set is valid
     * (the user deselected everything) but callers should prevent an empty
     * selection reaching publish by defaulting back to all crews.
     */
    suspend fun set(crewIds: Set<CrewId>): Result<Unit, DefaultAudienceError>
}

sealed interface DefaultAudienceError {
    sealed interface Persist : DefaultAudienceError {
        data object Unavailable : Persist
    }
}
