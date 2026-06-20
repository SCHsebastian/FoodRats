package es.schsebastian.foodrats.core.domain.crew

import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.coroutines.flow.Flow

/**
 * Live welcome-message, dismissal state, and weekly-challenge for a crew. Consumed by
 * `:feature:feed` to show a dismissible welcome banner and a weekly challenge chip without
 * depending on `:feature:crew`.
 *
 * Lives in `:core:domain` — mirroring [CrewBlindVotingPort] — and is bound in `crewModule`.
 *
 * [observeWelcomeMessage] emits `null` when no message is set or the crew is unreadable.
 * [isWelcomeDismissed] emits `true` once the user has dismissed the banner for this crew.
 * [dismissWelcome] persists the dismissal (fire-and-forget from the screen; errors silently dropped
 * since a failed dismissal just re-shows the banner).
 * [observeWeeklyChallenge] emits the raw challenge text + setAt epoch millis; both `null` when no
 * challenge is set. The feed performs the 7-day expiry check client-side.
 */
interface CrewWelcomePort {
    /** Emits the crew's current welcome message, or `null` when none is set. */
    fun observeWelcomeMessage(crewId: CrewId): Flow<String?>

    /** Emits `true` once the user has dismissed the welcome banner for the given crew. */
    fun isWelcomeDismissed(crewId: CrewId): Flow<Boolean>

    /** Persists the dismissal for [crewId] so the banner does not re-appear. */
    suspend fun dismissWelcome(crewId: CrewId)

    /**
     * Emits the crew's current weekly challenge as a [WeeklyChallengeSnapshot], or `null` when no
     * challenge is set. The feed checks the 7-day expiry client-side using the snapshot's
     * [WeeklyChallengeSnapshot.setAtMillis].
     */
    fun observeWeeklyChallenge(crewId: CrewId): Flow<WeeklyChallengeSnapshot?>
}

/**
 * Primitive snapshot of a crew's weekly challenge — no domain VO references so the port stays in
 * `:core:domain` without pulling in `:feature:crew` types.
 *
 * @param text The trimmed challenge text (never blank — absent challenge is represented by `null`
 *   in [CrewWelcomePort.observeWeeklyChallenge]).
 * @param setAtMillis The epoch-millisecond timestamp when the challenge was set; used by the feed
 *   to compute `now - setAt < 7 days`.
 */
data class WeeklyChallengeSnapshot(val text: String, val setAtMillis: Long)
