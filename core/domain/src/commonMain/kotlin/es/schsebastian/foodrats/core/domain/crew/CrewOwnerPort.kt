package es.schsebastian.foodrats.core.domain.crew

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.coroutines.flow.Flow

/**
 * Live owner of a crew. Consumed by features that need owner-vs-member authorization
 * for UI affordances (e.g. who may delete a meal or comment) without depending on
 * `:feature:crew`. Emits `null` when the crew is unknown or unreadable.
 */
interface CrewOwnerPort {
    fun observeOwner(crewId: CrewId): Flow<AccountId?>
}
