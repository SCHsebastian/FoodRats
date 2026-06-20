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
     * Throws one of [CodeUnknownException] / [NotFoundException] / [FullException] /
     * [AlreadyMemberException] (+ mapped backend throwables) on failure.
     */
    suspend fun joinByCode(
        code: CrewCode,
        joiner: AccountId,
        nowMs: Long,
    ): CrewDto

    /** Throws [NotFoundException] / [NotMemberException] (+ mapped backend throwables) on failure. */
    suspend fun leave(crewId: CrewId, leaver: AccountId)

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
}
