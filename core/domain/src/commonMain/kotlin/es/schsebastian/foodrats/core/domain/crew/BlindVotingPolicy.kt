package es.schsebastian.foodrats.core.domain.crew

/**
 * The blind-voting masking rule: a pure decision used by feed presentation to mask a
 * meal's author identity (name + avatar) until the viewer has cast their own score.
 *
 * Reusable across feed mapping and any catalog/test scenario; intentionally vendor-,
 * platform- and presentation-free so it can be unit-tested in isolation. The flag it
 * reads is [es.schsebastian.foodrats.feature.crew]'s `Crew.blindVoting`, carried to
 * feed by [CrewBlindVotingPort].
 */
object BlindVotingPolicy {

    /**
     * `true` when the viewer must NOT yet see who cooked [the meal].
     *
     * Masked only when ALL hold:
     *  - [blindVoting] is on for the crew, AND
     *  - the viewer is NOT the author ([isAuthor] = `false`) — an author always sees
     *    their own meal un-masked, AND
     *  - the viewer has NOT yet voted ([viewerHasVoted] = `false`) — casting a score
     *    reveals the author.
     *
     * Voters' identities in the meal's votes are unaffected; this governs only the
     * meal author's name/avatar. Reveal-after-window-close is a presentation concern
     * (it depends on the rating-window state feed already tracks) and is handled by
     * the caller passing the appropriate inputs — when the window has closed the
     * caller treats the meal as no longer maskable.
     */
    fun shouldMaskAuthor(
        blindVoting: Boolean,
        isAuthor: Boolean,
        viewerHasVoted: Boolean,
    ): Boolean = blindVoting && !isAuthor && !viewerHasVoted
}
