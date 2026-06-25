package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.error.toCrewError
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository

/**
 * Uploads JPEG [bytes] as the crew hero/banner image (C9). Delegates to [CrewRepository.setBanner]
 * which enforces owner-only authorization, uploads to Storage, then persists the path to Firestore.
 *
 * Cannot be queued in the outbox — raw byte arrays are not serializable to DataStore.
 * Fails immediately when offline or when the Storage/Firestore write fails.
 */
class SetCrewBannerUseCase(
    private val repository: CrewRepository,
    private val session: SessionProvider,
) {
    suspend operator fun invoke(crewId: CrewId, bytes: ByteArray): Result<Unit, CrewError> {
        val accountId = when (val s = session.requireCurrent()) {
            is Result.Ok -> s.value.accountId
            is Result.Err -> return Result.failure(s.error.toCrewError())
        }
        return repository.setBanner(crewId, accountId, bytes)
    }
}
