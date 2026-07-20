package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.error.toCrewError
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository
import kotlinx.coroutines.flow.first

class DeleteCrewUseCase(
    private val repository: CrewRepository,
    private val session: SessionProvider,
    private val activeCrew: ActiveCrewProvider,
) {
    suspend operator fun invoke(crewId: CrewId): Result<Unit, CrewError> {
        val accountId = when (val s = session.requireCurrent()) {
            is Result.Ok -> s.value.accountId
            is Result.Err -> return Result.failure(s.error.toCrewError())
        }
        val r = repository.deleteCrew(crewId, accountId)
        // Deleting the ACTIVE crew invalidates the active-crew selection — a stale id would
        // cold-start the app into the feed of a crew that no longer exists (see LeaveCrewUseCase).
        if (r is Result.Ok && activeCrew.current.first() == crewId) activeCrew.clear()
        return r
    }
}
