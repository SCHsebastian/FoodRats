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
 * Removes a member from a crew. Enforces the product invariants in-domain before delegating the
 * write to the repository, which re-checks them atomically server-side:
 *  - only the crew **owner** may remove a member ([CrewError.RemoveMember.NotOwner]);
 *  - the owner **cannot** remove themselves — leaving is a separate flow ([CrewError.RemoveMember.CannotRemoveSelf]);
 *  - the target must actually be a member ([CrewError.RemoveMember.MemberNotFound]).
 *
 * The requester is the signed-in account from [SessionProvider]; the caller only supplies the crew
 * and the [target] to remove.
 *
 * OFFLINE-FIRST (P2 §0.5). The in-domain invariant checks run whenever the crew read model is
 * available (it survives offline via the P1 list cache). When the device is offline — or the crew
 * read fails offline, or the direct write fails with a connectivity-class error
 * ([CrewError.Backend.Network] / [CrewError.Backend.Unavailable]) — the removal is durably parked
 * in the [OutboxPort] and the use case returns [Result.Ok]; the `OutboxRunner` replays it
 * (idempotently — removing an absent member succeeds) when connectivity returns, where the
 * invariants are re-enforced atomically.
 */
class RemoveMemberUseCase(
    private val repository: CrewRepository,
    private val session: SessionProvider,
    private val connectivity: ConnectivityPort,
    private val outbox: OutboxPort,
) {
    suspend operator fun invoke(crewId: CrewId, target: AccountId): Result<Unit, CrewError> {
        val requestedBy = when (val s = session.requireCurrent()) {
            is Result.Ok -> s.value.accountId
            is Result.Err -> return Result.failure(s.error.toCrewError())
        }

        val online = connectivity.isOnline().first()

        when (val c = repository.observeCrew(crewId).first()) {
            is Result.Ok -> {
                val crew = c.value
                if (crew.ownerId != requestedBy) return Result.failure(CrewError.RemoveMember.NotOwner)
                if (target == requestedBy) return Result.failure(CrewError.RemoveMember.CannotRemoveSelf)
                if (crew.members.none { it.accountId == target }) {
                    return Result.failure(CrewError.RemoveMember.MemberNotFound)
                }
            }
            // Crew read unavailable: offline → queue (server re-validates on replay);
            // online → surface the read error exactly as before.
            is Result.Err -> if (online) return Result.failure(c.error)
        }

        if (!online) return enqueue(crewId, requestedBy, target)
        return when (val r = repository.removeMember(crewId, requestedBy, target)) {
            is Result.Ok -> r
            is Result.Err -> when (r.error) {
                CrewError.Backend.Network, CrewError.Backend.Unavailable ->
                    enqueue(crewId, requestedBy, target)
                else -> r
            }
        }
    }

    private suspend fun enqueue(
        crewId: CrewId,
        requestedBy: AccountId,
        target: AccountId,
    ): Result<Unit, CrewError> {
        return when (outbox.enqueue(PendingCommand.RemoveMember(crewId, requestedBy, target))) {
            is Result.Ok -> Result.success(Unit)
            is Result.Err -> Result.failure(CrewError.Backend.Unavailable)
        }
    }
}
