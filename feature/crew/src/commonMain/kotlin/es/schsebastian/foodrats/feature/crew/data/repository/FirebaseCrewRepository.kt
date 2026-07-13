package es.schsebastian.foodrats.feature.crew.data.repository

import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.crew.CrewScoreStyle
import es.schsebastian.foodrats.feature.crew.data.firebase.toDto
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.crew.data.firebase.AlreadyMemberException
import es.schsebastian.foodrats.feature.crew.data.firebase.AlreadyRequestedException
import es.schsebastian.foodrats.feature.crew.data.firebase.CodeCollisionExhaustedException
import es.schsebastian.foodrats.feature.crew.data.firebase.BannerImageTooLargeException
import es.schsebastian.foodrats.feature.crew.data.firebase.BannerImageUnreadableException
import es.schsebastian.foodrats.feature.crew.data.firebase.CodeUnknownException
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewBannerStorage
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
import es.schsebastian.foodrats.feature.crew.domain.model.JoinRequest
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
    // C9 — crew banner Storage adapter (upload/delete). Null in tests that don't exercise banner.
    private val bannerStorage: CrewBannerStorage? = null,
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

    override suspend fun requestToJoinByCode(
        code: CrewCode,
        requester: AccountId,
    ): Result<Unit, CrewError> = withContext(dispatchers.io) {
        runCatching { dataSource.requestToJoin(code, requester, clock.now().toEpochMilliseconds()) }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { t ->
                Result.failure(
                    when (t) {
                        CodeUnknownException      -> CrewError.Invite.CodeUnknown
                        NotFoundException         -> CrewError.Membership.NotFound
                        AlreadyMemberException    -> CrewError.Membership.AlreadyMember
                        AlreadyRequestedException -> CrewError.Invite.AlreadyRequested
                        else                      -> errorMapper.map(t)
                    },
                )
            },
        )
    }

    override fun observeJoinRequests(crewId: CrewId): Flow<Result<List<JoinRequest>, CrewError>> =
        dataSource.observeJoinRequests(crewId)
            .map<List<es.schsebastian.foodrats.feature.crew.data.firebase.JoinRequestDto>, Result<List<JoinRequest>, CrewError>> { dtos ->
                Result.success(dtos.mapNotNull { it.toDomain() })
            }
            .catch { t -> emit(Result.failure(errorMapper.map(t))) }
            .flowOn(dispatchers.io)

    override suspend fun approveJoinRequest(
        crewId: CrewId,
        requestedBy: AccountId,
        requester: AccountId,
    ): Result<Unit, CrewError> {
        val crew = dataSource.fetchOnce(crewId) ?: return Result.failure(CrewError.Membership.NotFound)
        if (crew.ownerId != requestedBy) return Result.failure(CrewError.Authorization.NotOwner)
        return runCatching {
            dataSource.approveJoinRequest(crewId, requester, clock.now().toEpochMilliseconds())
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { t ->
                Result.failure(
                    when (t) {
                        NotFoundException -> CrewError.Membership.NotFound
                        FullException     -> CrewError.Membership.Full
                        else              -> errorMapper.map(t)
                    },
                )
            },
        )
    }

    override suspend fun declineJoinRequest(
        crewId: CrewId,
        requestedBy: AccountId,
        requester: AccountId,
    ): Result<Unit, CrewError> {
        val crew = dataSource.fetchOnce(crewId) ?: return Result.failure(CrewError.Membership.NotFound)
        if (crew.ownerId != requestedBy) return Result.failure(CrewError.Authorization.NotOwner)
        return dataSource.declineJoinRequest(crewId, requester)
    }

    override suspend fun cancelJoinRequest(
        crewId: CrewId,
        requester: AccountId,
    ): Result<Unit, CrewError> =
        // No owner/fetchOnce gate here: the requester deletes their OWN request doc, which the
        // Firestore rule permits. The datasource op is the same single-doc delete as decline.
        dataSource.declineJoinRequest(crewId, requester)

    override suspend fun transferOwnership(
        crewId: CrewId,
        requestedBy: AccountId,
        newOwner: AccountId,
    ): Result<Unit, CrewError> {
        val crew = dataSource.fetchOnce(crewId) ?: return Result.failure(CrewError.Membership.NotFound)
        if (crew.ownerId != requestedBy) return Result.failure(CrewError.Transfer.NotOwner)
        // A self-transfer is a silent no-op (re-writing ownerId to the same value "succeeds"); reject
        // it explicitly so the UI can say "you're already the owner" instead of showing a fake success.
        if (newOwner == requestedBy) return Result.failure(CrewError.Transfer.CannotTransferToSelf)
        if (crew.members.none { it.accountId == newOwner }) return Result.failure(CrewError.Transfer.TargetNotMember)
        return dataSource.transferOwnership(crewId, newOwner)
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

    override suspend fun leave(crewId: CrewId, leaver: AccountId, successor: AccountId?): Result<Unit, CrewError> =
        withContext(dispatchers.io) {
            runCatching { dataSource.leave(crewId, leaver, successor) }.fold(
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

    override suspend fun setWelcomeMessage(
        crewId: CrewId,
        requestedBy: AccountId,
        message: String?,
    ): Result<Unit, CrewError> {
        val crew = dataSource.fetchOnce(crewId) ?: return Result.failure(CrewError.Membership.NotFound)
        if (crew.ownerId != requestedBy) return Result.failure(CrewError.Authorization.NotOwner)
        return dataSource.setWelcomeMessage(crewId, message)
    }

    override suspend fun setWeeklyChallenge(
        crewId: CrewId,
        requestedBy: AccountId,
        challenge: String?,
        setAtMillis: Long?,
    ): Result<Unit, CrewError> {
        val crew = dataSource.fetchOnce(crewId) ?: return Result.failure(CrewError.Membership.NotFound)
        if (crew.ownerId != requestedBy) return Result.failure(CrewError.Authorization.NotOwner)
        return dataSource.setWeeklyChallenge(crewId, challenge, setAtMillis)
    }

    override suspend fun setScoreStyle(
        crewId: CrewId,
        requestedBy: AccountId,
        style: CrewScoreStyle,
    ): Result<Unit, CrewError> {
        val crew = dataSource.fetchOnce(crewId) ?: return Result.failure(CrewError.Membership.NotFound)
        if (crew.ownerId != requestedBy) return Result.failure(CrewError.Authorization.NotOwner)
        return dataSource.setScoreStyle(crewId, style.toDto())
    }

    override suspend fun setBannerFocalY(
        crewId: CrewId,
        requestedBy: AccountId,
        focalY: Float,
    ): Result<Unit, CrewError> {
        val crew = dataSource.fetchOnce(crewId) ?: return Result.failure(CrewError.Membership.NotFound)
        if (crew.ownerId != requestedBy) return Result.failure(CrewError.Authorization.NotOwner)
        return dataSource.setBannerFocalY(crewId, focalY.coerceIn(0f, 1f))
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

    // C9 — crew banner ————————————————————————————————————————————————————————————————————————————

    // NOTE: no repo-level `withContext(dispatchers.io)` here. `setBannerPath`/`clearBannerPath`
    // already own their IO boundary in the datasource (like every other settings write), so wrapping
    // them again would NEST `withContext(io)` inside `withContext(io)`. The Storage upload/delete —
    // which do NOT own a boundary — get their own `withContext(io)` individually. Sequential
    // boundaries (fetchOnce → storage → datasource), never nested, matching `renameCrew` et al.
    override suspend fun setBanner(
        crewId: CrewId,
        requestedBy: AccountId,
        bytes: ByteArray,
    ): Result<Unit, CrewError> {
        val crew = dataSource.fetchOnce(crewId) ?: return Result.failure(CrewError.Membership.NotFound)
        if (crew.ownerId != requestedBy) return Result.failure(CrewError.Authorization.NotOwner)
        val storage = bannerStorage ?: return Result.failure(CrewError.Banner.UploadFailed)
        // The banner PATH is content-versioned (`crew_banners/{crewId}/{token}.jpg`), so a NEW image
        // produces a NEW path; we reclaim the PREVIOUS object after the pointer write lands (the old
        // fixed path overwrote in place and needed no reclaim). Read the prior path before uploading.
        val previousPath = crew.bannerPath
        val path = try {
            withContext(dispatchers.io) { storage.upload(crewId, bytes) }
        } catch (t: Throwable) {
            return Result.failure(
                when (t) {
                    // Typed compression outcomes from the datasource — the image itself is the problem.
                    BannerImageUnreadableException -> CrewError.Banner.ImageUnreadable
                    BannerImageTooLargeException -> CrewError.Banner.ImageTooLarge
                    // Everything else classifies like every other backend throwable (toCrewFault via
                    // the mapper); only the unclassifiable bucket keeps the banner-specific message.
                    else -> when (val mapped = errorMapper.map(t)) {
                        CrewError.Backend.Unknown -> CrewError.Banner.UploadFailed
                        else -> mapped
                    }
                },
            )
        }
        // The token is the versioned object's filename stem: crew_banners/{crewId}/{token}.jpg.
        val token = path.substringAfterLast('/').substringBeforeLast('.')
        return when (val written = dataSource.setBannerPath(crewId, path, token)) {
            is Result.Ok -> {
                // Best-effort: reclaim the previous version's object so old banners don't accumulate.
                // Skipped when unchanged (same image → identical token → identical path) or absent. A
                // failure here must not fail the update (leaves a harmless orphan, never a dangle).
                if (previousPath != null && previousPath != path) {
                    runCatching { withContext(dispatchers.io) { storage.delete(previousPath) } }
                }
                written
            }
            is Result.Err -> {
                // The object landed but the Firestore pointer write was rejected (e.g. a rules
                // denial). Best-effort orphan cleanup of the JUST-uploaded object, then surface the
                // REAL cause rather than masking every post-upload failure as the generic UploadFailed.
                runCatching { withContext(dispatchers.io) { storage.delete(path) } }
                written
            }
        }
    }

    override suspend fun removeBanner(
        crewId: CrewId,
        requestedBy: AccountId,
    ): Result<Unit, CrewError> {
        val crew = dataSource.fetchOnce(crewId) ?: return Result.failure(CrewError.Membership.NotFound)
        if (crew.ownerId != requestedBy) return Result.failure(CrewError.Authorization.NotOwner)
        val storage = bannerStorage ?: return Result.failure(CrewError.Banner.DeleteFailed)
        // The object to reclaim is whatever the pointer currently names (versioned or legacy fixed).
        val currentPath = crew.bannerPath
        // Clear the Firestore pointer FIRST — it is the source of truth for "is there a banner". A
        // transient Storage delete error must NOT strand a dangling pointer (the old order deleted
        // Storage first, so a non-NOT_FOUND Storage error left `bannerPath` pointing at an object the
        // owner tried to remove, with no retry path). Then best-effort delete the now-orphaned object
        // (NOT_FOUND-tolerant; a leftover Storage object is harmless).
        return when (val cleared = dataSource.clearBannerPath(crewId)) {
            is Result.Ok -> {
                if (currentPath != null) {
                    runCatching { withContext(dispatchers.io) { storage.delete(currentPath) } }
                }
                cleared
            }
            // Surface the real cause (permission / unknown) instead of the generic DeleteFailed.
            is Result.Err -> cleared
        }
    }
}
