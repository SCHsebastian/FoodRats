package es.schsebastian.foodrats.feature.auth.domain.error

sealed interface AuthError {
    sealed interface GoogleSignIn : AuthError {
        data object UserCancelled : GoogleSignIn
        data object NoGoogleAccountsOnDevice : GoogleSignIn
        data object PlayServicesUnavailable : GoogleSignIn
        data object NetworkUnavailable : GoogleSignIn
        data object UnknownClientFailure : GoogleSignIn
    }
    sealed interface Firebase : AuthError {
        data object NotSignedIn : Firebase
        data object TokenExpired : Firebase
        data object AccountDisabled : Firebase
        data object Unavailable : Firebase
    }
}
