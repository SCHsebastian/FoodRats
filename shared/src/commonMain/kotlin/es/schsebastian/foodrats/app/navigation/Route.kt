package es.schsebastian.foodrats.app.navigation

import kotlinx.serialization.Serializable

/**
 * Typed navigation destinations.
 *
 * Every route declares its access level by implementing [Route.Public] (reachable while
 * signed-out) or [Route.Protected] (requires an authenticated session). The split makes
 * auth-gating exhaustive at compile time and lets a deep link into a protected screen be
 * intercepted while signed-out and resumed after sign-in (see [RootNavViewModel]).
 */
sealed interface Route {

    /** Reachable without an authenticated session. */
    sealed interface Public : Route

    /** Requires an authenticated session; entry is gated by `RootNavViewModel`. */
    sealed interface Protected : Route

    @Serializable data object Splash : Public
    @Serializable data object SignIn : Public

    /**
     * Embedded End User License Agreement (UGC compliance §6). [Public]: reachable from the SignIn
     * links pre-auth (Apple requires the EULA + acceptable-use terms be readable before sign-up) and
     * again from Profile.
     */
    @Serializable data object Eula : Public

    /**
     * Embedded Community Guidelines (UGC compliance §6) — the user-facing statement of prohibited
     * content, the report/block mechanisms, and the moderation SLA. [Public] for the same reasons as
     * [Eula].
     */
    @Serializable data object CommunityGuidelines : Public

    @Serializable data object NotificationPermission : Protected
    @Serializable data object Consent : Protected

    /**
     * EULA re-acceptance gate shown when the user has never accepted or when [CURRENT_EULA_VERSION]
     * was bumped since their last acceptance (UGC compliance §6). [Protected]: a signed-out user is
     * routed to [SignIn] first; the gate only fires for authenticated users who need to re-accept.
     * Once accepted, the stage machine re-emits [RootStage.Ready] and navigation proceeds to [Main].
     */
    @Serializable data object EulaGate : Protected
    @Serializable data object CrewPicker : Protected
    @Serializable data class CrewSettings(val crewId: String) : Protected

    /**
     * Accept-an-invite preview (roadmap §3.2), reached from a `…/invite/{code}` deep link or QR
     * scan. Shows the crew (name + member count) with a Join button that runs the existing
     * join-by-code path. [Protected]: reading + joining the crew both require an authenticated
     * session, so a pre-auth invite tap is stashed in `RootNavState.pendingDeepLink` and replayed
     * after sign-in (intercept-then-resume in `RootNavViewModel`) — the invite survives the gate.
     */
    @Serializable data class InvitePreview(val code: String) : Protected
    @Serializable data object Profile : Protected
    @Serializable data object Achievements : Protected

    /** The signed-in user's block list (UGC compliance §5), reached from Profile. */
    @Serializable data object BlockedUsers : Protected

    @Serializable data object Main : Protected               // bottom-nav scaffold (Feed + Stats)

    @Serializable data object CaptureMeal : Protected
    @Serializable data object ComposePlate : Protected
    @Serializable data object SelectIngredients : Protected

    @Serializable data class MealDetail(val mealId: String, val dayIso: String) : Protected

    /**
     * The weekly-recap story player (roadmap §2.4). [weekStart] is the ISO Monday of the recapped
     * week (carried from the digest deep link `…/digest/{weekStart}`); the client derives the recap
     * from its own stats/achievements read paths, so the value is currently informational. From a
     * notification tap [fromNotification] is true; the in-app Stats entry passes false — the player
     * lowers it onto the `digest_story_opened` analytics source.
     */
    @Serializable data class WeeklyStory(
        val weekStart: String,
        val fromNotification: Boolean = true,
    ) : Protected
}

/** Inner routes inside the [Route.Main] bottom-nav graph; all require a session. */
sealed interface MainTab : Route.Protected {
    @Serializable data object Feed : MainTab
    @Serializable data object Passport : MainTab
    @Serializable data object Stats : MainTab
}

/**
 * Whether reaching this destination requires an authenticated session.
 *
 * Exhaustive `when` over the sealed [Route] hierarchy with **no `else`** — adding a new route
 * forces a compile error here until its access level is decided, so auth-gating can never silently
 * default a new screen to "public". This is the single source of truth the root nav gates on
 * (see `RootNavViewModel`); the [Route.Public] / [Route.Protected] markers stay as the documented
 * classification but no longer carry the gating decision alone.
 */
fun Route.requiresSession(): Boolean = when (this) {
    Route.Splash,
    Route.SignIn,
    Route.Eula,
    Route.CommunityGuidelines,
        -> false

    Route.NotificationPermission,
    Route.Consent,
    Route.EulaGate,
    Route.CrewPicker,
    is Route.CrewSettings,
    is Route.InvitePreview,
    Route.Profile,
    Route.Achievements,
    Route.BlockedUsers,
    Route.Main,
    Route.CaptureMeal,
    Route.ComposePlate,
    Route.SelectIngredients,
    is Route.MealDetail,
    is Route.WeeklyStory,
    MainTab.Feed,
    MainTab.Passport,
    MainTab.Stats,
        -> true
}
