package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.WeeklyChallenge
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository

/**
 * Sets (or clears) the crew's weekly challenge. Owner-only; authorization is enforced by the
 * repository.
 *
 * Validates [challenge] via [WeeklyChallenge.of] first. A blank challenge is interpreted as
 * "clear" — sends `null` for both text and timestamp to Firestore. Returns
 * [CrewError.Validation.WeeklyChallengeTooLong] when the input exceeds [WeeklyChallenge.MAX_LEN] = 80 chars.
 *
 * When setting, stamps [Clock.now] as the challenge creation time so the feed can perform the
 * 7-day client-side expiry check.
 */
class SetCrewWeeklyChallengeUseCase(
    private val repository: CrewRepository,
    private val session: SessionProvider,
    private val clock: Clock,
) {
    suspend operator fun invoke(crewId: CrewId, challenge: String): Result<Unit, CrewError> {
        val accountId = when (val s = session.requireCurrent()) {
            is Result.Ok  -> s.value.accountId
            is Result.Err -> return Result.failure(CrewError.Backend.Unavailable)
        }
        val validated = when (val r = WeeklyChallenge.of(challenge)) {
            is Result.Ok  -> r.value      // null = blank input → clear challenge
            is Result.Err -> return Result.failure(r.error)
        }
        return if (validated == null) {
            // Blank input → clear both fields.
            repository.setWeeklyChallenge(crewId, accountId, challenge = null, setAtMillis = null)
        } else {
            repository.setWeeklyChallenge(
                crewId,
                accountId,
                challenge = validated.value,
                setAtMillis = clock.now().toEpochMilliseconds(),
            )
        }
    }
}
