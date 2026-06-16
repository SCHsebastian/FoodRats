package es.schsebastian.foodrats.core.domain.crew

import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.coroutines.flow.Flow

/**
 * Live "is blind voting on?" policy for a crew. When on, a meal's author identity
 * is hidden from a crewmate until that crewmate has cast their own score (so members
 * aren't anchored by who cooked it).
 *
 * Lives in `:core:domain` so `:feature:feed` can mask author identity without
 * depending on `:feature:crew` — mirroring how [CrewOwnerPort] / [ActiveCrewProvider]
 * are consumed. Bound in `crewModule` over the crew read model.
 *
 * Emits `false` when the crew is unknown or unreadable: the safe default is the
 * current (un-blind) behavior, never a crew accidentally hiding identities because a
 * read failed. The masking decision itself is [BlindVotingPolicy.shouldMaskAuthor].
 */
interface CrewBlindVotingPort {
    fun observeBlindVoting(crewId: CrewId): Flow<Boolean>
}
