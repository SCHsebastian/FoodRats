package es.schsebastian.foodrats.feature.feed.presentation

import es.schsebastian.foodrats.core.domain.meal.MealDeleteError
import es.schsebastian.foodrats.core.i18n.StringKey
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey

fun MealDeleteError.toStringKey(): StringKey = when (this) {
    MealDeleteError.NotAuthorOrOwner -> FeedStringKey.DeleteMealErrorUnauthorized
    MealDeleteError.NotFound         -> FeedStringKey.DeleteMealErrorNotFound
    MealDeleteError.Unavailable      -> FeedStringKey.DeleteMealErrorUnavailable
}
