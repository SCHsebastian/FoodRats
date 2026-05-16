package es.schsebastian.foodrats.feature.stats.domain.error

sealed interface StatsError {
    sealed interface Aggregation : StatsError {
        data object Unavailable : Aggregation
        data object NotEnoughData : Aggregation
    }
}
