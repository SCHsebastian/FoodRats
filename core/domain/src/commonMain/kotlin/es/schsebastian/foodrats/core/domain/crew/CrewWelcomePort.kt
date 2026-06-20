package es.schsebastian.foodrats.core.domain.crew

import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.coroutines.flow.Flow

/**
 * Live welcome-message and dismissal state for a crew. Consumed by `:feature:feed` to show a
 * dismissible banner to new joiners without depending on `:feature:crew`.
 *
 * Lives in `:core:domain` — mirroring [CrewBlindVotingPort] — and is bound in `crewModule`.
 *
 * [observeWelcomeMessage] emits `null` when no message is set or the crew is unreadable.
 * [isWelcomeDismissed] emits `true` once the user has dismissed the banner for this crew.
 * [dismissWelcome] persists the dismissal (fire-and-forget from the screen; errors silently dropped
 * since a failed dismissal just re-shows the banner).
 */
interface CrewWelcomePort {
    /** Emits the crew's current welcome message, or `null` when none is set. */
    fun observeWelcomeMessage(crewId: CrewId): Flow<String?>

    /** Emits `true` once the user has dismissed the welcome banner for the given crew. */
    fun isWelcomeDismissed(crewId: CrewId): Flow<Boolean>

    /** Persists the dismissal for [crewId] so the banner does not re-appear. */
    suspend fun dismissWelcome(crewId: CrewId)
}
