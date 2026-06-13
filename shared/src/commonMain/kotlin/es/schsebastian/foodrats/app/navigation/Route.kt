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

    @Serializable data object NotificationPermission : Protected
    @Serializable data object CrewPicker : Protected
    @Serializable data class CrewSettings(val crewId: String) : Protected
    @Serializable data object Profile : Protected

    @Serializable data object Main : Protected               // bottom-nav scaffold (Feed + Stats)

    @Serializable data object CaptureMeal : Protected
    @Serializable data object ComposePlate : Protected
    @Serializable data object SelectIngredients : Protected

    @Serializable data class MealDetail(val mealId: String, val dayIso: String) : Protected
}

/** Inner routes inside the [Route.Main] bottom-nav graph; all require a session. */
sealed interface MainTab : Route.Protected {
    @Serializable data object Feed : MainTab
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
        -> false

    Route.NotificationPermission,
    Route.CrewPicker,
    is Route.CrewSettings,
    Route.Profile,
    Route.Main,
    Route.CaptureMeal,
    Route.ComposePlate,
    Route.SelectIngredients,
    is Route.MealDetail,
    MainTab.Feed,
    MainTab.Stats,
        -> true
}
