package es.schsebastian.foodrats.feature.auth.presentation

import es.schsebastian.foodrats.feature.auth.domain.error.AuthError
import es.schsebastian.foodrats.feature.auth.i18n.AuthStringKey

fun AuthError.toStringKey(): AuthStringKey = when (this) {
    AuthError.GoogleSignIn.UserCancelled            -> AuthStringKey.ErrorUserCancelled
    AuthError.GoogleSignIn.NoGoogleAccountsOnDevice -> AuthStringKey.ErrorNoGoogleAccounts
    AuthError.GoogleSignIn.PlayServicesUnavailable  -> AuthStringKey.ErrorPlayServices
    AuthError.GoogleSignIn.NetworkUnavailable       -> AuthStringKey.ErrorNetwork
    AuthError.GoogleSignIn.MissingServerClientId    -> AuthStringKey.ErrorMissingServerClientId
    AuthError.GoogleSignIn.UnknownClientFailure     -> AuthStringKey.ErrorUnknown
    AuthError.EmailPassword.InvalidEmail            -> AuthStringKey.ErrorEmailInvalid
    AuthError.EmailPassword.WeakPassword            -> AuthStringKey.ErrorPasswordTooShort
    AuthError.EmailPassword.EmailAlreadyInUse       -> AuthStringKey.ErrorEmailInUse
    AuthError.EmailPassword.WrongCredentials        -> AuthStringKey.ErrorWrongCredentials
    AuthError.Firebase.NotSignedIn                  -> AuthStringKey.ErrorUnknown
    AuthError.Firebase.TokenExpired                 -> AuthStringKey.ErrorUnknown
    AuthError.Firebase.AccountDisabled              -> AuthStringKey.ErrorAccountDisabled
    AuthError.Firebase.Unavailable                  -> AuthStringKey.ErrorUnknown
}
