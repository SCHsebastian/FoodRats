package es.schsebastian.foodrats.app.nav

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object SignIn : Route
    @Serializable data object CrewPicker : Route
    @Serializable data object Feed : Route
    @Serializable data object CaptureMeal : Route
    @Serializable data object ComposePlate : Route
    @Serializable data object PublishMeal : Route
    @Serializable data object Stats : Route
    @Serializable data object NotificationPermission : Route
}
