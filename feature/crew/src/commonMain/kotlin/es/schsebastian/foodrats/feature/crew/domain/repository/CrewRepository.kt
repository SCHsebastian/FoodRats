package es.schsebastian.foodrats.feature.crew.domain.repository

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import kotlinx.coroutines.flow.Flow

interface CrewRepository {

    /** Creates a new Crew with the calling user as the sole founding member. */
    suspend fun create(name: String, founder: AccountId): Result<Crew, CrewError>

    /** Joins an existing Crew by invite code. Transactional under contention. */
    suspend fun joinByCode(code: CrewCode, joiner: AccountId): Result<Crew, CrewError>

    /**
     * Resolves a Crew by its invite [code] WITHOUT joining — for the accept-invite preview (the
     * recipient has only the code from the link). Read-only. Returns [CrewError.Invite.CodeUnknown]
     * for an unknown code, [CrewError.Membership.NotFound] if the crew is gone.
     */
    suspend fun findByCode(code: CrewCode): Result<Crew, CrewError>

    /** Leaves a Crew. Hard-deletes the Crew + invite code if `leaver` was the last member. */
    suspend fun leave(crewId: CrewId, leaver: AccountId): Result<Unit, CrewError>

    /** Streams the crews this account is a member of. */
    fun observeMyCrews(accountId: AccountId): Flow<Result<List<Crew>, CrewError>>

    /** Streams a single crew (for the settings screen). */
    fun observeCrew(crewId: CrewId): Flow<Result<Crew, CrewError>>

    /** Renames a Crew. Only the owner may rename. */
    suspend fun renameCrew(crewId: CrewId, requestedBy: AccountId, newName: String): Result<Unit, CrewError>

    /** Deletes a Crew entirely. Only the owner may delete. */
    suspend fun deleteCrew(crewId: CrewId, requestedBy: AccountId): Result<Unit, CrewError>

    /** Toggles the crew's blind-voting policy. Only the owner may change it. */
    suspend fun setBlindVoting(crewId: CrewId, requestedBy: AccountId, enabled: Boolean): Result<Unit, CrewError>

    /**
     * Removes [target] from the crew. Only the owner ([requestedBy]) may remove a member, and the
     * owner cannot remove themselves (leaving is a separate flow). Authorization, self-removal, and
     * membership invariants are enforced atomically server-side by the implementation (mirrored
     * in-domain by [es.schsebastian.foodrats.feature.crew.domain.usecase.RemoveMemberUseCase]).
     */
    suspend fun removeMember(crewId: CrewId, requestedBy: AccountId, target: AccountId): Result<Unit, CrewError>
}
