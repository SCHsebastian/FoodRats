package es.schsebastian.foodrats.feature.crew.domain.model

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import kotlin.time.Instant

/**
 * The Crew aggregate root. The invariant this context exists to protect is the
 * 3..8 membership cap ([CrewSize]) — enforced here in-memory by [addMember], and
 * atomically server-side by the join transaction in `CrewFirestoreDataSource`.
 * Both reference [CrewSize.MAX] so the cap has a single source of truth.
 */
data class Crew(
    val id: CrewId,
    val name: String,
    val code: CrewCode,
    val ownerId: AccountId,
    val createdAt: Instant,
    val members: List<Member>,
    /**
     * When `true`, a meal's author identity is hidden from a crewmate until that
     * crewmate has cast their own [es.schsebastian.foodrats.core.domain.meal.Score]
     * — so members aren't anchored by who cooked it. An owner-settable crew policy;
     * defaults to `false` so existing crews keep current (un-blind) behavior.
     * The masking rule itself is
     * [es.schsebastian.foodrats.core.domain.crew.BlindVotingPolicy]; feed reads this
     * flag through [es.schsebastian.foodrats.core.domain.crew.CrewBlindVotingPort].
     */
    val blindVoting: Boolean = false,
) {
    val size: Int get() = members.size

    /**
     * Adds [member] to the crew, enforcing the membership cap and uniqueness.
     * Returns [CrewError.Membership.AlreadyMember] if the account is already a member,
     * or [CrewError.Membership.Full] if the crew is at [CrewSize.MAX]. The atomic
     * authoritative check lives in the join transaction; this is the in-memory model
     * used by domain logic and tests.
     */
    fun addMember(member: Member): Result<Crew, CrewError> {
        if (members.any { it.accountId == member.accountId }) {
            return Result.failure(CrewError.Membership.AlreadyMember)
        }
        if (!CrewSize.canAdd(size)) {
            return Result.failure(CrewError.Membership.Full)
        }
        return Result.success(copy(members = members + member))
    }

    companion object {
        /** Constructs a Crew from already-validated parts (e.g. when reconstituting from storage). */
        fun of(
            id: CrewId,
            name: String,
            code: CrewCode,
            ownerId: AccountId,
            createdAt: Instant,
            members: List<Member>,
            blindVoting: Boolean = false,
        ): Crew = Crew(id, name, code, ownerId, createdAt, members, blindVoting)
    }
}
