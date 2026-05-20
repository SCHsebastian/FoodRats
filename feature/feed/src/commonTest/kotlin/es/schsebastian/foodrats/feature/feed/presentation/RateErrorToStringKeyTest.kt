package es.schsebastian.foodrats.feature.feed.presentation

import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import kotlin.test.Test
import kotlin.test.assertEquals

class RateErrorToStringKeyTest {
    @Test fun all_variants_mapped() {
        assertEquals(FeedStringKey.RateErrorCannotRateOwnMeal, RateError.CannotRateOwnMeal.toStringKey())
        assertEquals(FeedStringKey.RateErrorAlreadyRated, RateError.AlreadyRated.toStringKey())
        assertEquals(FeedStringKey.RateErrorWindowClosed, RateError.RatingWindowClosed.toStringKey())
        assertEquals(FeedStringKey.RateErrorUnauthorized, RateError.Unauthorized.toStringKey())
        assertEquals(FeedStringKey.RateErrorOffline, RateError.Offline.toStringKey())
        assertEquals(FeedStringKey.RateErrorUnavailable, RateError.RateUnavailable.toStringKey())
    }
}
