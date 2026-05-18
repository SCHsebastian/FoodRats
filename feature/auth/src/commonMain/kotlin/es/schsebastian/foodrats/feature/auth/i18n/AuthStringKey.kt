package es.schsebastian.foodrats.feature.auth.i18n

import es.schsebastian.foodrats.core.i18n.StringKey
import foodrats.feature.auth.generated.resources.Res
import foodrats.feature.auth.generated.resources.auth_error_account_disabled
import foodrats.feature.auth.generated.resources.auth_error_missing_server_client_id
import foodrats.feature.auth.generated.resources.auth_error_network
import foodrats.feature.auth.generated.resources.auth_error_no_google_accounts
import foodrats.feature.auth.generated.resources.auth_error_play_services
import foodrats.feature.auth.generated.resources.auth_error_unknown
import foodrats.feature.auth.generated.resources.auth_error_user_cancelled
import foodrats.feature.auth.generated.resources.auth_signin_continue_google
import foodrats.feature.auth.generated.resources.auth_signin_subtitle
import foodrats.feature.auth.generated.resources.auth_signin_title
import org.jetbrains.compose.resources.StringResource

enum class AuthStringKey(override val resourceId: StringResource) : StringKey {
    SignInTitle(Res.string.auth_signin_title),
    SignInSubtitle(Res.string.auth_signin_subtitle),
    ContinueWithGoogle(Res.string.auth_signin_continue_google),
    ErrorUserCancelled(Res.string.auth_error_user_cancelled),
    ErrorNoGoogleAccounts(Res.string.auth_error_no_google_accounts),
    ErrorPlayServices(Res.string.auth_error_play_services),
    ErrorNetwork(Res.string.auth_error_network),
    ErrorAccountDisabled(Res.string.auth_error_account_disabled),
    ErrorMissingServerClientId(Res.string.auth_error_missing_server_client_id),
    ErrorUnknown(Res.string.auth_error_unknown),
}
