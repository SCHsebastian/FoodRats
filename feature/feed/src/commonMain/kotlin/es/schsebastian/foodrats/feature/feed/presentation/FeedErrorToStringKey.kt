package es.schsebastian.foodrats.feature.feed.presentation

import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.feature.feed.domain.error.FeedError
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey

fun FeedError.toStringKey(): FeedStringKey = when (this) {
    FeedError.Session.NoActiveCrew -> FeedStringKey.NoActiveCrewHeadline
    FeedError.Session.NotSignedIn  -> FeedStringKey.ErrorNotSignedIn
    FeedError.Read.Unauthorized    -> FeedStringKey.ErrorUnauthorized
    FeedError.Read.CrewNotFound    -> FeedStringKey.ErrorCrewNotFound
    FeedError.Read.Unavailable     -> FeedStringKey.ErrorUnavailable
}

fun RateError.toStringKey(): FeedStringKey = when (this) {
    RateError.CannotRateOwnMeal  -> FeedStringKey.RateErrorCannotRateOwnMeal
    RateError.AlreadyRated       -> FeedStringKey.RateErrorAlreadyRated
    RateError.RatingWindowClosed -> FeedStringKey.RateErrorWindowClosed
    RateError.Unauthorized       -> FeedStringKey.RateErrorUnauthorized
    RateError.RateUnavailable    -> FeedStringKey.RateErrorUnavailable
}
