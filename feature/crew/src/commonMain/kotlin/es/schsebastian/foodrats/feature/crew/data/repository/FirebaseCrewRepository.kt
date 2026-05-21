package es.schsebastian.foodrats.feature.crew.data.repository

import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.crew.data.firebase.AlreadyMemberException
import es.schsebastian.foodrats.feature.crew.data.firebase.CodeCollisionExhaustedException
import es.schsebastian.foodrats.feature.crew.data.firebase.CodeUnknownException
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewErrorMapper
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewFirestoreDataSource
import es.schsebastian.foodrats.feature.crew.data.firebase.FullException
import es.schsebastian.foodrats.feature.crew.data.firebase.NotFoundException
import es.schsebastian.foodrats.feature.crew.data.firebase.NotMemberException
import es.schsebastian.foodrats.feature.crew.data.firebase.toDomain
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
    private val firestore: CrewFirestoreDataSource,
    private val dispatchers: DispatcherProvider,
    private val errorMapper: CrewErrorMapper,
    private val clock: Clock,
) : CrewRepository {

    override suspend fun create(
        name: String,
        founder: AccountId,
        founderDisplayName: String,
    ): Result<Crew, CrewError> = withContext(dispatchers.io) {
        runCatching {
            firestore.createCrew(name, founder, founderDisplayName, clock.now().toEpochMilliseconds())
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
        joinerDisplayName: String,
    ): Result<Crew, CrewError> = withContext(dispatchers.io) {
        runCatching {
            firestore.joinByCode(code, joiner, joinerDisplayName, clock.now().toEpochMilliseconds())
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

    override suspend fun leave(crewId: CrewId, leaver: AccountId): Result<Unit, CrewError> =
        withContext(dispatchers.io) {
            runCatching { firestore.leave(crewId, leaver) }.fold(
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

    override fun observeMyCrews(accountId: AccountId): Flow<Result<List<Crew>, CrewError>> =
        firestore.observeMyCrews(accountId)
            .map<List<es.schsebastian.foodrats.feature.crew.data.firebase.CrewDto>, Result<List<Crew>, CrewError>> { dtos ->
                Result.success(dtos.mapNotNull { (it.toDomain() as? Result.Ok)?.value })
            }
            .catch { t -> emit(Result.failure(errorMapper.map(t))) }
            .flowOn(dispatchers.io)

    override fun observeCrew(crewId: CrewId): Flow<Result<Crew, CrewError>> =
        firestore.observeCrew(crewId)
            .map { dto ->
                if (dto == null) Result.failure(CrewError.Membership.NotFound)
                else dto.toDomain()
            }
            .catch { t -> emit(Result.failure(errorMapper.map(t))) }
            .flowOn(dispatchers.io)

    override suspend fun renameCrew(crewId: CrewId, requestedBy: AccountId, newName: String): Result<Unit, CrewError> {
        val crew = firestore.fetchOnce(crewId) ?: return Result.failure(CrewError.Membership.NotFound)
        if (crew.ownerId != requestedBy) return Result.failure(CrewError.Authorization.NotOwner)
        return firestore.renameCrew(crewId, newName)
    }

    override suspend fun deleteCrew(crewId: CrewId, requestedBy: AccountId): Result<Unit, CrewError> {
        val crew = firestore.fetchOnce(crewId) ?: return Result.failure(CrewError.Membership.NotFound)
        if (crew.ownerId != requestedBy) return Result.failure(CrewError.Authorization.NotOwner)
        return firestore.deleteCrew(crewId, crew.code)
    }
}
