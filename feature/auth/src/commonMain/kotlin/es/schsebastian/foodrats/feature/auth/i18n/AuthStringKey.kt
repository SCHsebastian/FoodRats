package es.schsebastian.foodrats.feature.auth.i18n

import es.schsebastian.foodrats.core.i18n.StringKey
import foodrats.feature.auth.generated.resources.Res
import foodrats.feature.auth.generated.resources.auth_delete_account_confirm_cta
import foodrats.feature.auth.generated.resources.auth_delete_account_dialog_body
import foodrats.feature.auth.generated.resources.auth_delete_account_dialog_cancel
import foodrats.feature.auth.generated.resources.auth_delete_account_dialog_confirm
import foodrats.feature.auth.generated.resources.auth_delete_account_dialog_title
import foodrats.feature.auth.generated.resources.auth_delete_account_error_backend
import foodrats.feature.auth.generated.resources.auth_delete_account_error_not_implemented
import foodrats.feature.auth.generated.resources.auth_delete_account_error_ownership
import foodrats.feature.auth.generated.resources.auth_delete_account_error_phrase
import foodrats.feature.auth.generated.resources.auth_delete_account_in_flight
import foodrats.feature.auth.generated.resources.auth_delete_account_intro
import foodrats.feature.auth.generated.resources.auth_delete_account_phrase_label
import foodrats.feature.auth.generated.resources.auth_delete_account_phrase_template
import foodrats.feature.auth.generated.resources.auth_delete_account_title
import foodrats.feature.auth.generated.resources.auth_delete_account_warning_crews
import foodrats.feature.auth.generated.resources.auth_delete_account_warning_irreversible
import foodrats.feature.auth.generated.resources.auth_delete_account_warning_meals
import foodrats.feature.auth.generated.resources.auth_delete_account_warning_ratings
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
import foodrats.feature.auth.generated.resources.auth_profile_account_section
import foodrats.feature.auth.generated.resources.auth_profile_avatar_empty_bytes
import foodrats.feature.auth.generated.resources.auth_profile_avatar_uploading
import foodrats.feature.auth.generated.resources.auth_profile_backend_unavailable
import foodrats.feature.auth.generated.resources.auth_profile_change_avatar_cta
import foodrats.feature.auth.generated.resources.auth_profile_danger_zone_section
import foodrats.feature.auth.generated.resources.auth_profile_danger_zone_subtitle
import foodrats.feature.auth.generated.resources.auth_profile_delete_account_row
import foodrats.feature.auth.generated.resources.auth_profile_delete_account_subtitle
import foodrats.feature.auth.generated.resources.auth_profile_display_name_blank
import foodrats.feature.auth.generated.resources.auth_profile_display_name_label
import foodrats.feature.auth.generated.resources.auth_profile_display_name_too_long
import foodrats.feature.auth.generated.resources.auth_profile_identity_section
import foodrats.feature.auth.generated.resources.auth_profile_language_option_en
import foodrats.feature.auth.generated.resources.auth_profile_language_option_es
import foodrats.feature.auth.generated.resources.auth_profile_language_option_system
import foodrats.feature.auth.generated.resources.auth_profile_language_persist_failed
import foodrats.feature.auth.generated.resources.auth_profile_language_picker_title
import foodrats.feature.auth.generated.resources.auth_profile_language_row
import foodrats.feature.auth.generated.resources.auth_profile_notifications_open_system_settings_cta
import foodrats.feature.auth.generated.resources.auth_profile_notifications_permission_denied
import foodrats.feature.auth.generated.resources.auth_profile_notifications_permission_denied_forever
import foodrats.feature.auth.generated.resources.auth_profile_notifications_persist_failed
import foodrats.feature.auth.generated.resources.auth_profile_notifications_row
import foodrats.feature.auth.generated.resources.auth_profile_notifications_subtitle_off
import foodrats.feature.auth.generated.resources.auth_profile_notifications_subtitle_on
import foodrats.feature.auth.generated.resources.auth_profile_preferences_section
import foodrats.feature.auth.generated.resources.auth_profile_save
import foodrats.feature.auth.generated.resources.auth_profile_sign_out_cta
import foodrats.feature.auth.generated.resources.auth_profile_sign_out_failed
import foodrats.feature.auth.generated.resources.auth_profile_signed_in_as_label
import foodrats.feature.auth.generated.resources.auth_profile_theme_option_dark
import foodrats.feature.auth.generated.resources.auth_profile_theme_option_light
import foodrats.feature.auth.generated.resources.auth_profile_theme_option_system
import foodrats.feature.auth.generated.resources.auth_profile_theme_persist_failed
import foodrats.feature.auth.generated.resources.auth_profile_theme_picker_title
import foodrats.feature.auth.generated.resources.auth_profile_theme_row
import foodrats.feature.auth.generated.resources.auth_profile_back_cta
import foodrats.feature.auth.generated.resources.auth_profile_title
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
    ProfileTitle(Res.string.auth_profile_title),
    ProfileBackCta(Res.string.auth_profile_back_cta),
    ProfileIdentitySection(Res.string.auth_profile_identity_section),
    ProfilePreferencesSection(Res.string.auth_profile_preferences_section),
    ProfileDangerZoneSection(Res.string.auth_profile_danger_zone_section),
    ProfileDangerZoneSubtitle(Res.string.auth_profile_danger_zone_subtitle),
    ProfileDisplayNameLabel(Res.string.auth_profile_display_name_label),
    ProfileSignedInAsLabel(Res.string.auth_profile_signed_in_as_label),
    ProfileSave(Res.string.auth_profile_save),
    ProfileChangeAvatarCta(Res.string.auth_profile_change_avatar_cta),
    ProfileAvatarUploading(Res.string.auth_profile_avatar_uploading),
    ProfileAccountSection(Res.string.auth_profile_account_section),
    ProfileSignOutCta(Res.string.auth_profile_sign_out_cta),
    ProfileSignOutFailed(Res.string.auth_profile_sign_out_failed),
    ProfileDisplayNameBlank(Res.string.auth_profile_display_name_blank),
    ProfileDisplayNameTooLong(Res.string.auth_profile_display_name_too_long),
    ProfileBackendUnavailable(Res.string.auth_profile_backend_unavailable),
    ProfileAvatarEmptyBytes(Res.string.auth_profile_avatar_empty_bytes),

    ProfileThemeRow(Res.string.auth_profile_theme_row),
    ProfileThemePickerTitle(Res.string.auth_profile_theme_picker_title),
    ProfileThemeOptionLight(Res.string.auth_profile_theme_option_light),
    ProfileThemeOptionDark(Res.string.auth_profile_theme_option_dark),
    ProfileThemeOptionSystem(Res.string.auth_profile_theme_option_system),
    ProfileThemePersistFailed(Res.string.auth_profile_theme_persist_failed),

    ProfileLanguageRow(Res.string.auth_profile_language_row),
    ProfileLanguagePickerTitle(Res.string.auth_profile_language_picker_title),
    ProfileLanguageOptionSystem(Res.string.auth_profile_language_option_system),
    ProfileLanguageOptionEn(Res.string.auth_profile_language_option_en),
    ProfileLanguageOptionEs(Res.string.auth_profile_language_option_es),
    ProfileLanguagePersistFailed(Res.string.auth_profile_language_persist_failed),

    ProfileNotificationsRow(Res.string.auth_profile_notifications_row),
    ProfileNotificationsSubtitleOn(Res.string.auth_profile_notifications_subtitle_on),
    ProfileNotificationsSubtitleOff(Res.string.auth_profile_notifications_subtitle_off),
    ProfileNotificationsPersistFailed(Res.string.auth_profile_notifications_persist_failed),
    ProfileNotificationsPermissionDenied(Res.string.auth_profile_notifications_permission_denied),
    ProfileNotificationsPermissionDeniedForever(Res.string.auth_profile_notifications_permission_denied_forever),
    ProfileNotificationsOpenSystemSettingsCta(Res.string.auth_profile_notifications_open_system_settings_cta),

    ProfileDeleteAccountRow(Res.string.auth_profile_delete_account_row),
    ProfileDeleteAccountSubtitle(Res.string.auth_profile_delete_account_subtitle),

    DeleteAccountTitle(Res.string.auth_delete_account_title),
    DeleteAccountIntro(Res.string.auth_delete_account_intro),
    DeleteAccountWarningMeals(Res.string.auth_delete_account_warning_meals),
    DeleteAccountWarningRatings(Res.string.auth_delete_account_warning_ratings),
    DeleteAccountWarningCrews(Res.string.auth_delete_account_warning_crews),
    DeleteAccountWarningIrreversible(Res.string.auth_delete_account_warning_irreversible),
    DeleteAccountPhraseLabel(Res.string.auth_delete_account_phrase_label),
    DeleteAccountPhraseTemplate(Res.string.auth_delete_account_phrase_template),
    DeleteAccountConfirmCta(Res.string.auth_delete_account_confirm_cta),
    DeleteAccountDialogTitle(Res.string.auth_delete_account_dialog_title),
    DeleteAccountDialogBody(Res.string.auth_delete_account_dialog_body),
    DeleteAccountDialogCancel(Res.string.auth_delete_account_dialog_cancel),
    DeleteAccountDialogConfirm(Res.string.auth_delete_account_dialog_confirm),
    DeleteAccountInFlight(Res.string.auth_delete_account_in_flight),
    DeleteAccountErrorPhrase(Res.string.auth_delete_account_error_phrase),
    DeleteAccountErrorNotImplemented(Res.string.auth_delete_account_error_not_implemented),
    DeleteAccountErrorBackend(Res.string.auth_delete_account_error_backend),
    DeleteAccountErrorOwnership(Res.string.auth_delete_account_error_ownership),
}
