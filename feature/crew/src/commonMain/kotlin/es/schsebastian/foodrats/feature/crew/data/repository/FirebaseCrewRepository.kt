package es.schsebastian.foodrats.feature.crew.data.repository

import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.crew.data.firebase.AlreadyMemberException
import es.schsebastian.foodrats.feature.crew.data.firebase.CodeCollisionExhaustedException
import es.schsebastian.foodrats.feature.crew.data.firebase.CodeUnknownException
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewDataSource
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewErrorMapper
import es.schsebastian.foodrats.feature.crew.data.firebase.FullException
import es.schsebastian.foodrats.feature.crew.data.firebase.NotFoundException
import es.schsebastian.foodrats.feature.crew.data.firebase.NotMemberException
import es.schsebastian.foodrats.feature.crew.data.firebase.toDomain
import es.schsebastian.foodrats.feature.crew.data.local.CrewLocalStore
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class FirebaseCrewRepository(
    private val dataSource: CrewDataSource,
    private val dispatchers: DispatcherProvider,
    private val errorMapper: CrewErrorMapper,
    private val clock: Clock,
    // Offline-first read source-of-truth (P3b §P3b-T7): the crew picker observes this local
    // SQLDelight store; the CrewSyncEngine keeps it fresh off the Firestore listener.
    private val local: CrewLocalStore,
) : CrewRepository {

    override suspend fun create(
        name: String,
        founder: AccountId,
    ): Result<Crew, CrewError> = withContext(dispatchers.io) {
        runCatching {
            dataSource.createCrew(name, founder, clock.now().toEpochMilliseconds())
                .toDomain()
        }.fold(
            onSuccess = { it },
            onFailure = { t ->
                Result.failure(
                    when (t) {
                        CodeCollisionExhaustedException -> CrewError.Create.CodeCollisionRetriesExhausted
                        else -> errorMapper.map(t)
                    },
                )
            },
        )
    }

    override suspend fun joinByCode(
        code: CrewCode,
        joiner: AccountId,
    ): Result<Crew, CrewError> = withContext(dispatchers.io) {
        runCatching {
            dataSource.joinByCode(code, joiner, clock.now().toEpochMilliseconds())
                .toDomain()
        }.fold(
            onSuccess = { it },
            onFailure = { t ->
                Result.failure(
                    when (t) {
                        CodeUnknownException   -> CrewError.Invite.CodeUnknown
                        NotFoundException      -> CrewError.Membership.NotFound
                        FullException          -> CrewError.Membership.Full
                        AlreadyMemberException -> CrewError.Membership.AlreadyMember
                        else                   -> errorMapper.map(t)
                    },
                )
            },
        )
    }

    override suspend fun findByCode(code: CrewCode): Result<Crew, CrewError> =
        withContext(dispatchers.io) {
            runCatching { dataSource.fetchByCode(code) }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { t ->
                    Result.failure(
                        when (t) {
                            CodeUnknownException -> CrewError.Invite.CodeUnknown
                            NotFoundException    -> CrewError.Membership.NotFound
                            else                 -> errorMapper.map(t)
                        },
                    )
                },
            )
        }

    override suspend fun leave(crewId: CrewId, leaver: AccountId): Result<Unit, CrewError> =
        withContext(dispatchers.io) {
            runCatching { dataSource.leave(crewId, leaver) }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { t ->
                    Result.failure(
                        when (t) {
                            NotFoundException -> CrewError.Membership.NotFound
                            NotMemberException -> CrewError.Membership.NotMember
                            else -> errorMapper.map(t)
                        },
                    )
                },
            )
        }

    // Offline-first read source-of-truth (P3b §P3b-T7): the crew picker reads the local SQLDelight
    // store (CrewLocalStore.observeMyCrews) — NOT Firestore. The CrewSyncEngine is the only consumer
    // of the Firestore crew-list listener and mirrors each snapshot in; this stream reads it back.
    // Rebuilt DTOs go through the SAME CrewMapper.toDomain so the offline list is identical to the
    // online one. The `.catch` is defensive — the local read shouldn't throw, but a benign-empty
    // failure keeps the picker rendering.
    override fun observeMyCrews(accountId: AccountId): Flow<Result<List<Crew>, CrewError>> =
        local.observeMyCrews()
            .map<List<es.schsebastian.foodrats.feature.crew.data.firebase.CrewDto>, Result<List<Crew>, CrewError>> { dtos ->
                Result.success(dtos.mapNotNull { (it.toDomain() as? Result.Ok)?.value })
            }
            .catch { t -> emit(Result.failure(errorMapper.map(t))) }
            .flowOn(dispatchers.io)

    override fun observeCrew(crewId: CrewId): Flow<Result<Crew, CrewError>> =
        dataSource.observeCrew(crewId)
            .map { dto ->
                if (dto == null) Result.failure(CrewError.Membership.NotFound)
                else dto.toDomain()
            }
            .catch { t -> emit(Result.failure(errorMapper.map(t))) }
            .flowOn(dispatchers.io)

    override suspend fun renameCrew(crewId: CrewId, requestedBy: AccountId, newName: String): Result<Unit, CrewError> {
        val crew = dataSource.fetchOnce(crewId) ?: return Result.failure(CrewError.Membership.NotFound)
        if (crew.ownerId != requestedBy) return Result.failure(CrewError.Authorization.NotOwner)
        return dataSource.renameCrew(crewId, newName)
    }

    override suspend fun deleteCrew(crewId: CrewId, requestedBy: AccountId): Result<Unit, CrewError> {
        val crew = dataSource.fetchOnce(crewId) ?: return Result.failure(CrewError.Membership.NotFound)
        if (crew.ownerId != requestedBy) return Result.failure(CrewError.Authorization.NotOwner)
        return dataSource.deleteCrew(crewId, crew.code)
    }

    override suspend fun setBlindVoting(
        crewId: CrewId,
        requestedBy: AccountId,
        enabled: Boolean,
    ): Result<Unit, CrewError> {
        val crew = dataSource.fetchOnce(crewId) ?: return Result.failure(CrewError.Membership.NotFound)
        if (crew.ownerId != requestedBy) return Result.failure(CrewError.Authorization.NotOwner)
        return dataSource.setBlindVoting(crewId, enabled)
    }

    override suspend fun setTagline(
        crewId: CrewId,
        requestedBy: AccountId,
        tagline: String?,
    ): Result<Unit, CrewError> {
        val crew = dataSource.fetchOnce(crewId) ?: return Result.failure(CrewError.Membership.NotFound)
        if (crew.ownerId != requestedBy) return Result.failure(CrewError.Authorization.NotOwner)
        return dataSource.setTagline(crewId, tagline)
    }

    override suspend fun removeMember(
        crewId: CrewId,
        requestedBy: AccountId,
        target: AccountId,
    ): Result<Unit, CrewError> {
        val crew = dataSource.fetchOnce(crewId) ?: return Result.failure(CrewError.Membership.NotFound)
        if (crew.ownerId != requestedBy) return Result.failure(CrewError.RemoveMember.NotOwner)
        if (target == requestedBy) return Result.failure(CrewError.RemoveMember.CannotRemoveSelf)
        if (crew.members.none { it.accountId == target }) {
            return Result.failure(CrewError.RemoveMember.MemberNotFound)
        }
        return runCatching { dataSource.removeMember(crewId, target) }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { t ->
                Result.failure(
                    when (t) {
                        NotFoundException -> CrewError.Membership.NotFound
                        // TOCTOU: target vanished between the read above and the atomic write.
                        NotMemberException -> CrewError.RemoveMember.MemberNotFound
                        else -> errorMapper.map(t)
                    },
                )
            },
        )
    }
}
