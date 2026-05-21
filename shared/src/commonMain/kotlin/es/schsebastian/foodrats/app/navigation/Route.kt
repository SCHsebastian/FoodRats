package es.schsebastian.foodrats.app.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object Splash : Route

    @Serializable data object SignIn : Route

    @Serializable data object NotificationPermission : Route

    @Serializable data object CrewPicker : Route
    @Serializable data class CrewSettings(val crewId: String) : Route
    @Serializable data object Profile : Route

    @Serializable data object Main : Route               // bottom-nav scaffold (Feed + Stats)

    @Serializable data object CaptureMeal : Route
    @Serializable data object ComposePlate : Route
    @Serializable data object PublishMeal : Route

    @Serializable data class MealDetail(val mealId: String, val dayIso: String) : Route
}

/** Inner routes inside the [Route.Main] bottom-nav graph. */
sealed interface MainTab {
    @Serializable data object Feed : Route
    @Serializable data object Stats : Route
}
