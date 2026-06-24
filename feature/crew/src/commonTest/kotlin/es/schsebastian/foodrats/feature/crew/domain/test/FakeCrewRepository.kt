package es.schsebastian.foodrats.feature.crew.domain.test

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.model.CrewTagline
import es.schsebastian.foodrats.feature.crew.domain.model.JoinRequest
import es.schsebastian.foodrats.feature.crew.domain.model.Member
import es.schsebastian.foodrats.feature.crew.domain.model.WelcomeMessage
import es.schsebastian.foodrats.feature.crew.domain.model.WeeklyChallenge
import es.schsebastian.foodrats.core.domain.crew.CrewScoreStyle
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeCrewRepository(
    initial: List<Crew> = emptyList(),
) : CrewRepository {
    val crews = MutableStateFlow(initial)
    /** Pending join requests per crew, observed by [observeJoinRequests]. */
    val joinRequests = MutableStateFlow<Map<CrewId, List<JoinRequest>>>(emptyMap())
    var nextCreate: Result<Crew, CrewError>? = null
    /** When set, overrides the default requestToJoinByCode behavior (success no-op). */
    var nextRequestToJoin: Result<Unit, CrewError>? = null
    /** When set, overrides the default approveJoinRequest behavior (owner check + add member). */
    var nextApprove: Result<Unit, CrewError>? = null
    /** When set, overrides the default declineJoinRequest behavior (owner check + drop request). */
    var nextDecline: Result<Unit, CrewError>? = null
    /** When set, overrides the default transferOwnership behavior (owner/member checks + mutation). */
    var nextTransfer: Result<Unit, CrewError>? = null
    /** When set, overrides the default findByCode behavior (lookup by code in [crews]). */
    var nextFindByCode: Result<Crew, CrewError>? = null
    var nextLeave: Result<Unit, CrewError>? = null
    var lastRequestToJoin: Pair<CrewCode, AccountId>? = null
    var lastApprove: Pair<CrewId, AccountId>? = null
    var lastDecline: Pair<CrewId, AccountId>? = null
    var lastTransfer: Pair<CrewId, AccountId>? = null
    var lastLeave: Triple<CrewId, AccountId, AccountId?>? = null
    /** When set, overrides the default rename behavior (ownership check + mutation). */
    var nextRename: Result<Unit, CrewError>? = null
    /** When set, overrides the default delete behavior (ownership check + mutation). */
    var nextDelete: Result<Unit, CrewError>? = null
    /** When set, overrides the default setBlindVoting behavior (ownership check + mutation). */
    var nextSetBlindVoting: Result<Unit, CrewError>? = null
    /** When set, overrides the default setTagline behavior (ownership check + mutation). */
    var nextSetTagline: Result<Unit, CrewError>? = null
    /** When set, overrides the default setWelcomeMessage behavior (ownership check + mutation). */
    var nextSetWelcomeMessage: Result<Unit, CrewError>? = null
    /** When set, overrides the default setWeeklyChallenge behavior (ownership check + mutation). */
    var nextSetWeeklyChallenge: Result<Unit, CrewError>? = null
    /** When set, overrides the default removeMember behavior (ownership/self/membership checks + mutation). */
    var nextRemoveMember: Result<Unit, CrewError>? = null
    /** When set, overrides the default setScoreStyle behavior (ownership check + mutation). */
    var nextSetScoreStyle: Result<Unit, CrewError>? = null
    /** When set, overrides the default setBanner behavior. */
    var nextSetBanner: Result<Unit, CrewError>? = null
    /** When set, overrides the default removeBanner behavior. */
    var nextRemoveBanner: Result<Unit, CrewError>? = null
    /** When set, overrides the default setBannerFocalY behavior (ownership check + mutation). */
    var nextSetBannerFocal: Result<Unit, CrewError>? = null
    /** The last focal value passed to setBannerFocalY (for assertions). */
    var lastSetBannerFocal: Pair<CrewId, Float>? = null

    var lastRename: Pair<CrewId, String>? = null
    var lastMemberRename: Triple<CrewId, AccountId, String>? = null
    var lastDelete: CrewId? = null
    var lastSetBlindVoting: Pair<CrewId, Boolean>? = null
    var lastSetTagline: Pair<CrewId, String?>? = null
    var lastSetWelcomeMessage: Pair<CrewId, String?>? = null
    var lastSetWeeklyChallenge: Triple<CrewId, String?, Long?>? = null
    var lastRemoveMember: Triple<CrewId, AccountId, AccountId>? = null

    override suspend fun create(
        name: String,
        founder: AccountId,
    ): Result<Crew, CrewError> {
        val r = nextCreate ?: error("nextCreate not stubbed")
        if (r is Result.Ok) crews.value = crews.value + r.value
        return r
    }

    override suspend fun requestToJoinByCode(
        code: CrewCode,
        requester: AccountId,
    ): Result<Unit, CrewError> {
        lastRequestToJoin = code to requester
        return nextRequestToJoin ?: Result.success(Unit)
    }

    override fun observeJoinRequests(crewId: CrewId): Flow<Result<List<JoinRequest>, CrewError>> =
        joinRequests.map { Result.success(it[crewId].orEmpty()) }

    override suspend fun approveJoinRequest(
        crewId: CrewId,
        requestedBy: AccountId,
        requester: AccountId,
    ): Result<Unit, CrewError> {
        lastApprove = crewId to requester
        nextApprove?.let { return it }
        val crew = crews.value.firstOrNull { it.id == crewId }
            ?: return Result.failure(CrewError.Membership.NotFound)
        if (requestedBy != crew.ownerId) return Result.failure(CrewError.Authorization.NotOwner)
        crews.value = crews.value.map {
            if (it.id == crewId && it.members.none { m -> m.accountId == requester })
                it.copy(members = it.members + Member(requester, Instant.fromEpochMilliseconds(0L)))
            else it
        }
        joinRequests.value = joinRequests.value + (crewId to joinRequests.value[crewId].orEmpty().filterNot { it.accountId == requester })
        return Result.success(Unit)
    }

    override suspend fun declineJoinRequest(
        crewId: CrewId,
        requestedBy: AccountId,
        requester: AccountId,
    ): Result<Unit, CrewError> {
        lastDecline = crewId to requester
        nextDecline?.let { return it }
        val crew = crews.value.firstOrNull { it.id == crewId }
            ?: return Result.failure(CrewError.Membership.NotFound)
        if (requestedBy != crew.ownerId) return Result.failure(CrewError.Authorization.NotOwner)
        joinRequests.value = joinRequests.value + (crewId to joinRequests.value[crewId].orEmpty().filterNot { it.accountId == requester })
        return Result.success(Unit)
    }

    override suspend fun transferOwnership(
        crewId: CrewId,
        requestedBy: AccountId,
        newOwner: AccountId,
    ): Result<Unit, CrewError> {
        lastTransfer = crewId to newOwner
        nextTransfer?.let { return it }
        val crew = crews.value.firstOrNull { it.id == crewId }
            ?: return Result.failure(CrewError.Membership.NotFound)
        if (requestedBy != crew.ownerId) return Result.failure(CrewError.Transfer.NotOwner)
        if (crew.members.none { it.accountId == newOwner }) return Result.failure(CrewError.Transfer.TargetNotMember)
        crews.value = crews.value.map { if (it.id == crewId) it.copy(ownerId = newOwner) else it }
        return Result.success(Unit)
    }

    override suspend fun findByCode(code: CrewCode): Result<Crew, CrewError> {
        nextFindByCode?.let { return it }
        return crews.value.firstOrNull { it.code == code }?.let { Result.success(it) }
            ?: Result.failure(CrewError.Invite.CodeUnknown)
    }

    override suspend fun leave(crewId: CrewId, leaver: AccountId, successor: AccountId?): Result<Unit, CrewError> {
        lastLeave = Triple(crewId, leaver, successor)
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

    override suspend fun setBlindVoting(
        crewId: CrewId,
        requestedBy: AccountId,
        enabled: Boolean,
    ): Result<Unit, CrewError> {
        nextSetBlindVoting?.let { return it }
        val crew = crews.value.firstOrNull { it.id == crewId }
            ?: return Result.failure(CrewError.Membership.NotFound)
        if (requestedBy != crew.ownerId) return Result.failure(CrewError.Authorization.NotOwner)
        lastSetBlindVoting = Pair(crewId, enabled)
        crews.value = crews.value.map { if (it.id == crewId) it.copy(blindVoting = enabled) else it }
        return Result.success(Unit)
    }

    override suspend fun setTagline(
        crewId: CrewId,
        requestedBy: AccountId,
        tagline: String?,
    ): Result<Unit, CrewError> {
        nextSetTagline?.let { return it }
        val crew = crews.value.firstOrNull { it.id == crewId }
            ?: return Result.failure(CrewError.Membership.NotFound)
        if (requestedBy != crew.ownerId) return Result.failure(CrewError.Authorization.NotOwner)
        lastSetTagline = Pair(crewId, tagline)
        val parsedTagline = tagline?.let {
            when (val r = CrewTagline.of(it)) {
                is Result.Ok  -> r.value
                is Result.Err -> return Result.failure(r.error)
            }
        }
        crews.value = crews.value.map {
            if (it.id == crewId) it.copy(tagline = parsedTagline) else it
        }
        return Result.success(Unit)
    }

    override suspend fun setWelcomeMessage(
        crewId: CrewId,
        requestedBy: AccountId,
        message: String?,
    ): Result<Unit, CrewError> {
        nextSetWelcomeMessage?.let { return it }
        val crew = crews.value.firstOrNull { it.id == crewId }
            ?: return Result.failure(CrewError.Membership.NotFound)
        if (requestedBy != crew.ownerId) return Result.failure(CrewError.Authorization.NotOwner)
        lastSetWelcomeMessage = Pair(crewId, message)
        val parsedMessage = message?.let {
            when (val r = WelcomeMessage.of(it)) {
                is Result.Ok  -> r.value
                is Result.Err -> return Result.failure(r.error)
            }
        }
        crews.value = crews.value.map {
            if (it.id == crewId) it.copy(welcomeMessage = parsedMessage) else it
        }
        return Result.success(Unit)
    }

    override suspend fun setWeeklyChallenge(
        crewId: CrewId,
        requestedBy: AccountId,
        challenge: String?,
        setAtMillis: Long?,
    ): Result<Unit, CrewError> {
        nextSetWeeklyChallenge?.let { return it }
        val crew = crews.value.firstOrNull { it.id == crewId }
            ?: return Result.failure(CrewError.Membership.NotFound)
        if (requestedBy != crew.ownerId) return Result.failure(CrewError.Authorization.NotOwner)
        lastSetWeeklyChallenge = Triple(crewId, challenge, setAtMillis)
        val parsedChallenge = challenge?.let {
            when (val r = WeeklyChallenge.of(it)) {
                is Result.Ok  -> r.value
                is Result.Err -> return Result.failure(r.error)
            }
        }
        val setAt = setAtMillis?.let { Instant.fromEpochMilliseconds(it) }
        crews.value = crews.value.map {
            if (it.id == crewId) it.copy(weeklyChallenge = parsedChallenge, weeklyChallengeSetAt = setAt) else it
        }
        return Result.success(Unit)
    }

    override suspend fun setScoreStyle(
        crewId: CrewId,
        requestedBy: AccountId,
        style: CrewScoreStyle,
    ): Result<Unit, CrewError> {
        nextSetScoreStyle?.let { return it }
        val crew = crews.value.firstOrNull { it.id == crewId }
            ?: return Result.failure(CrewError.Membership.NotFound)
        if (requestedBy != crew.ownerId) return Result.failure(CrewError.Authorization.NotOwner)
        crews.value = crews.value.map { if (it.id == crewId) it.copy(scoreStyle = style) else it }
        return Result.success(Unit)
    }

    override suspend fun setBannerFocalY(
        crewId: CrewId,
        requestedBy: AccountId,
        focalY: Float,
    ): Result<Unit, CrewError> {
        nextSetBannerFocal?.let { return it }
        val crew = crews.value.firstOrNull { it.id == crewId }
            ?: return Result.failure(CrewError.Membership.NotFound)
        if (requestedBy != crew.ownerId) return Result.failure(CrewError.Authorization.NotOwner)
        lastSetBannerFocal = crewId to focalY
        crews.value = crews.value.map { if (it.id == crewId) it.copy(bannerFocalY = focalY) else it }
        return Result.success(Unit)
    }

    override suspend fun removeMember(
        crewId: CrewId,
        requestedBy: AccountId,
        target: AccountId,
    ): Result<Unit, CrewError> {
        nextRemoveMember?.let { return it }
        val crew = crews.value.firstOrNull { it.id == crewId }
            ?: return Result.failure(CrewError.Membership.NotFound)
        if (requestedBy != crew.ownerId) return Result.failure(CrewError.RemoveMember.NotOwner)
        if (target == requestedBy) return Result.failure(CrewError.RemoveMember.CannotRemoveSelf)
        if (crew.members.none { it.accountId == target }) {
            return Result.failure(CrewError.RemoveMember.MemberNotFound)
        }
        lastRemoveMember = Triple(crewId, requestedBy, target)
        crews.value = crews.value.map {
            if (it.id == crewId) it.copy(members = it.members.filterNot { m -> m.accountId == target }) else it
        }
        return Result.success(Unit)
    }

    // C9 — banner

    override suspend fun setBanner(
        crewId: CrewId,
        requestedBy: AccountId,
        bytes: ByteArray,
    ): Result<Unit, CrewError> {
        nextSetBanner?.let { return it }
        val crew = crews.value.firstOrNull { it.id == crewId }
            ?: return Result.failure(CrewError.Membership.NotFound)
        if (requestedBy != crew.ownerId) return Result.failure(CrewError.Authorization.NotOwner)
        crews.value = crews.value.map {
            if (it.id == crewId) it.copy(bannerPath = "crew_banners/${crewId.value}/banner.jpg") else it
        }
        return Result.success(Unit)
    }

    override suspend fun removeBanner(
        crewId: CrewId,
        requestedBy: AccountId,
    ): Result<Unit, CrewError> {
        nextRemoveBanner?.let { return it }
        val crew = crews.value.firstOrNull { it.id == crewId }
            ?: return Result.failure(CrewError.Membership.NotFound)
        if (requestedBy != crew.ownerId) return Result.failure(CrewError.Authorization.NotOwner)
        crews.value = crews.value.map {
            if (it.id == crewId) it.copy(bannerPath = null) else it
        }
        return Result.success(Unit)
    }

}

/** Helper: create AccountId without going through validation (tests only). */
fun aid(raw: String): AccountId = (AccountId.of(raw) as Result.Ok).value
/** Helper: create CrewId without going through validation (tests only). */
fun cid(raw: String): CrewId = (CrewId.of(raw) as Result.Ok).value
