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
