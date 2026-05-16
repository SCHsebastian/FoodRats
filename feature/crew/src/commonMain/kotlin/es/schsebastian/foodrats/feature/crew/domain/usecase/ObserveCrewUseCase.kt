package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository
import kotlinx.coroutines.flow.Flow

class ObserveCrewUseCase(private val repo: CrewRepository) {
    operator fun invoke(crewId: CrewId): Flow<Result<Crew, CrewError>> = repo.observeCrew(crewId)
}
