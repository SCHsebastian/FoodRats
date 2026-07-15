package es.schsebastian.foodrats.core.domain.crew

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.coroutines.flow.Flow

/**
 * Live roster of a crew's member account ids. Consumed by features that need the crew's
 * membership for candidate lists (e.g. `:feature:feed`'s @-mention autocomplete) without
 * depending on `:feature:crew`. Mirrors [CrewOwnerPort]: emits `emptyList()` when the crew is
 * unknown or unreadable rather than surfacing an error channel — the roster is advisory, so a
 * read failure degrades to "no candidates" instead of blocking the caller.
 */
interface CrewRosterPort {
    fun observeMembers(crewId: CrewId): Flow<List<AccountId>>
}
