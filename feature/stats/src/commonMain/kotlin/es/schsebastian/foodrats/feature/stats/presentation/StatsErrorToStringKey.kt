package es.schsebastian.foodrats.feature.stats.presentation

import es.schsebastian.foodrats.feature.stats.domain.error.StatsError
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey

fun StatsError.toStringKey(): StatsStringKey = when (this) {
    StatsError.Session.NoActiveCrew -> StatsStringKey.ErrorNoActiveCrew
    StatsError.Session.NotSignedIn  -> StatsStringKey.ErrorNotSignedIn
    StatsError.Read.Unauthorized    -> StatsStringKey.ErrorUnauthorized
    StatsError.Read.CrewNotFound    -> StatsStringKey.ErrorCrewNotFound
    StatsError.Read.Unavailable     -> StatsStringKey.ErrorUnavailable
}
