package es.schsebastian.foodrats.feature.stats.presentation

import es.schsebastian.foodrats.feature.stats.domain.error.StatsError
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey
import kotlin.test.Test
import kotlin.test.assertEquals

class StatsErrorToStringKeyTest {
    @Test fun noActiveCrew()    = assertEquals(StatsStringKey.ErrorNoActiveCrew, StatsError.Session.NoActiveCrew.toStringKey())
    @Test fun notSignedIn()     = assertEquals(StatsStringKey.ErrorNotSignedIn, StatsError.Session.NotSignedIn.toStringKey())
    @Test fun unauthorized()    = assertEquals(StatsStringKey.ErrorUnauthorized, StatsError.Read.Unauthorized.toStringKey())
    @Test fun crewNotFound()    = assertEquals(StatsStringKey.ErrorCrewNotFound, StatsError.Read.CrewNotFound.toStringKey())
    @Test fun unavailable()     = assertEquals(StatsStringKey.ErrorUnavailable, StatsError.Read.Unavailable.toStringKey())
}
