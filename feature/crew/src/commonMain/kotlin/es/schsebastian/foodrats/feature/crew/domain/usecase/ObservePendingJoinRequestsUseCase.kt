package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.JoinRequest
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository
import kotlinx.coroutines.flow.Flow

/** Streams a crew's pending join requests for the owner-only intake list in Crew Settings. */
class ObservePendingJoinRequestsUseCase(private val repo: CrewRepository) {
    operator fun invoke(crewId: CrewId): Flow<Result<List<JoinRequest>, CrewError>> =
        repo.observeJoinRequests(crewId)
}
