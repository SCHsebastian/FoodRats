package es.schsebastian.foodrats.feature.feed.domain.error

sealed interface FeedError {

    /** The user hasn't selected (or doesn't yet have) an active crew. */
    sealed interface Session : FeedError {
        data object NoActiveCrew : Session
        data object NotSignedIn : Session
    }

    sealed interface Read : FeedError {
        data object Unauthorized : Read
        data object CrewNotFound : Read
        data object Unavailable : Read
    }
}
