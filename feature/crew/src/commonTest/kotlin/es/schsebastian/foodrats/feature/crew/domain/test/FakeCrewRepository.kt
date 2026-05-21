package es.schsebastian.foodrats.feature.crew.domain.test

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeCrewRepository(
    initial: List<Crew> = emptyList(),
) : CrewRepository {
    val crews = MutableStateFlow(initial)
    var nextCreate: Result<Crew, CrewError>? = null
    var nextJoin: Result<Crew, CrewError>? = null
    var nextLeave: Result<Unit, CrewError>? = null
    /** When set, overrides the default rename behavior (ownership check + mutation). */
    var nextRename: Result<Unit, CrewError>? = null
    /** When set, overrides the default delete behavior (ownership check + mutation). */
    var nextDelete: Result<Unit, CrewError>? = null

    var lastRename: Pair<CrewId, String>? = null
    var lastMemberRename: Triple<CrewId, AccountId, String>? = null
    var lastDelete: CrewId? = null

    override suspend fun create(
        name: String,
        founder: AccountId,
        founderDisplayName: String,
    ): Result<Crew, CrewError> {
        val r = nextCreate ?: error("nextCreate not stubbed")
        if (r is Result.Ok) crews.value = crews.value + r.value
        return r
    }

    override suspend fun joinByCode(
        code: CrewCode,
        joiner: AccountId,
        joinerDisplayName: String,
    ): Result<Crew, CrewError> {
        val r = nextJoin ?: error("nextJoin not stubbed")
        if (r is Result.Ok) crews.value = crews.value + r.value
        return r
    }

    override suspend fun leave(crewId: CrewId, leaver: AccountId): Result<Unit, CrewError> {
        val r = nextLeave ?: error("nextLeave not stubbed")
        if (r is Result.Ok) crews.value = crews.value.filterNot { it.id == crewId }
        return r
    }

    override fun observeMyCrews(accountId: AccountId): Flow<Result<List<Crew>, CrewError>> =
        crews.map { list -> Result.success(list.filter { c -> c.members.any { it.accountId == accountId } }) }

    override fun observeCrew(crewId: CrewId): Flow<Result<Crew, CrewError>> =
        crews.map { list ->
            list.firstOrNull { it.id == crewId }?.let { Result.success(it) }
                ?: Result.failure(CrewError.Membership.NotFound)
        }

    override suspend fun renameCrew(
        crewId: CrewId,
        requestedBy: AccountId,
        newName: String,
    ): Result<Unit, CrewError> {
        nextRename?.let { return it }
        val crew = crews.value.firstOrNull { it.id == crewId }
            ?: return Result.failure(CrewError.Membership.NotFound)
        if (requestedBy != crew.ownerId) return Result.failure(CrewError.Authorization.NotOwner)
        lastRename = Pair(crewId, newName)
        crews.value = crews.value.map { if (it.id == crewId) it.copy(name = newName) else it }
        return Result.success(Unit)
    }

    override suspend fun deleteCrew(
        crewId: CrewId,
        requestedBy: AccountId,
    ): Result<Unit, CrewError> {
        nextDelete?.let { return it }
        val crew = crews.value.firstOrNull { it.id == crewId }
            ?: return Result.failure(CrewError.Membership.NotFound)
        if (requestedBy != crew.ownerId) return Result.failure(CrewError.Authorization.NotOwner)
        lastDelete = crewId
        crews.value = crews.value.filterNot { it.id == crewId }
        return Result.success(Unit)
    }

}

/** Helper: create AccountId without going through validation (tests only). */
fun aid(raw: String): AccountId = (AccountId.of(raw) as Result.Ok).value
/** Helper: create CrewId without going through validation (tests only). */
fun cid(raw: String): CrewId = (CrewId.of(raw) as Result.Ok).value
