package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository
import kotlinx.coroutines.flow.first

/**
 * Owner gate for the OFFLINE enqueue branch of the owner-only crew-settings use cases
 * (rename / blind-voting / tagline / welcome / weekly-challenge / score-style / banner-focal).
 *
 * The repository re-checks ownership atomically on every ONLINE write and on every outbox replay,
 * so this is NOT the authoritative gate. Its job is narrower: stop a non-owner from getting an
 * offline FALSE SUCCESS. Without it, an offline Set\* returns [Result.Ok] and parks a command that
 * only fails (terminally) on replay — long after the user saw "saved". When the crew read model is
 * available (it survives offline via the P1 list cache), a non-owner is rejected with
 * [CrewError.Authorization.NotOwner] before the enqueue. When the crew can't be read offline, the
 * command is allowed through and the server re-validates on replay — the same posture as
 * [RemoveMemberUseCase].
 *
 * Returns the [CrewError] to fail with, or `null` when the caller may proceed to enqueue.
 */
internal suspend fun CrewRepository.offlineOwnerGuard(
    crewId: CrewId,
    requestedBy: AccountId,
): CrewError? = when (val c = observeCrew(crewId).first()) {
    is Result.Ok -> if (c.value.ownerId == requestedBy) null else CrewError.Authorization.NotOwner
    is Result.Err -> null // can't verify offline → allow; the server re-validates on replay
}
