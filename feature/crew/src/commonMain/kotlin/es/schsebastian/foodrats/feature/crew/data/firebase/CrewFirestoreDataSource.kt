package es.schsebastian.foodrats.feature.crew.data.firebase

import dev.gitlive.firebase.firestore.FirebaseFirestore
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.model.CrewSize
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class CrewFirestoreDataSource(
    private val firestore: FirebaseFirestore,
    private val codeGenerator: CrewCodeGenerator,
    private val dispatchers: DispatcherProvider,
    private val errorMapper: CrewErrorMapper,
) : CrewDataSource {

    // CoroutineExceptionHandler is mandatory here: any uncaught exception inside the
    // `shareIn` upstream (e.g. Firestore PERMISSION_DENIED after sign-out token revoke)
    // would otherwise propagate to Kotlin/Native's default handler → SIGABRT. The handler
    // swallows + logs; consumers downstream of `observeCrew` already receive `null` via
    // the upstream `.catch { emit(null) }` and map that to CrewError.Membership.NotFound.
    private val obsScope = CoroutineScope(
        SupervisorJob() +
            dispatchers.default +
            CoroutineExceptionHandler { _, t ->
                FrLog.w("Crew", t) { "obsScope uncaught: ${t.message}" }
            },
    )
    private val crewSharedFlowsLock = Mutex()
    private val crewSharedFlows = mutableMapOf<String, SharedFlow<CrewDto?>>()

    private val crewsCol get() = firestore.collection("crews")
    private val codesCol get() = firestore.collection("crewCodes")

    /**
     * Creates a crew + claims its invite code in one transaction. Retries up to
     * [MAX_CODE_ATTEMPTS] times on code collision.
     *
     * GitLive API note: `firestore.runTransaction` takes a suspend lambda with `Transaction`
     * as receiver (not a parameter). `tx.get(ref)` is a suspend call inside the lambda.
     * `tx.set(ref, dto)` / `tx.delete(ref)` are synchronous.
     */
    override suspend fun createCrew(
        name: String,
        founder: AccountId,
        nowMs: Long,
    ): CrewDto {
        repeat(MAX_CODE_ATTEMPTS) {
            val code = codeGenerator.generate()
            val newCrewRef = crewsCol.document
            val crewId = newCrewRef.id
            val codeRef = codesCol.document(code)
            try {
                val crewDto = CrewDto(
                    id = crewId,
                    name = name,
                    code = code,
                    ownerId = founder.value,
                    createdAtEpochMs = nowMs,
                    memberIds = listOf(founder.value),
                    members = mapOf(
                        founder.value to MemberDto(joinedAtEpochMs = nowMs),
                    ),
                )
                firestore.runTransaction {
                    val existing = get(codeRef)
                    if (existing.exists) throw CodeTakenException
                    set(newCrewRef, crewDto)
                    set(codeRef, CrewCodeDto(crewId = crewId, createdAtEpochMs = nowMs))
                }
                return crewDto
            } catch (e: CodeTakenException) {
                // try again with a fresh code
            }
        }
        throw CodeCollisionExhaustedException
    }

    override suspend fun joinByCode(
        code: CrewCode,
        joiner: AccountId,
        nowMs: Long,
    ): CrewDto {
        val codeRef = codesCol.document(code.value)
        return firestore.runTransaction {
            val codeSnap = get(codeRef)
            if (!codeSnap.exists) throw CodeUnknownException
            val codeDto = codeSnap.data<CrewCodeDto>()
            val crewId = codeDto.crewId ?: throw NotFoundException
            val crewRef = crewsCol.document(crewId)
            val crewSnap = get(crewRef)
            if (!crewSnap.exists) throw NotFoundException
            val crew = crewSnap.data<CrewDto>()
            if (joiner.value in crew.memberIds) throw AlreadyMemberException
            // Authoritative, atomic cap check — must stay INSIDE the transaction to avoid a
            // TOCTOU race. References CrewSize.MAX so the 3..8 invariant has one source of truth.
            if (!CrewSize.canAdd(crew.memberIds.size)) throw FullException
            val updatedMemberIds = crew.memberIds + joiner.value
            val updatedMembers = crew.members + (joiner.value to MemberDto(joinedAtEpochMs = nowMs))
            val updated = crew.copy(memberIds = updatedMemberIds, members = updatedMembers)
            set(crewRef, updated)
            updated
        }
    }

    override suspend fun leave(crewId: CrewId, leaver: AccountId) {
        val crewRef = crewsCol.document(crewId.value)
        firestore.runTransaction {
            val crewSnap = get(crewRef)
            if (!crewSnap.exists) throw NotFoundException
            val crew = crewSnap.data<CrewDto>()
            if (leaver.value !in crew.memberIds) throw NotMemberException
            val remainingIds = crew.memberIds - leaver.value
            val remainingMembers = crew.members - leaver.value
            if (remainingIds.isEmpty()) {
                val codeRef = crew.code?.let { codesCol.document(it) }
                delete(crewRef)
                if (codeRef != null) delete(codeRef)
            } else {
                set(crewRef, crew.copy(memberIds = remainingIds, members = remainingMembers))
            }
        }
    }

    override suspend fun removeMember(crewId: CrewId, target: AccountId): Unit =
        withContext(dispatchers.io) {
            val crewRef = crewsCol.document(crewId.value)
            firestore.runTransaction {
                val crewSnap = get(crewRef)
                if (!crewSnap.exists) throw NotFoundException
                val crew = crewSnap.data<CrewDto>()
                if (target.value !in crew.memberIds) throw NotMemberException
                // The owner can never remove themselves (enforced by the repository + rules), so the
                // remaining set is always non-empty — no crew-deletion branch needed here, unlike `leave`.
                val remainingIds = crew.memberIds - target.value
                val remainingMembers = crew.members - target.value
                set(crewRef, crew.copy(memberIds = remainingIds, members = remainingMembers))
            }
        }

    override fun observeMyCrews(accountId: AccountId): Flow<List<CrewDto>> =
        crewsCol.where { "memberIds" contains accountId.value }
            .snapshots
            .map { snap -> snap.documents.map { it.data<CrewDto>() } }

    override fun observeCrew(crewId: CrewId): Flow<CrewDto?> = flow {
        val shared = crewSharedFlowsLock.withLock {
            crewSharedFlows.getOrPut(crewId.value) {
                crewsCol.document(crewId.value).snapshots
                    .map { snap -> if (snap.exists) snap.data<CrewDto>() else null }
                    // Sign-out revokes the auth token; iOS Firestore fires a
                    // PERMISSION_DENIED that GitLive surfaces by throwing into the
                    // upstream of `shareIn`. Swallow it as `null` so the SharedFlow
                    // stays alive and downstream maps to NotFound. The obsScope
                    // CoroutineExceptionHandler above is the safety net.
                    .catch { t ->
                        FrLog.w("Crew", t) { "observeCrew upstream throw: ${t.message}" }
                        emit(null)
                    }
                    .shareIn(obsScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
            }
        }
        emitAll(shared)
    }

    /** Single-shot read of a crew; returns null on not-found or error. */
    override suspend fun fetchOnce(crewId: CrewId): Crew? =
        observeCrew(crewId)
            .map { dto -> if (dto == null) null else (dto.toDomain() as? Result.Ok)?.value }
            .first()

    /** Single-shot resolve of a crew via its invite code: code doc → crew id → crew doc. */
    override suspend fun fetchByCode(code: CrewCode): Crew = withContext(dispatchers.io) {
        val codeSnap = codesCol.document(code.value).get()
        if (!codeSnap.exists) throw CodeUnknownException
        val crewId = codeSnap.data<CrewCodeDto>().crewId ?: throw NotFoundException
        val crewSnap = crewsCol.document(crewId).get()
        if (!crewSnap.exists) throw NotFoundException
        when (val r = crewSnap.data<CrewDto>().toDomain()) {
            is Result.Ok  -> r.value
            is Result.Err -> throw NotFoundException
        }
    }

    override suspend fun renameCrew(crewId: CrewId, newName: String): Result<Unit, CrewError> =
        withContext(dispatchers.io) {
            runCatching {
                crewsCol.document(crewId.value).update("name" to newName)
                Result.success(Unit)
            }.getOrElse { Result.failure(errorMapper.map(it)) }
        }

    override suspend fun deleteCrew(crewId: CrewId, code: CrewCode): Result<Unit, CrewError> =
        withContext(dispatchers.io) {
            runCatching {
                firestore.batch().apply {
                    delete(crewsCol.document(crewId.value))
                    delete(codesCol.document(code.value))
                }.commit()
                Result.success(Unit)
            }.getOrElse { Result.failure(errorMapper.map(it)) }
        }

    override suspend fun setBlindVoting(crewId: CrewId, enabled: Boolean): Result<Unit, CrewError> =
        withContext(dispatchers.io) {
            runCatching {
                crewsCol.document(crewId.value).update("blindVoting" to enabled)
                Result.success(Unit)
            }.getOrElse { Result.failure(errorMapper.map(it)) }
        }

    override suspend fun setTagline(crewId: CrewId, tagline: String?): Result<Unit, CrewError> =
        withContext(dispatchers.io) {
            runCatching {
                crewsCol.document(crewId.value).update("tagline" to tagline)
                Result.success(Unit)
            }.getOrElse { Result.failure(errorMapper.map(it)) }
        }

    override suspend fun setWelcomeMessage(crewId: CrewId, message: String?): Result<Unit, CrewError> =
        withContext(dispatchers.io) {
            runCatching {
                crewsCol.document(crewId.value).update("welcomeMessage" to message)
                Result.success(Unit)
            }.getOrElse { Result.failure(errorMapper.map(it)) }
        }

    override suspend fun setWeeklyChallenge(
        crewId: CrewId,
        challenge: String?,
        setAtMillis: Long?,
    ): Result<Unit, CrewError> = withContext(dispatchers.io) {
        runCatching {
            // Both fields are written together — the Firestore rule arm enforces the
            // ['weeklyChallenge','weeklyChallengeSetAtMillis'] hasOnly constraint server-side.
            crewsCol.document(crewId.value).update(
                "weeklyChallenge" to challenge,
                "weeklyChallengeSetAtMillis" to setAtMillis,
            )
            Result.success(Unit)
        }.getOrElse { Result.failure(errorMapper.map(it)) }
    }

    override suspend fun setScoreStyle(crewId: CrewId, style: String): Result<Unit, CrewError> =
        withContext(dispatchers.io) {
            runCatching {
                crewsCol.document(crewId.value).update("scoreStyle" to style)
                Result.success(Unit)
            }.getOrElse { Result.failure(errorMapper.map(it)) }
        }

    override suspend fun setBannerPath(crewId: CrewId, path: String): Result<Unit, CrewError> =
        withContext(dispatchers.io) {
            runCatching {
                crewsCol.document(crewId.value).update("bannerPath" to path)
                Result.success(Unit)
            }.getOrElse { Result.failure(errorMapper.map(it)) }
        }

    override suspend fun clearBannerPath(crewId: CrewId): Result<Unit, CrewError> =
        withContext(dispatchers.io) {
            runCatching {
                crewsCol.document(crewId.value).update("bannerPath" to null)
                Result.success(Unit)
            }.getOrElse { Result.failure(errorMapper.map(it)) }
        }

    companion object { const val MAX_CODE_ATTEMPTS = 5 }
}

internal object CodeTakenException : RuntimeException() { private fun readResolve(): Any = CodeTakenException }
internal object CodeUnknownException : RuntimeException() { private fun readResolve(): Any = CodeUnknownException }
internal object CodeCollisionExhaustedException : RuntimeException() { private fun readResolve(): Any = CodeCollisionExhaustedException }
internal object NotFoundException : RuntimeException() { private fun readResolve(): Any = NotFoundException }
internal object FullException : RuntimeException() { private fun readResolve(): Any = FullException }
internal object AlreadyMemberException : RuntimeException() { private fun readResolve(): Any = AlreadyMemberException }
internal object NotMemberException : RuntimeException() { private fun readResolve(): Any = NotMemberException }
