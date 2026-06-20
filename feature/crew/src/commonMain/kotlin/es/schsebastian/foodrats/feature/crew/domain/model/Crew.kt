package es.schsebastian.foodrats.feature.crew.domain.model

import es.schsebastian.foodrats.core.domain.crew.CrewScoreStyle
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import kotlin.time.Instant
import es.schsebastian.foodrats.feature.crew.domain.model.CrewTagline
import es.schsebastian.foodrats.feature.crew.domain.model.WelcomeMessage
import es.schsebastian.foodrats.feature.crew.domain.model.WeeklyChallenge

/**
 * The Crew aggregate root. The invariant this context exists to protect is the
 * 3..8 membership cap ([CrewSize]) — enforced here in-memory by [addMember], and
 * atomically server-side by the join transaction in `CrewFirestoreDataSource`.
 * Both reference [CrewSize.MAX] so the cap has a single source of truth.
 */
data class Crew(
    val id: CrewId,
    val name: String,
    val code: CrewCode,
    val ownerId: AccountId,
    val createdAt: Instant,
    val members: List<Member>,
    /**
     * When `true`, a meal's author identity is hidden from a crewmate until that
     * crewmate has cast their own [es.schsebastian.foodrats.core.domain.meal.Score]
     * — so members aren't anchored by who cooked it. An owner-settable crew policy;
     * defaults to `false` so existing crews keep current (un-blind) behavior.
     * The masking rule itself is
     * [es.schsebastian.foodrats.core.domain.crew.BlindVotingPolicy]; feed reads this
     * flag through [es.schsebastian.foodrats.core.domain.crew.CrewBlindVotingPort].
     */
    val blindVoting: Boolean = false,
    /**
     * Optional owner-set tagline — a short blurb displayed on the crew card and join screen
     * ("only home-cooked", "no Scores after midnight"). `null` means no tagline is set.
     * Managed by [es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewTaglineUseCase].
     * Capped at [CrewTagline.MAX_LEN] = 120 chars.
     */
    val tagline: CrewTagline? = null,
    /**
     * Optional owner-set onboarding message shown as a dismissible banner to new joiners the first
     * time they open the crew feed. `null` means no message is set.
     * Managed by [es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewWelcomeMessageUseCase].
     * Capped at [WelcomeMessage.MAX_LEN] = 200 chars.
     */
    val welcomeMessage: WelcomeMessage? = null,
    /**
     * Optional owner-set weekly theme pinned to the crew feed header — e.g. "Taco Tuesday",
     * "Soup week". `null` means no challenge is set. Auto-expires 7 days after [weeklyChallengeSetAt]
     * (client-side check in the feed). Managed by
     * [es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewWeeklyChallengeUseCase].
     * Capped at [WeeklyChallenge.MAX_LEN] = 80 chars.
     */
    val weeklyChallenge: WeeklyChallenge? = null,
    /**
     * The instant when [weeklyChallenge] was last set. Used by the feed for the 7-day expiry
     * check. `null` when no challenge is set.
     */
    val weeklyChallengeSetAt: Instant? = null,
    /**
     * The crew's chosen Score vocabulary (C8). Defaults to [CrewScoreStyle.Stars] so pre-C8 crews
     * behave exactly as before. Owner-settable via
     * [es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewScoreStyleUseCase].
     */
    val scoreStyle: CrewScoreStyle = CrewScoreStyle.Stars,
) {
    val size: Int get() = members.size

    /**
     * Adds [member] to the crew, enforcing the membership cap and uniqueness.
     * Returns [CrewError.Membership.AlreadyMember] if the account is already a member,
     * or [CrewError.Membership.Full] if the crew is at [CrewSize.MAX]. The atomic
     * authoritative check lives in the join transaction; this is the in-memory model
     * used by domain logic and tests.
     */
    fun addMember(member: Member): Result<Crew, CrewError> {
        if (members.any { it.accountId == member.accountId }) {
            return Result.failure(CrewError.Membership.AlreadyMember)
        }
        if (!CrewSize.canAdd(size)) {
            return Result.failure(CrewError.Membership.Full)
        }
        return Result.success(copy(members = members + member))
    }

    companion object {
        /** Constructs a Crew from already-validated parts (e.g. when reconstituting from storage). */
        fun of(
            id: CrewId,
            name: String,
            code: CrewCode,
            ownerId: AccountId,
            createdAt: Instant,
            members: List<Member>,
            blindVoting: Boolean = false,
            tagline: CrewTagline? = null,
            welcomeMessage: WelcomeMessage? = null,
            weeklyChallenge: WeeklyChallenge? = null,
            weeklyChallengeSetAt: Instant? = null,
            scoreStyle: CrewScoreStyle = CrewScoreStyle.Stars,
        ): Crew = Crew(id, name, code, ownerId, createdAt, members, blindVoting, tagline, welcomeMessage, weeklyChallenge, weeklyChallengeSetAt, scoreStyle)
    }
}
