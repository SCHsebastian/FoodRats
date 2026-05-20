package es.schsebastian.foodrats.feature.auth.domain.error

sealed interface AuthError {
    sealed interface GoogleSignIn : AuthError {
        data object UserCancelled : GoogleSignIn
        data object NoGoogleAccountsOnDevice : GoogleSignIn
        data object PlayServicesUnavailable : GoogleSignIn
        data object NetworkUnavailable : GoogleSignIn
        data object MissingServerClientId : GoogleSignIn
        data object UnknownClientFailure : GoogleSignIn
    }
    sealed interface EmailPassword : AuthError {
        /** Email format doesn't match a valid address. Caught client-side before the network call. */
        data object InvalidEmail : EmailPassword
        /** Password fails the minimum strength rule (Firebase default: 6 chars). */
        data object WeakPassword : EmailPassword
        /** Sign-up failed because the address is already taken. */
        data object EmailAlreadyInUse : EmailPassword
        /** Sign-in rejected (wrong password or unknown user). Deliberately ambiguous to avoid user-enumeration leaks. */
        data object WrongCredentials : EmailPassword
    }
    sealed interface Firebase : AuthError {
        data object NotSignedIn : Firebase
        data object TokenExpired : Firebase
        data object AccountDisabled : Firebase
        data object Unavailable : Firebase
    }
}
