package es.schsebastian.foodrats.feature.auth.i18n

import es.schsebastian.foodrats.core.i18n.StringKey
import foodrats.feature.auth.generated.resources.Res
import foodrats.feature.auth.generated.resources.auth_delete_account_confirm_cta
import foodrats.feature.auth.generated.resources.auth_delete_account_dialog_body
import foodrats.feature.auth.generated.resources.auth_delete_account_dialog_cancel
import foodrats.feature.auth.generated.resources.auth_delete_account_dialog_confirm
import foodrats.feature.auth.generated.resources.auth_delete_account_dialog_title
import foodrats.feature.auth.generated.resources.auth_delete_account_error_backend
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
import foodrats.feature.auth.generated.resources.auth_error_apple_coming_soon
import foodrats.feature.auth.generated.resources.auth_export_data_error_backend
import foodrats.feature.auth.generated.resources.auth_export_data_in_flight
import foodrats.feature.auth.generated.resources.auth_export_data_ready_cta
import foodrats.feature.auth.generated.resources.auth_export_data_ready_subtitle
import foodrats.feature.auth.generated.resources.auth_export_data_row
import foodrats.feature.auth.generated.resources.auth_export_data_subtitle
import foodrats.feature.auth.generated.resources.auth_error_email_in_use
import foodrats.feature.auth.generated.resources.auth_error_email_invalid
import foodrats.feature.auth.generated.resources.auth_error_missing_server_client_id
import foodrats.feature.auth.generated.resources.auth_error_network
import foodrats.feature.auth.generated.resources.auth_error_no_google_accounts
import foodrats.feature.auth.generated.resources.auth_error_password_too_short
import foodrats.feature.auth.generated.resources.auth_error_play_services
import foodrats.feature.auth.generated.resources.auth_error_session_expired
import foodrats.feature.auth.generated.resources.auth_error_unknown
import foodrats.feature.auth.generated.resources.auth_error_user_cancelled
import foodrats.feature.auth.generated.resources.auth_error_wrong_credentials
import foodrats.feature.auth.generated.resources.auth_field_email
import foodrats.feature.auth.generated.resources.auth_field_password
import foodrats.feature.auth.generated.resources.auth_mode_signin_cta
import foodrats.feature.auth.generated.resources.auth_mode_signup_cta
import foodrats.feature.auth.generated.resources.auth_or_divider
import foodrats.feature.auth.generated.resources.auth_profile_account_section
import foodrats.feature.auth.generated.resources.auth_profile_achievements_row
import foodrats.feature.auth.generated.resources.auth_profile_bio_label
import foodrats.feature.auth.generated.resources.auth_profile_bio_placeholder
import foodrats.feature.auth.generated.resources.auth_profile_bio_save
import foodrats.feature.auth.generated.resources.auth_profile_bio_too_long
import foodrats.feature.auth.generated.resources.auth_profile_achievements_section
import foodrats.feature.auth.generated.resources.auth_profile_achievements_subtitle
import foodrats.feature.auth.generated.resources.auth_profile_ai_row
import foodrats.feature.auth.generated.resources.auth_profile_ai_subtitle_on
import foodrats.feature.auth.generated.resources.auth_profile_ai_subtitle_off
import foodrats.feature.auth.generated.resources.auth_profile_ai_persist_failed
import foodrats.feature.auth.generated.resources.auth_profile_analytics_row
import foodrats.feature.auth.generated.resources.auth_profile_analytics_subtitle_off
import foodrats.feature.auth.generated.resources.auth_profile_analytics_subtitle_on
import foodrats.feature.auth.generated.resources.auth_profile_avatar_empty_bytes
import foodrats.feature.auth.generated.resources.auth_profile_avatar_uploading
import foodrats.feature.auth.generated.resources.auth_profile_backend_unavailable
import foodrats.feature.auth.generated.resources.auth_profile_change_avatar_cta
import foodrats.feature.auth.generated.resources.auth_profile_remove_avatar_cta
import foodrats.feature.auth.generated.resources.auth_profile_remove_avatar_removing
import foodrats.feature.auth.generated.resources.auth_profile_remove_avatar_confirm_title
import foodrats.feature.auth.generated.resources.auth_profile_remove_avatar_confirm_body
import foodrats.feature.auth.generated.resources.auth_profile_remove_avatar_confirm_cta
import foodrats.feature.auth.generated.resources.auth_profile_remove_avatar_cancel
import foodrats.feature.auth.generated.resources.auth_profile_remove_avatar_error
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
import foodrats.feature.auth.generated.resources.auth_profile_reminders_add_cta
import foodrats.feature.auth.generated.resources.auth_profile_reminders_empty
import foodrats.feature.auth.generated.resources.auth_profile_reminders_persist_failed
import foodrats.feature.auth.generated.resources.auth_profile_reminders_picker_title
import foodrats.feature.auth.generated.resources.auth_profile_reminders_remove_cta
import foodrats.feature.auth.generated.resources.auth_profile_reminders_row
import foodrats.feature.auth.generated.resources.auth_profile_reminders_subtitle
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
import foodrats.feature.auth.generated.resources.auth_signin_continue_apple
import foodrats.feature.auth.generated.resources.auth_signin_continue_google
import foodrats.feature.auth.generated.resources.auth_signin_agreement_connector
import foodrats.feature.auth.generated.resources.auth_signin_agreement_eula_link
import foodrats.feature.auth.generated.resources.auth_signin_agreement_guidelines_link
import foodrats.feature.auth.generated.resources.auth_signin_agreement_prefix
import foodrats.feature.auth.generated.resources.auth_signin_footer
import foodrats.feature.auth.generated.resources.auth_profile_legal_section
import foodrats.feature.auth.generated.resources.auth_profile_legal_eula_row
import foodrats.feature.auth.generated.resources.auth_profile_legal_guidelines_row
import foodrats.feature.auth.generated.resources.auth_profile_safety_section
import foodrats.feature.auth.generated.resources.auth_profile_blocked_users_row
import foodrats.feature.auth.generated.resources.auth_profile_blocked_users_subtitle
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
    ContinueWithApple(Res.string.auth_signin_continue_apple),
    HighlightShare(Res.string.auth_signin_highlight_share),
    HighlightRate(Res.string.auth_signin_highlight_rate),
    HighlightFeed(Res.string.auth_signin_highlight_feed),
    Footer(Res.string.auth_signin_footer),

    // UGC-compliance agreement line below the sign-in buttons (UGC compliance §6). The two doc names
    // are tappable links that open Route.Eula / Route.CommunityGuidelines; continuing accepts both.
    SignInAgreementPrefix(Res.string.auth_signin_agreement_prefix),
    SignInAgreementEulaLink(Res.string.auth_signin_agreement_eula_link),
    SignInAgreementConnector(Res.string.auth_signin_agreement_connector),
    SignInAgreementGuidelinesLink(Res.string.auth_signin_agreement_guidelines_link),
    FieldEmail(Res.string.auth_field_email),
    FieldPassword(Res.string.auth_field_password),
    ModeSignInCta(Res.string.auth_mode_signin_cta),
    ModeSignUpCta(Res.string.auth_mode_signup_cta),
    ToggleToSignUp(Res.string.auth_toggle_to_signup),
    ToggleToSignIn(Res.string.auth_toggle_to_signin),
    OrDivider(Res.string.auth_or_divider),
    ErrorAppleComingSoon(Res.string.auth_error_apple_coming_soon),
    ErrorUserCancelled(Res.string.auth_error_user_cancelled),
    ErrorNoGoogleAccounts(Res.string.auth_error_no_google_accounts),
    ErrorPlayServices(Res.string.auth_error_play_services),
    ErrorNetwork(Res.string.auth_error_network),
    ErrorAccountDisabled(Res.string.auth_error_account_disabled),
    ErrorSessionExpired(Res.string.auth_error_session_expired),
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
    ProfileRemoveAvatarCta(Res.string.auth_profile_remove_avatar_cta),
    ProfileAvatarRemoving(Res.string.auth_profile_remove_avatar_removing),
    ProfileRemoveAvatarConfirmTitle(Res.string.auth_profile_remove_avatar_confirm_title),
    ProfileRemoveAvatarConfirmBody(Res.string.auth_profile_remove_avatar_confirm_body),
    ProfileRemoveAvatarConfirmCta(Res.string.auth_profile_remove_avatar_confirm_cta),
    ProfileRemoveAvatarCancel(Res.string.auth_profile_remove_avatar_cancel),
    ProfileRemoveAvatarError(Res.string.auth_profile_remove_avatar_error),

    ProfileBioLabel(Res.string.auth_profile_bio_label),
    ProfileBioPlaceholder(Res.string.auth_profile_bio_placeholder),
    ProfileBioSave(Res.string.auth_profile_bio_save),
    ProfileBioTooLong(Res.string.auth_profile_bio_too_long),

    ProfileAccountSection(Res.string.auth_profile_account_section),
    ProfileAchievementsSection(Res.string.auth_profile_achievements_section),
    ProfileAchievementsRow(Res.string.auth_profile_achievements_row),
    ProfileAchievementsSubtitle(Res.string.auth_profile_achievements_subtitle),

    // Profile "Legal" section (UGC compliance §6) — opens the embedded EULA / Community Guidelines.
    // Profile "Safety" section (UGC compliance §5) — opens the blocked-users list.
    ProfileSafetySection(Res.string.auth_profile_safety_section),
    ProfileBlockedUsersRow(Res.string.auth_profile_blocked_users_row),
    ProfileBlockedUsersSubtitle(Res.string.auth_profile_blocked_users_subtitle),
    ProfileLegalSection(Res.string.auth_profile_legal_section),
    ProfileLegalEulaRow(Res.string.auth_profile_legal_eula_row),
    ProfileLegalGuidelinesRow(Res.string.auth_profile_legal_guidelines_row),
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

    ProfileRemindersRow(Res.string.auth_profile_reminders_row),
    ProfileRemindersSubtitle(Res.string.auth_profile_reminders_subtitle),
    ProfileRemindersEmpty(Res.string.auth_profile_reminders_empty),
    ProfileRemindersAddCta(Res.string.auth_profile_reminders_add_cta),
    ProfileRemindersPickerTitle(Res.string.auth_profile_reminders_picker_title),
    ProfileRemindersRemoveCta(Res.string.auth_profile_reminders_remove_cta),
    ProfileRemindersPersistFailed(Res.string.auth_profile_reminders_persist_failed),

    ProfileAiRow(Res.string.auth_profile_ai_row),
    ProfileAiSubtitleOn(Res.string.auth_profile_ai_subtitle_on),
    ProfileAiSubtitleOff(Res.string.auth_profile_ai_subtitle_off),
    ProfileAiPersistFailed(Res.string.auth_profile_ai_persist_failed),

    ProfileAnalyticsRow(Res.string.auth_profile_analytics_row),
    ProfileAnalyticsSubtitleOn(Res.string.auth_profile_analytics_subtitle_on),
    ProfileAnalyticsSubtitleOff(Res.string.auth_profile_analytics_subtitle_off),

    ProfileDeleteAccountRow(Res.string.auth_profile_delete_account_row),
    ProfileDeleteAccountSubtitle(Res.string.auth_profile_delete_account_subtitle),

    ExportDataRow(Res.string.auth_export_data_row),
    ExportDataSubtitle(Res.string.auth_export_data_subtitle),
    ExportDataInFlight(Res.string.auth_export_data_in_flight),
    ExportDataReadySubtitle(Res.string.auth_export_data_ready_subtitle),
    ExportDataReadyCta(Res.string.auth_export_data_ready_cta),
    ExportDataErrorBackend(Res.string.auth_export_data_error_backend),

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
    DeleteAccountErrorBackend(Res.string.auth_delete_account_error_backend),
    DeleteAccountErrorOwnership(Res.string.auth_delete_account_error_ownership),
}
