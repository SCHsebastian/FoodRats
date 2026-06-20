package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.CrewTagline
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository
import kotlinx.coroutines.flow.first

/**
 * Sets (or clears) the crew tagline. Owner-only; authorization is enforced by the repository.
 *
 * Validates [tagline] via [CrewTagline.of] first. A blank tagline is interpreted as "clear the
 * tagline" (sends `null` to Firestore). Returns [CrewError.Validation.TaglineTooLong] on a tagline
 * that exceeds [CrewTagline.MAX_LEN] = 120 chars.
 */
class SetCrewTaglineUseCase(
    private val repository: CrewRepository,
    private val session: SessionProvider,
) {
    suspend operator fun invoke(crewId: CrewId, tagline: String): Result<Unit, CrewError> {
        val accountId = when (val s = session.requireCurrent()) {
            is Result.Ok  -> s.value.accountId
            is Result.Err -> return Result.failure(CrewError.Backend.Unavailable)
        }
        val validated = when (val r = CrewTagline.of(tagline)) {
            is Result.Ok  -> r.value      // null = blank input → clear tagline
            is Result.Err -> return Result.failure(r.error)
        }
        return repository.setTagline(crewId, accountId, validated?.value)
    }
}
