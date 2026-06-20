package es.schsebastian.foodrats.core.domain.crew

import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.coroutines.flow.Flow

/**
 * Live welcome-message, dismissal state, weekly-challenge, and score style for a crew. Consumed by
 * `:feature:feed` to show a dismissible welcome banner, a weekly challenge chip, and the
 * crew-chosen Score vocabulary — all without depending on `:feature:crew`.
 *
 * Lives in `:core:domain` — mirroring [CrewBlindVotingPort] — and is bound in `crewModule`.
 *
 * [observeWelcomeMessage] emits `null` when no message is set or the crew is unreadable.
 * [isWelcomeDismissed] emits `true` once the user has dismissed the banner for this crew.
 * [dismissWelcome] persists the dismissal (fire-and-forget from the screen; errors silently dropped
 * since a failed dismissal just re-shows the banner).
 * [observeWeeklyChallenge] emits the raw challenge text + setAt epoch millis; both `null` when no
 * challenge is set. The feed performs the 7-day expiry check client-side.
 * [observeScoreStyle] emits the crew's chosen [CrewScoreStyle]; defaults to [CrewScoreStyle.Stars]
 * when the field is absent (pre-C8 crews) or unreadable.
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

    /**
     * Emits the crew's chosen Score display vocabulary (C8). Defaults to [CrewScoreStyle.Stars]
     * for pre-C8 crews (no stored field ⇒ legacy behavior preserved). Never emits `null`.
     */
    fun observeScoreStyle(crewId: CrewId): Flow<CrewScoreStyle>
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
