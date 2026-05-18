package es.schsebastian.foodrats.feature.auth.presentation

import es.schsebastian.foodrats.feature.auth.domain.error.AuthError
import es.schsebastian.foodrats.feature.auth.i18n.AuthStringKey
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthErrorToStringKeyTest {
    @Test fun userCancelled_maps_to_userCancelled_string() =
        assertEquals(AuthStringKey.ErrorUserCancelled, AuthError.GoogleSignIn.UserCancelled.toStringKey())
    @Test fun noAccounts_maps_to_noAccounts_string() =
        assertEquals(AuthStringKey.ErrorNoGoogleAccounts, AuthError.GoogleSignIn.NoGoogleAccountsOnDevice.toStringKey())
    @Test fun playServices_maps_to_playServices_string() =
        assertEquals(AuthStringKey.ErrorPlayServices, AuthError.GoogleSignIn.PlayServicesUnavailable.toStringKey())
    @Test fun network_maps_to_network_string() =
        assertEquals(AuthStringKey.ErrorNetwork, AuthError.GoogleSignIn.NetworkUnavailable.toStringKey())
    @Test fun missingServerClientId_maps_to_missingServerClientId_string() =
        assertEquals(AuthStringKey.ErrorMissingServerClientId, AuthError.GoogleSignIn.MissingServerClientId.toStringKey())
    @Test fun unknownClient_maps_to_unknown_string() =
        assertEquals(AuthStringKey.ErrorUnknown, AuthError.GoogleSignIn.UnknownClientFailure.toStringKey())
    @Test fun firebaseAccountDisabled_maps_to_accountDisabled_string() =
        assertEquals(AuthStringKey.ErrorAccountDisabled, AuthError.Firebase.AccountDisabled.toStringKey())
    @Test fun firebaseNotSignedIn_maps_to_unknown_string() =
        assertEquals(AuthStringKey.ErrorUnknown, AuthError.Firebase.NotSignedIn.toStringKey())
    @Test fun firebaseTokenExpired_maps_to_unknown_string() =
        assertEquals(AuthStringKey.ErrorUnknown, AuthError.Firebase.TokenExpired.toStringKey())
    @Test fun firebaseUnavailable_maps_to_unknown_string() =
        assertEquals(AuthStringKey.ErrorUnknown, AuthError.Firebase.Unavailable.toStringKey())
}
