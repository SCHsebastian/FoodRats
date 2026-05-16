package es.schsebastian.foodrats.feature.stats.domain.error

sealed interface StatsError {
    sealed interface Session : StatsError {
        data object NoActiveCrew : Session
        data object NotSignedIn : Session
    }
    sealed interface Read : StatsError {
        data object Unauthorized : Read
        data object CrewNotFound : Read
        data object Unavailable : Read
    }
}
