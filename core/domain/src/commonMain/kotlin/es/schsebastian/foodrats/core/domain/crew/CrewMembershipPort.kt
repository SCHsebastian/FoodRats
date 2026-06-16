package es.schsebastian.foodrats.core.domain.crew

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.coroutines.flow.Flow

/**
 * A crew the signed-in account belongs to, projected to just the fields the
 * meal-audience picker renders (id for selection, name for the chip label).
 */
data class CrewSummary(val id: CrewId, val name: String)

/**
 * Cross-context read port exposing every crew an account is a member of.
 *
 * Lives in `:core:domain` so `:feature:meal` can populate the publish-audience
 * picker (and fan a published plate out to the chosen crews) without depending on
 * `:feature:crew` — mirroring how [ActiveCrewProvider] / `MealReadPort` are consumed.
 * Bound in `crewModule` over `CrewRepository.observeMyCrews`.
 */
interface CrewMembershipPort {
    /**
     * Streams the crews [accountId] belongs to. A read failure surfaces as an empty
     * list (the picker then shows no crews and publishing is gated) rather than an
     * error channel — the audience picker degrades gracefully, it never blocks.
     */
    fun observeMyCrews(accountId: AccountId): Flow<List<CrewSummary>>
}
