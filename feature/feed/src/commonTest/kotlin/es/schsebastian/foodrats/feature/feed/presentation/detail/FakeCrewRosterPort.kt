package es.schsebastian.foodrats.feature.feed.presentation.detail

import es.schsebastian.foodrats.core.domain.crew.CrewRosterPort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A mutable in-memory [CrewRosterPort] fake — tests set [members] to drive @-mention candidates. */
class FakeCrewRosterPort(initial: List<AccountId> = emptyList()) : CrewRosterPort {
    val members = MutableStateFlow(initial)
    override fun observeMembers(crewId: CrewId): Flow<List<AccountId>> = members
}
