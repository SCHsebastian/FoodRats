package es.schsebastian.foodrats.feature.feed.presentation

import es.schsebastian.foodrats.feature.feed.domain.error.FeedError
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedErrorToStringKeyTest {
    @Test fun noActiveCrew() = assertEquals(FeedStringKey.NoActiveCrewHeadline, FeedError.Session.NoActiveCrew.toStringKey())
    @Test fun notSignedIn() = assertEquals(FeedStringKey.ErrorNotSignedIn, FeedError.Session.NotSignedIn.toStringKey())
    @Test fun unauthorized() = assertEquals(FeedStringKey.ErrorUnauthorized, FeedError.Read.Unauthorized.toStringKey())
    @Test fun crewNotFound() = assertEquals(FeedStringKey.ErrorCrewNotFound, FeedError.Read.CrewNotFound.toStringKey())
    @Test fun unavailable() = assertEquals(FeedStringKey.ErrorUnavailable, FeedError.Read.Unavailable.toStringKey())
}
