package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
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
 */
class RemoveMemberUseCase(
    private val repository: CrewRepository,
    private val session: SessionProvider,
) {
    suspend operator fun invoke(crewId: CrewId, target: AccountId): Result<Unit, CrewError> {
        val requestedBy = when (val s = session.requireCurrent()) {
            is Result.Ok -> s.value.accountId
            is Result.Err -> return Result.failure(CrewError.Backend.Unavailable)
        }

        val crew = when (val c = repository.observeCrew(crewId).first()) {
            is Result.Ok -> c.value
            is Result.Err -> return Result.failure(c.error)
        }

        if (crew.ownerId != requestedBy) return Result.failure(CrewError.RemoveMember.NotOwner)
        if (target == requestedBy) return Result.failure(CrewError.RemoveMember.CannotRemoveSelf)
        if (crew.members.none { it.accountId == target }) {
            return Result.failure(CrewError.RemoveMember.MemberNotFound)
        }

        return repository.removeMember(crewId, requestedBy, target)
    }
}
