package es.schsebastian.foodrats.feature.crew.data.firebase

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import kotlinx.coroutines.flow.Flow

/**
 * The data-layer seam the [es.schsebastian.foodrats.feature.crew.data.repository.FirebaseCrewRepository]
 * orchestrates over. Exists so the repository's mapping / sequencing / error-classification logic can be
 * unit-tested with a behavioral fake instead of a live Firestore — the same role [Firebase→own-server] swap
 * depends on. The concrete [CrewFirestoreDataSource] is the production implementation.
 *
 * The membership-cap check is INTENTIONALLY not on this interface: it lives inside the Firestore
 * `runTransaction` (atomic, references [es.schsebastian.foodrats.feature.crew.domain.model.CrewSize.canAdd])
 * and is covered by the emulator harness, not by repository unit tests.
 */
interface CrewDataSource {

    /** Throws [CodeCollisionExhaustedException] (+ mapped backend throwables) on failure. */
    suspend fun createCrew(
        name: String,
        founder: AccountId,
        nowMs: Long,
    ): CrewDto

    /**
     * Files a join request at `crews/{crewId}/joinRequests/{requester}` after resolving [code] to a
     * crew. No instant join — owner approval is required.
     * Throws [CodeUnknownException] (code doc missing) / [NotFoundException] (crew gone) /
     * [AlreadyMemberException] (requester already in the crew) (+ mapped backend throwables).
     */
    suspend fun requestToJoin(code: CrewCode, requester: AccountId, nowMs: Long)

    /** Streams the pending join-request DTOs for [crewId] (owner-only by Firestore rule). */
    fun observeJoinRequests(crewId: CrewId): Flow<List<JoinRequestDto>>

    /**
     * Atomically adds [requester] to the crew (with [nowMs] as their join time) and deletes their
     * request doc. Idempotent: if the requester is already a member, only the request is cleared.
     * Throws [NotFoundException] (crew gone) / [FullException] (crew at cap) (+ mapped backend throwables).
     */
    suspend fun approveJoinRequest(crewId: CrewId, requester: AccountId, nowMs: Long)

    /** Deletes the pending request doc for [requester] (the decline / cancel path). */
    suspend fun declineJoinRequest(crewId: CrewId, requester: AccountId): Result<Unit, CrewError>

    /**
     * Reassigns `crews/{crewId}.ownerId` to [newOwner]. Updates only the `ownerId` field — the
     * transfer-ownership Firestore rule enforces this server-side (new owner must be a member).
     */
    suspend fun transferOwnership(crewId: CrewId, newOwner: AccountId): Result<Unit, CrewError>

    /**
     * Throws [NotFoundException] / [NotMemberException] (+ mapped backend throwables) on failure.
     * When [leaver] is the owner and other members remain, ownership is reassigned atomically: to
     * [successor] if given (and a remaining member), otherwise to the longest-tenured remaining member.
     */
    suspend fun leave(crewId: CrewId, leaver: AccountId, successor: AccountId? = null)

    /**
     * Owner-initiated removal of [target] from the crew. Atomic: drops [target] from both
     * `memberIds` and the `members` map inside a transaction.
     *
     * Throws [NotFoundException] (crew gone) / [NotMemberException] (target is not a member —
     * the TOCTOU guard re-checked inside the transaction) (+ mapped backend throwables) on failure.
     * The owner / not-self invariants are enforced by the repository (read-then-decide) and by
     * `firestore.rules` server-side; the transaction here owns only the atomic membership mutation.
     */
    suspend fun removeMember(crewId: CrewId, target: AccountId)

    fun observeMyCrews(accountId: AccountId): Flow<List<CrewDto>>

    fun observeCrew(crewId: CrewId): Flow<CrewDto?>

    /** Single-shot read of a crew; returns null on not-found or error. */
    suspend fun fetchOnce(crewId: CrewId): Crew?

    /**
     * Single-shot read of a crew via its invite [code] — resolves `crewCodes/{code}` → crew id →
     * `crews/{crewId}`. Used by the accept-invite preview (the recipient has the code from the link,
     * not the crew id). Throws [CodeUnknownException] (code doc missing) / [NotFoundException] (crew
     * doc gone) (+ mapped backend throwables) on failure.
     */
    suspend fun fetchByCode(code: CrewCode): Crew

    suspend fun renameCrew(crewId: CrewId, newName: String): Result<Unit, CrewError>

    suspend fun deleteCrew(crewId: CrewId, code: CrewCode): Result<Unit, CrewError>

    suspend fun setBlindVoting(crewId: CrewId, enabled: Boolean): Result<Unit, CrewError>

    /**
     * Sets the crew's tagline. Pass `null` to clear it. Updates only the `tagline` field —
     * the `['tagline']` Firestore rule arm enforces this server-side.
     */
    suspend fun setTagline(crewId: CrewId, tagline: String?): Result<Unit, CrewError>

    /**
     * Sets the crew's welcome message. Pass `null` to clear it. Updates only the `welcomeMessage`
     * field — the `['welcomeMessage']` Firestore rule arm enforces this server-side.
     */
    suspend fun setWelcomeMessage(crewId: CrewId, message: String?): Result<Unit, CrewError>

    /**
     * Sets or clears the crew's weekly challenge. Pass `null` for both to clear.
     * Updates BOTH `weeklyChallenge` AND `weeklyChallengeSetAtMillis` together — the
     * `['weeklyChallenge','weeklyChallengeSetAtMillis']` Firestore rule arm enforces this.
     */
    suspend fun setWeeklyChallenge(crewId: CrewId, challenge: String?, setAtMillis: Long?): Result<Unit, CrewError>

    /**
     * Sets the crew's Score display vocabulary (C8). [style] is the Firestore string
     * ("stars" | "emoji" | "numeric"). Updates only the `scoreStyle` field — the
     * `['scoreStyle']` Firestore rule arm enforces this.
     */
    suspend fun setScoreStyle(crewId: CrewId, style: String): Result<Unit, CrewError>

    /**
     * Sets the crew's banner vertical focal point (C9), in `0f..1f`. Updates only the `bannerFocalY`
     * field — the `['bannerFocalY']` Firestore rule arm enforces this.
     */
    suspend fun setBannerFocalY(crewId: CrewId, focalY: Float): Result<Unit, CrewError>

    /**
     * Sets the crew's banner image path (C9). [path] is the Storage object path returned by
     * [es.schsebastian.foodrats.feature.crew.data.firebase.CrewBannerStorageDataSource.upload].
     * Updates only the `bannerPath` field — the `['bannerPath']` Firestore rule arm enforces this.
     */
    suspend fun setBannerPath(crewId: CrewId, path: String): Result<Unit, CrewError>

    /**
     * Clears the crew's banner image path (C9). Passes `null` for `bannerPath`.
     * Updates only the `bannerPath` field — the `['bannerPath']` Firestore rule arm enforces this.
     */
    suspend fun clearBannerPath(crewId: CrewId): Result<Unit, CrewError>
}
