package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.model.CrewId

class SwitchActiveCrewUseCase(private val provider: ActiveCrewProvider) {
    suspend operator fun invoke(crewId: CrewId) = provider.set(crewId)
}
