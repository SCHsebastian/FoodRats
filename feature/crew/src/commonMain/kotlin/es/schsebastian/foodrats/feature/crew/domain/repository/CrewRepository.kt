package es.schsebastian.foodrats.feature.crew.domain.repository

import es.schsebastian.foodrats.core.domain.crew.CrewScoreStyle
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.model.JoinRequest
import kotlinx.coroutines.flow.Flow

interface CrewRepository {

    /** Creates a new Crew with the calling user as the sole founding member. */
    suspend fun create(name: String, founder: AccountId): Result<Crew, CrewError>

    /**
     * Files a request to join the crew behind invite [code] (no instant join — owner approval is
     * required). Resolves the code to a crew, then writes `crews/{crewId}/joinRequests/{requester}`.
     * Returns [CrewError.Invite.CodeUnknown] for an unknown code, [CrewError.Membership.NotFound] if
     * the crew is gone, [CrewError.Membership.AlreadyMember] if the requester is already in the crew.
     */
    suspend fun requestToJoinByCode(code: CrewCode, requester: AccountId): Result<Unit, CrewError>

    /** Streams the pending join requests for [crewId] (owner-only by Firestore rule). */
    fun observeJoinRequests(crewId: CrewId): Flow<Result<List<JoinRequest>, CrewError>>

    /**
     * Withdraws the signed-in [requester]'s OWN pending join request for [crewId] (the requester-side
     * cancel — distinct from the owner's decline). Deletes `crews/{crewId}/joinRequests/{requester}`;
     * the Firestore rule already permits a requester to delete their own request doc. Idempotent —
     * cancelling an absent request succeeds.
     */
    suspend fun cancelJoinRequest(crewId: CrewId, requester: AccountId): Result<Unit, CrewError>

    /**
     * Approves a pending join request: atomically adds [requester] to the crew and deletes their
     * request doc. Only the owner ([requestedBy]) may approve; the cap is re-checked server-side.
     * Idempotent — approving a requester who is already a member just clears the stale request.
     */
    suspend fun approveJoinRequest(crewId: CrewId, requestedBy: AccountId, requester: AccountId): Result<Unit, CrewError>

    /** Declines (deletes) a pending join request. Only the owner ([requestedBy]) may decline. */
    suspend fun declineJoinRequest(crewId: CrewId, requestedBy: AccountId, requester: AccountId): Result<Unit, CrewError>

    /**
     * Transfers crew ownership to [newOwner], who must be a current member. Only the owner
     * ([requestedBy]) may transfer. Returns [CrewError.Transfer.NotOwner] /
     * [CrewError.Transfer.TargetNotMember] on a violation; enforced server-side by the Firestore rule.
     */
    suspend fun transferOwnership(crewId: CrewId, requestedBy: AccountId, newOwner: AccountId): Result<Unit, CrewError>

    /**
     * Resolves a Crew by its invite [code] WITHOUT joining — for the accept-invite preview (the
     * recipient has only the code from the link). Read-only. Returns [CrewError.Invite.CodeUnknown]
     * for an unknown code, [CrewError.Membership.NotFound] if the crew is gone.
     */
    suspend fun findByCode(code: CrewCode): Result<Crew, CrewError>

    /**
     * Leaves a Crew. Hard-deletes the Crew + invite code if `leaver` was the last member. If `leaver`
     * is the OWNER and other members remain, ownership is reassigned in the same atomic write: to
     * [successor] when given (and still a member), otherwise to the longest-tenured remaining member.
     */
    suspend fun leave(crewId: CrewId, leaver: AccountId, successor: AccountId? = null): Result<Unit, CrewError>

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
     * Sets or clears the crew tagline. Only the owner may change it.
     * Pass `null` to clear the tagline. The raw string must be pre-validated via
     * [es.schsebastian.foodrats.feature.crew.domain.model.CrewTagline.of] before calling
     * this method.
     */
    suspend fun setTagline(crewId: CrewId, requestedBy: AccountId, tagline: String?): Result<Unit, CrewError>

    /**
     * Sets or clears the crew's pinned welcome message. Only the owner may change it.
     * Pass `null` to clear the message. The raw string must be pre-validated via
     * [es.schsebastian.foodrats.feature.crew.domain.model.WelcomeMessage.of] before calling
     * this method.
     */
    suspend fun setWelcomeMessage(crewId: CrewId, requestedBy: AccountId, message: String?): Result<Unit, CrewError>

    /**
     * Sets or clears the crew's weekly challenge. Only the owner may change it.
     * Pass `null` for both to clear. Both [challenge] and [setAtMillis] are written together
     * atomically — the `['weeklyChallenge','weeklyChallengeSetAtMillis']` Firestore rule arm
     * enforces this. The raw string must be pre-validated via
     * [es.schsebastian.foodrats.feature.crew.domain.model.WeeklyChallenge.of] before calling
     * this method.
     */
    suspend fun setWeeklyChallenge(
        crewId: CrewId,
        requestedBy: AccountId,
        challenge: String?,
        setAtMillis: Long?,
    ): Result<Unit, CrewError>

    /**
     * Sets the crew's Score display vocabulary (C8). Only the owner may change it.
     * Authorization is enforced in both the repository and by the `['scoreStyle']` Firestore rule arm.
     */
    suspend fun setScoreStyle(crewId: CrewId, requestedBy: AccountId, style: CrewScoreStyle): Result<Unit, CrewError>

    /**
     * Sets the crew's banner vertical focal point (C9), in `0f..1f`. Only the owner may change it.
     * Authorization is enforced in both the repository and by the `['bannerFocalY']` Firestore rule
     * arm. [focalY] is expected pre-clamped to `0f..1f` by the use case.
     */
    suspend fun setBannerFocalY(crewId: CrewId, requestedBy: AccountId, focalY: Float): Result<Unit, CrewError>

    /**
     * Removes [target] from the crew. Only the owner ([requestedBy]) may remove a member, and the
     * owner cannot remove themselves (leaving is a separate flow). Authorization, self-removal, and
     * membership invariants are enforced atomically server-side by the implementation (mirrored
     * in-domain by [es.schsebastian.foodrats.feature.crew.domain.usecase.RemoveMemberUseCase]).
     */
    suspend fun removeMember(crewId: CrewId, requestedBy: AccountId, target: AccountId): Result<Unit, CrewError>

    /**
     * Uploads [bytes] as the crew's hero/banner image (C9). Only the owner ([requestedBy]) may set
     * it. Uploads bytes to Storage, then persists the path to `crews/{crewId}.bannerPath`.
     * Returns [es.schsebastian.foodrats.feature.crew.domain.error.CrewError.Banner.UploadFailed] if
     * the Storage write fails; [es.schsebastian.foodrats.feature.crew.domain.error.CrewError.Authorization.NotOwner]
     * if the caller is not the crew owner.
     */
    suspend fun setBanner(crewId: CrewId, requestedBy: AccountId, bytes: ByteArray): Result<Unit, CrewError>

    /**
     * Removes the crew's hero/banner image (C9). Deletes the Storage object (NOT_FOUND-tolerant)
     * then clears `crews/{crewId}.bannerPath`. Only the owner ([requestedBy]) may remove the banner.
     * Returns [es.schsebastian.foodrats.feature.crew.domain.error.CrewError.Banner.DeleteFailed] if
     * the Storage delete fails with a non-NOT_FOUND error.
     */
    suspend fun removeBanner(crewId: CrewId, requestedBy: AccountId): Result<Unit, CrewError>
}
