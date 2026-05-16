package es.schsebastian.foodrats.core.domain.crew

import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.coroutines.flow.Flow

/**
 * Active crew selection — a per-device preference observed by features that need to know
 * "which crew is the user currently focused on" (Feed, Stats). Implementation lives in
 * :feature:crew (DataStore-backed) and is registered in the crew Koin module.
 */
interface ActiveCrewProvider {
    val current: Flow<CrewId?>
    suspend fun set(crewId: CrewId)
    suspend fun clear()
}
