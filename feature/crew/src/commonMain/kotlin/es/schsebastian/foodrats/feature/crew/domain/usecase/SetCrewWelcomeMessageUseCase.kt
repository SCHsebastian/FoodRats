package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.WelcomeMessage
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository
import kotlinx.coroutines.flow.first

/**
 * Sets (or clears) the crew's pinned welcome message. Owner-only; authorization is enforced by the
 * repository.
 *
 * Validates [message] via [WelcomeMessage.of] first. A blank message is interpreted as "clear the
 * welcome message" (sends `null` to Firestore). Returns
 * [CrewError.Validation.WelcomeMessageTooLong] when the input exceeds [WelcomeMessage.MAX_LEN] = 200 chars.
 */
class SetCrewWelcomeMessageUseCase(
    private val repository: CrewRepository,
    private val session: SessionProvider,
) {
    suspend operator fun invoke(crewId: CrewId, message: String): Result<Unit, CrewError> {
        val accountId = when (val s = session.requireCurrent()) {
            is Result.Ok  -> s.value.accountId
            is Result.Err -> return Result.failure(CrewError.Backend.Unavailable)
        }
        val validated = when (val r = WelcomeMessage.of(message)) {
            is Result.Ok  -> r.value      // null = blank input → clear message
            is Result.Err -> return Result.failure(r.error)
        }
        return repository.setWelcomeMessage(crewId, accountId, validated?.value)
    }
}
