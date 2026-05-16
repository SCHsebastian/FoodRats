package es.schsebastian.foodrats.feature.feed.domain.error

sealed interface FeedError {
    sealed interface Read : FeedError {
        data object Unauthorized : Read
        data object Unavailable : Read
    }
}
