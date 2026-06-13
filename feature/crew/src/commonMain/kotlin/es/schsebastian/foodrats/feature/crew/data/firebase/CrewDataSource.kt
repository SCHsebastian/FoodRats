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
        founderDisplayName: String,
        nowMs: Long,
    ): CrewDto

    /**
     * Throws one of [CodeUnknownException] / [NotFoundException] / [FullException] /
     * [AlreadyMemberException] (+ mapped backend throwables) on failure.
     */
    suspend fun joinByCode(
        code: CrewCode,
        joiner: AccountId,
        joinerDisplayName: String,
        nowMs: Long,
    ): CrewDto

    /** Throws [NotFoundException] / [NotMemberException] (+ mapped backend throwables) on failure. */
    suspend fun leave(crewId: CrewId, leaver: AccountId)

    fun observeMyCrews(accountId: AccountId): Flow<List<CrewDto>>

    fun observeCrew(crewId: CrewId): Flow<CrewDto?>

    /** Single-shot read of a crew; returns null on not-found or error. */
    suspend fun fetchOnce(crewId: CrewId): Crew?

    suspend fun renameCrew(crewId: CrewId, newName: String): Result<Unit, CrewError>

    suspend fun deleteCrew(crewId: CrewId, code: CrewCode): Result<Unit, CrewError>
}
