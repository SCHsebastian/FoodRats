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
    suspend fun create(name: String, founder: AccountId, founderDisplayName: String): Result<Crew, CrewError>

    /** Joins an existing Crew by invite code. Transactional under contention. */
    suspend fun joinByCode(code: CrewCode, joiner: AccountId, joinerDisplayName: String): Result<Crew, CrewError>

    /** Leaves a Crew. Hard-deletes the Crew + invite code if `leaver` was the last member. */
    suspend fun leave(crewId: CrewId, leaver: AccountId): Result<Unit, CrewError>

    /** Streams the crews this account is a member of. */
    fun observeMyCrews(accountId: AccountId): Flow<Result<List<Crew>, CrewError>>

    /** Streams a single crew (for the settings screen). */
    fun observeCrew(crewId: CrewId): Flow<Result<Crew, CrewError>>

    /** Renames a Crew. Only the owner may rename. */
    suspend fun renameCrew(crewId: CrewId, requestedBy: AccountId, newName: String): Result<Unit, CrewError>

    /** Renames a member's display name within a Crew. */
    suspend fun renameMember(crewId: CrewId, accountId: AccountId, newDisplayName: String): Result<Unit, CrewError>

    /** Deletes a Crew entirely. Only the owner may delete. */
    suspend fun deleteCrew(crewId: CrewId, requestedBy: AccountId): Result<Unit, CrewError>
}
