package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.OutboxPort
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.error.toCrewError
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository
import kotlinx.coroutines.flow.first

/**
 * Sets the crew banner's vertical focal point (C9) — the `0f..1f` position the fixed-height feed
 * crop anchors to (0 = top, 0.5 = center, 1 = bottom). Delegates to [CrewRepository.setBannerFocalY]
 * which enforces owner-only authorization. [focalY] is clamped to the valid range here so an
 * out-of-range gesture value can never reach the write.
 *
 * OFFLINE-FIRST (P2 §0.5). The focal point IS queue-able (it's a plain `0..1` value — unlike the
 * banner IMAGE bytes, which stay online-only). When offline — or the direct write fails with a
 * connectivity-class error ([CrewError.Backend.Network] / [CrewError.Backend.Unavailable]) — the
 * change is durably parked in the [OutboxPort] and the use case returns [Result.Ok]; the
 * `OutboxRunner` replays it (idempotently) when connectivity returns.
 */
class SetCrewBannerFocalUseCase(
    private val repository: CrewRepository,
    private val session: SessionProvider,
    private val connectivity: ConnectivityPort,
    private val outbox: OutboxPort,
) {
    suspend operator fun invoke(crewId: CrewId, focalY: Float): Result<Unit, CrewError> {
        val accountId = when (val s = session.requireCurrent()) {
            is Result.Ok -> s.value.accountId
            is Result.Err -> return Result.failure(s.error.toCrewError())
        }
        val clamped = focalY.coerceIn(0f, 1f)
        if (!connectivity.isOnline().first()) {
            repository.offlineOwnerGuard(crewId, accountId)?.let { return Result.failure(it) }
            return enqueue(crewId, accountId, clamped)
        }
        return when (val r = repository.setBannerFocalY(crewId, accountId, clamped)) {
            is Result.Ok -> r
            is Result.Err -> when (r.error) {
                CrewError.Backend.Network, CrewError.Backend.Unavailable ->
                    enqueue(crewId, accountId, clamped)
                else -> r
            }
        }
    }

    private suspend fun enqueue(
        crewId: CrewId,
        requestedBy: AccountId,
        focalY: Float,
    ): Result<Unit, CrewError> {
        outbox.enqueue(PendingCommand.SetCrewBannerFocalY(crewId, requestedBy, focalY))
        return Result.success(Unit)
    }
}
