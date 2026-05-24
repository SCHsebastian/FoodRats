package es.schsebastian.foodrats.feature.feed.presentation

import es.schsebastian.foodrats.core.domain.meal.MealDeleteError
import es.schsebastian.foodrats.core.i18n.StringKey
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey

// TODO(i18n): dedicated copy for meal-delete errors. Reusing the comment-error
// strings for now ("not allowed" / "couldn't, try again") to avoid adding
// resources before the delete UI lands.
fun MealDeleteError.toStringKey(): StringKey = when (this) {
    MealDeleteError.NotAuthorOrOwner -> FeedStringKey.CommentsErrorUnauthorized
    MealDeleteError.NotFound         -> FeedStringKey.CommentsErrorUnavailable
    MealDeleteError.Unavailable      -> FeedStringKey.CommentsErrorUnavailable
}
