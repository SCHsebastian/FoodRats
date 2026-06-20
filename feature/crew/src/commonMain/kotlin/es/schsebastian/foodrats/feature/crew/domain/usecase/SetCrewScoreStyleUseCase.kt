package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.crew.CrewScoreStyle
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository
import kotlinx.coroutines.flow.first

/**
 * Sets the crew's Score display vocabulary (C8). Owner-only; authorization is enforced by the
 * repository and the `['scoreStyle']` Firestore rule arm.
 *
 * No validation errors: [CrewScoreStyle] is a sealed interface with exactly 3 data-object leaves;
 * the caller passes a typed value and the repository converts it to the Firestore string via
 * [es.schsebastian.foodrats.feature.crew.data.firebase.toDto]. Re-uses the existing backend /
 * persist [CrewError] leaves — no new validation leaf is needed.
 */
class SetCrewScoreStyleUseCase(
    private val repository: CrewRepository,
    private val session: SessionProvider,
) {
    suspend operator fun invoke(crewId: CrewId, style: CrewScoreStyle): Result<Unit, CrewError> {
        val accountId = when (val s = session.requireCurrent()) {
            is Result.Ok  -> s.value.accountId
            is Result.Err -> return Result.failure(CrewError.Backend.Unavailable)
        }
        return repository.setScoreStyle(crewId, accountId, style)
    }
}
