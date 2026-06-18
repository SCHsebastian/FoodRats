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
    sealed interface AppleSignIn : AuthError {
        /**
         * The Sign-in-with-Apple seam is wired end-to-end but the native flow is not yet
         * enabled — the platform client returns this so the UI can say "being built". Remove
         * (or stop returning) this leaf once the real Apple flow + Firebase exchange land.
         */
        data object NotYetAvailable : AppleSignIn
        data object UserCancelled : AppleSignIn
        data object NetworkUnavailable : AppleSignIn
        /** Apple returned a malformed/empty identity token. */
        data object InvalidResponse : AppleSignIn
        data object UnknownClientFailure : AppleSignIn
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
