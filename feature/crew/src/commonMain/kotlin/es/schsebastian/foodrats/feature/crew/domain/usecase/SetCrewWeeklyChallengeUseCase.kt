package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.OutboxPort
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.error.toCrewError
import es.schsebastian.foodrats.feature.crew.domain.model.WeeklyChallenge
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository
import kotlinx.coroutines.flow.first

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
 *
 * OFFLINE-FIRST (P2 §0.5). Validation + session resolution run first, and the set-at timestamp is
 * stamped HERE (at enqueue time) so a deferred replay preserves when the challenge was authored.
 * When offline — or the direct write fails with a connectivity-class error
 * ([CrewError.Backend.Network] / [CrewError.Backend.Unavailable]) — the change is durably parked in
 * the [OutboxPort] and the use case returns [Result.Ok]; the `OutboxRunner` replays it (idempotently)
 * when connectivity returns.
 */
class SetCrewWeeklyChallengeUseCase(
    private val repository: CrewRepository,
    private val session: SessionProvider,
    private val clock: Clock,
    private val connectivity: ConnectivityPort,
    private val outbox: OutboxPort,
) {
    suspend operator fun invoke(crewId: CrewId, challenge: String): Result<Unit, CrewError> {
        val accountId = when (val s = session.requireCurrent()) {
            is Result.Ok  -> s.value.accountId
            is Result.Err -> return Result.failure(s.error.toCrewError())
        }
        val validated = when (val r = WeeklyChallenge.of(challenge)) {
            is Result.Ok  -> r.value      // null = blank input → clear challenge
            is Result.Err -> return Result.failure(r.error)
        }
        // Compute the (text, setAt) pair ONCE so the online write and the offline enqueue agree.
        val challengeText = validated?.value
        val setAtMillis = if (validated == null) null else clock.now().toEpochMilliseconds()
        if (!connectivity.isOnline().first()) {
            repository.offlineOwnerGuard(crewId, accountId)?.let { return Result.failure(it) }
            return enqueue(crewId, accountId, challengeText, setAtMillis)
        }
        return when (
            val r = repository.setWeeklyChallenge(crewId, accountId, challengeText, setAtMillis)
        ) {
            is Result.Ok -> r
            is Result.Err -> when (r.error) {
                CrewError.Backend.Network, CrewError.Backend.Unavailable ->
                    enqueue(crewId, accountId, challengeText, setAtMillis)
                else -> r
            }
        }
    }

    private suspend fun enqueue(
        crewId: CrewId,
        requestedBy: AccountId,
        challenge: String?,
        setAtMillis: Long?,
    ): Result<Unit, CrewError> {
        outbox.enqueue(PendingCommand.SetCrewWeeklyChallenge(crewId, requestedBy, challenge, setAtMillis))
        return Result.success(Unit)
    }
}
