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

    /**
     * Emits the crew's hero/banner image URL (C9), or `null` when no banner is set or the crew is
     * unreadable. The URL is a short-lived signed URL resolved via `ImageUrlPort.resolve` at read
     * time — callers must not cache it beyond a single screen composition.
     *
     * Feed consumes this to render a banner atop the meal list without depending on `:feature:crew`.
     */
    fun observeBannerImageUrl(crewId: CrewId): Flow<String?>

    /**
     * Emits the STABLE Storage object path to use as a Coil cache key for the banner (IMAGE-2), or
     * `""` when no banner is set, the banner is a legacy fixed-path one, or the crew is unreadable.
     *
     * A content-versioned banner path (`crew_banners/{c}/{token}.jpg`) is upload-immutable, so keying
     * Coil's disk + memory caches on it lets the cached bytes serve every future re-signed URL —
     * the same trick the feed uses for plates. The signed URL from [observeBannerImageUrl] rotates
     * (at most) once per server TTL; keying on the stable path survives that rotation. A legacy fixed
     * `crew_banners/{c}/banner.jpg` is overwritten in place (mutable), so it emits `""` and the caller
     * falls back to URL-derived keying. Never emits `null`, mirroring [observeBannerFocalY].
     */
    fun observeBannerCacheKey(crewId: CrewId): Flow<String>

    /**
     * Emits the crew's banner vertical focal point (C9) in `0f..1f` — `0` anchors the crop to the
     * top of the image, `0.5` centers it, `1` anchors the bottom. Owner-set via the crew settings
     * reposition control; lets the fixed-height feed crop show the part of the image the owner chose.
     * Defaults to `0.5f` (center) when unset (pre-reposition crews) or the crew is unreadable; never
     * emits `null`, mirroring [observeScoreStyle].
     */
    fun observeBannerFocalY(crewId: CrewId): Flow<Float>
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
