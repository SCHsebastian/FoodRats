package es.schsebastian.foodrats.feature.crew.domain.test

import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [ActiveCrewProvider] for use-case / reconciler tests. */
class FakeActiveCrewProvider(initial: CrewId? = null) : ActiveCrewProvider {
    val state = MutableStateFlow(initial)
    override val current: Flow<CrewId?> get() = state
    override suspend fun set(crewId: CrewId) { state.value = crewId }
    override suspend fun clear() { state.value = null }
}
