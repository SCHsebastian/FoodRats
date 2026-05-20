package es.schsebastian.foodrats.feature.auth.i18n

import es.schsebastian.foodrats.core.i18n.StringKey
import foodrats.feature.auth.generated.resources.Res
import foodrats.feature.auth.generated.resources.auth_error_account_disabled
import foodrats.feature.auth.generated.resources.auth_error_email_in_use
import foodrats.feature.auth.generated.resources.auth_error_email_invalid
import foodrats.feature.auth.generated.resources.auth_error_missing_server_client_id
import foodrats.feature.auth.generated.resources.auth_error_network
import foodrats.feature.auth.generated.resources.auth_error_no_google_accounts
import foodrats.feature.auth.generated.resources.auth_error_password_too_short
import foodrats.feature.auth.generated.resources.auth_error_play_services
import foodrats.feature.auth.generated.resources.auth_error_unknown
import foodrats.feature.auth.generated.resources.auth_error_user_cancelled
import foodrats.feature.auth.generated.resources.auth_error_wrong_credentials
import foodrats.feature.auth.generated.resources.auth_field_email
import foodrats.feature.auth.generated.resources.auth_field_password
import foodrats.feature.auth.generated.resources.auth_mode_signin_cta
import foodrats.feature.auth.generated.resources.auth_mode_signup_cta
import foodrats.feature.auth.generated.resources.auth_or_divider
import foodrats.feature.auth.generated.resources.auth_signin_continue_google
import foodrats.feature.auth.generated.resources.auth_signin_footer
import foodrats.feature.auth.generated.resources.auth_signin_highlight_feed
import foodrats.feature.auth.generated.resources.auth_signin_highlight_rate
import foodrats.feature.auth.generated.resources.auth_signin_highlight_share
import foodrats.feature.auth.generated.resources.auth_signin_subtitle
import foodrats.feature.auth.generated.resources.auth_signin_title
import foodrats.feature.auth.generated.resources.auth_toggle_to_signin
import foodrats.feature.auth.generated.resources.auth_toggle_to_signup
import org.jetbrains.compose.resources.StringResource

enum class AuthStringKey(override val resourceId: StringResource) : StringKey {
    SignInTitle(Res.string.auth_signin_title),
    SignInSubtitle(Res.string.auth_signin_subtitle),
    ContinueWithGoogle(Res.string.auth_signin_continue_google),
    HighlightShare(Res.string.auth_signin_highlight_share),
    HighlightRate(Res.string.auth_signin_highlight_rate),
    HighlightFeed(Res.string.auth_signin_highlight_feed),
    Footer(Res.string.auth_signin_footer),
    FieldEmail(Res.string.auth_field_email),
    FieldPassword(Res.string.auth_field_password),
    ModeSignInCta(Res.string.auth_mode_signin_cta),
    ModeSignUpCta(Res.string.auth_mode_signup_cta),
    ToggleToSignUp(Res.string.auth_toggle_to_signup),
    ToggleToSignIn(Res.string.auth_toggle_to_signin),
    OrDivider(Res.string.auth_or_divider),
    ErrorUserCancelled(Res.string.auth_error_user_cancelled),
    ErrorNoGoogleAccounts(Res.string.auth_error_no_google_accounts),
    ErrorPlayServices(Res.string.auth_error_play_services),
    ErrorNetwork(Res.string.auth_error_network),
    ErrorAccountDisabled(Res.string.auth_error_account_disabled),
    ErrorMissingServerClientId(Res.string.auth_error_missing_server_client_id),
    ErrorUnknown(Res.string.auth_error_unknown),
    ErrorEmailInvalid(Res.string.auth_error_email_invalid),
    ErrorPasswordTooShort(Res.string.auth_error_password_too_short),
    ErrorEmailInUse(Res.string.auth_error_email_in_use),
    ErrorWrongCredentials(Res.string.auth_error_wrong_credentials),
}
