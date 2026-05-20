package es.schsebastian.foodrats.core.domain.crew

import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.coroutines.flow.Flow

/** Lightweight projection used by features that need only "uid → display name + avatar". */
data class CrewMemberView(
    val accountUid: String,
    val displayName: String,
    val avatarUrl: String?,
)

/**
 * Streams the members of a crew. Implementation is responsible for sharing the
 * underlying listener across consumers (see CrewFirestoreDataSource per-crewId cache).
 */
interface CrewMembersPort {
    fun observeMembers(crewId: CrewId): Flow<List<CrewMemberView>>
}
