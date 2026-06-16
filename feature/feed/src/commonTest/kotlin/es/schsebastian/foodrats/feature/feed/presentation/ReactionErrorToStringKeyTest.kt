package es.schsebastian.foodrats.feature.feed.presentation

import es.schsebastian.foodrats.core.domain.meal.ReactionError
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import kotlin.test.Test
import kotlin.test.assertEquals

class ReactionErrorToStringKeyTest {
    @Test fun all_variants_mapped() {
        assertEquals(FeedStringKey.ReactionErrorUnauthorized, ReactionError.Read.Unauthorized.toStringKey())
        assertEquals(FeedStringKey.ReactionErrorUnavailable, ReactionError.Read.Unavailable.toStringKey())
        assertEquals(FeedStringKey.ReactionErrorUnauthorized, ReactionError.Toggle.Unauthorized.toStringKey())
        assertEquals(FeedStringKey.ReactionErrorMealNotFound, ReactionError.Toggle.MealNotFound.toStringKey())
        assertEquals(FeedStringKey.ReactionErrorOffline, ReactionError.Toggle.Offline.toStringKey())
        assertEquals(FeedStringKey.ReactionErrorUnavailable, ReactionError.Toggle.Unavailable.toStringKey())
    }
}
