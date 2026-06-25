package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository

/**
 * Files a request to join the crew behind raw invite [rawCode]. Validates the code shape first
 * ([CrewError.Validation.CodeMalformed]) so a corrupt code fails fast before a backend round-trip,
 * then delegates to [CrewRepository.requestToJoinByCode]. There is NO instant join — the crew owner
 * must approve the request before the requester becomes a member.
 */
class RequestToJoinCrewUseCase(private val repo: CrewRepository) {
    suspend operator fun invoke(
        rawCode: String,
        requester: AccountId,
    ): Result<Unit, CrewError> = when (val parsed = CrewCode.of(rawCode)) {
        is Result.Err -> Result.failure(parsed.error)
        is Result.Ok  -> repo.requestToJoinByCode(parsed.value, requester)
    }
}
