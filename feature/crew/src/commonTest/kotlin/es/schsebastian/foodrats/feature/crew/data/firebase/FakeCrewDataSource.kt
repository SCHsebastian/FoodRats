package es.schsebastian.foodrats.feature.crew.data.firebase

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Canonical behavioral fake for [CrewDataSource]. ONE fake per datasource — do not proliferate.
 *
 * Each method either returns a stubbed value/DTO or throws a stubbed [Throwable] (the same
 * `internal object` exceptions / arbitrary backend throwables the real Firestore datasource raises),
 * so the repository's runCatching → error-classification path is exercised exactly as in production.
 * Last-call arguments are captured to assert sequencing (e.g. fetchOnce-before-write on rename/delete).
 */
class FakeCrewDataSource : CrewDataSource {

    // ---- stubs: set the value to return, or the throwable to throw ----
    var createResult: CrewDto? = null
    var createThrows: Throwable? = null

    var joinResult: CrewDto? = null
    var joinThrows: Throwable? = null

    var leaveThrows: Throwable? = null

    var removeMemberThrows: Throwable? = null

    var observeMyCrewsFlow: Flow<List<CrewDto>> = flowOf(emptyList())
    var observeCrewFlow: Flow<CrewDto?> = flowOf(null)

    /** Single-shot read used by rename/delete; null = NotFound. */
    var fetchOnceResult: Crew? = null

    /** Single-shot resolve-by-code (accept-invite preview); set [fetchByCodeThrows] to fail. */
    var fetchByCodeResult: Crew? = null
    var fetchByCodeThrows: Throwable? = null
    var lastFetchByCode: CrewCode? = null

    var renameResult: Result<Unit, CrewError> = Result.success(Unit)
    var deleteResult: Result<Unit, CrewError> = Result.success(Unit)
    var setBlindVotingResult: Result<Unit, CrewError> = Result.success(Unit)

    // ---- call captures ----
    var lastCreate: CreateCall? = null
    var lastJoin: JoinCall? = null
    var lastLeave: Pair<CrewId, AccountId>? = null
    var lastRemoveMember: Pair<CrewId, AccountId>? = null
    var lastFetchOnce: CrewId? = null
    var lastRename: Pair<CrewId, String>? = null
    var lastDelete: Pair<CrewId, CrewCode>? = null
    var lastSetBlindVoting: Pair<CrewId, Boolean>? = null

    data class CreateCall(val name: String, val founder: AccountId, val nowMs: Long)
    data class JoinCall(val code: CrewCode, val joiner: AccountId, val nowMs: Long)

    override suspend fun createCrew(
        name: String,
        founder: AccountId,
        nowMs: Long,
    ): CrewDto {
        lastCreate = CreateCall(name, founder, nowMs)
        createThrows?.let { throw it }
        return createResult ?: error("createResult not stubbed")
    }

    override suspend fun joinByCode(
        code: CrewCode,
        joiner: AccountId,
        nowMs: Long,
    ): CrewDto {
        lastJoin = JoinCall(code, joiner, nowMs)
        joinThrows?.let { throw it }
        return joinResult ?: error("joinResult not stubbed")
    }

    override suspend fun leave(crewId: CrewId, leaver: AccountId) {
        lastLeave = crewId to leaver
        leaveThrows?.let { throw it }
    }

    override suspend fun removeMember(crewId: CrewId, target: AccountId) {
        lastRemoveMember = crewId to target
        removeMemberThrows?.let { throw it }
    }

    override fun observeMyCrews(accountId: AccountId): Flow<List<CrewDto>> = observeMyCrewsFlow

    override fun observeCrew(crewId: CrewId): Flow<CrewDto?> = observeCrewFlow

    override suspend fun fetchOnce(crewId: CrewId): Crew? {
        lastFetchOnce = crewId
        return fetchOnceResult
    }

    override suspend fun fetchByCode(code: CrewCode): Crew {
        lastFetchByCode = code
        fetchByCodeThrows?.let { throw it }
        return fetchByCodeResult ?: error("fetchByCodeResult not stubbed")
    }

    override suspend fun renameCrew(crewId: CrewId, newName: String): Result<Unit, CrewError> {
        lastRename = crewId to newName
        return renameResult
    }

    override suspend fun deleteCrew(crewId: CrewId, code: CrewCode): Result<Unit, CrewError> {
        lastDelete = crewId to code
        return deleteResult
    }

    override suspend fun setBlindVoting(crewId: CrewId, enabled: Boolean): Result<Unit, CrewError> {
        lastSetBlindVoting = crewId to enabled
        return setBlindVotingResult
    }
}
