package es.schsebastian.foodrats.feature.crew.i18n

import es.schsebastian.foodrats.core.i18n.StringKey
import foodrats.feature.crew.generated.resources.Res
import foodrats.feature.crew.generated.resources.crew_create_name_label
import foodrats.feature.crew.generated.resources.crew_create_submit
import foodrats.feature.crew.generated.resources.crew_error_already_member
import foodrats.feature.crew.generated.resources.crew_error_authorization_not_owner
import foodrats.feature.crew.generated.resources.crew_error_validation_display_name_blank
import foodrats.feature.crew.generated.resources.crew_error_validation_display_name_too_long
import foodrats.feature.crew.generated.resources.crew_error_remove_member_cannot_remove_self
import foodrats.feature.crew.generated.resources.crew_error_remove_member_member_not_found
import foodrats.feature.crew.generated.resources.crew_error_remove_member_not_owner
import foodrats.feature.crew.generated.resources.crew_error_code_malformed
import foodrats.feature.crew.generated.resources.crew_error_code_unknown
import foodrats.feature.crew.generated.resources.crew_error_collision
import foodrats.feature.crew.generated.resources.crew_error_full
import foodrats.feature.crew.generated.resources.crew_error_name_blank
import foodrats.feature.crew.generated.resources.crew_error_name_too_long
import foodrats.feature.crew.generated.resources.crew_error_network
import foodrats.feature.crew.generated.resources.crew_error_not_found
import foodrats.feature.crew.generated.resources.crew_error_not_member
import foodrats.feature.crew.generated.resources.crew_error_permission
import foodrats.feature.crew.generated.resources.crew_error_unknown
import foodrats.feature.crew.generated.resources.crew_join_code_label
import foodrats.feature.crew.generated.resources.crew_join_submit
import foodrats.feature.crew.generated.resources.crew_member_deleted
import foodrats.feature.crew.generated.resources.crew_picker_brand_name
import foodrats.feature.crew.generated.resources.crew_picker_create_cta
import foodrats.feature.crew.generated.resources.crew_picker_crew_button
import foodrats.feature.crew.generated.resources.crew_picker_empty_headline
import foodrats.feature.crew.generated.resources.crew_picker_empty_subtext
import foodrats.feature.crew.generated.resources.crew_picker_hero_subtitle
import foodrats.feature.crew.generated.resources.crew_picker_join_cta
import foodrats.feature.crew.generated.resources.crew_picker_title
import foodrats.feature.crew.generated.resources.crew_settings_actions_section
import foodrats.feature.crew.generated.resources.crew_settings_back_cta
import foodrats.feature.crew.generated.resources.crew_settings_blind_voting_description
import foodrats.feature.crew.generated.resources.crew_settings_blind_voting_label
import foodrats.feature.crew.generated.resources.crew_settings_blind_voting_section
import foodrats.feature.crew.generated.resources.crew_settings_tagline_section
import foodrats.feature.crew.generated.resources.crew_settings_tagline_label
import foodrats.feature.crew.generated.resources.crew_settings_tagline_placeholder
import foodrats.feature.crew.generated.resources.crew_error_validation_tagline_too_long
import foodrats.feature.crew.generated.resources.crew_settings_welcome_message_section
import foodrats.feature.crew.generated.resources.crew_settings_welcome_message_label
import foodrats.feature.crew.generated.resources.crew_settings_welcome_message_placeholder
import foodrats.feature.crew.generated.resources.crew_error_validation_welcome_message_too_long
import foodrats.feature.crew.generated.resources.crew_settings_cancel
import foodrats.feature.crew.generated.resources.crew_settings_crew_name_label
import foodrats.feature.crew.generated.resources.crew_settings_crew_section
import foodrats.feature.crew.generated.resources.crew_settings_danger_section
import foodrats.feature.crew.generated.resources.crew_settings_delete_body
import foodrats.feature.crew.generated.resources.crew_settings_delete_confirm
import foodrats.feature.crew.generated.resources.crew_settings_delete_cta
import foodrats.feature.crew.generated.resources.crew_settings_delete_title
import foodrats.feature.crew.generated.resources.crew_settings_invite_code
import foodrats.feature.crew.generated.resources.crew_settings_leave_cta
import foodrats.feature.crew.generated.resources.crew_settings_members_count
import foodrats.feature.crew.generated.resources.crew_settings_members_section
import foodrats.feature.crew.generated.resources.crew_settings_owner_badge
import foodrats.feature.crew.generated.resources.crew_settings_role_member
import foodrats.feature.crew.generated.resources.crew_settings_role_owner
import foodrats.feature.crew.generated.resources.crew_settings_member_removed
import foodrats.feature.crew.generated.resources.crew_settings_remove_member_confirm_body
import foodrats.feature.crew.generated.resources.crew_settings_remove_member_confirm_title
import foodrats.feature.crew.generated.resources.crew_settings_remove_member_cta
import foodrats.feature.crew.generated.resources.crew_settings_save
import foodrats.feature.crew.generated.resources.crew_settings_copy_cta
import foodrats.feature.crew.generated.resources.crew_settings_share
import foodrats.feature.crew.generated.resources.crew_settings_share_code
import foodrats.feature.crew.generated.resources.crew_settings_share_link
import foodrats.feature.crew.generated.resources.crew_settings_show_qr
import foodrats.feature.crew.generated.resources.crew_settings_qr_caption
import foodrats.feature.crew.generated.resources.crew_settings_qr_close
import foodrats.feature.crew.generated.resources.crew_invite_share_message
import foodrats.feature.crew.generated.resources.crew_invite_eyebrow
import foodrats.feature.crew.generated.resources.crew_invite_subtitle
import foodrats.feature.crew.generated.resources.crew_invite_join_cta
import foodrats.feature.crew.generated.resources.crew_invite_decline_cta
import foodrats.feature.crew.generated.resources.crew_invite_back_cta
import foodrats.feature.crew.generated.resources.crew_settings_switch_crew
import foodrats.feature.crew.generated.resources.crew_settings_title
import org.jetbrains.compose.resources.StringResource

enum class CrewStringKey(override val resourceId: StringResource) : StringKey {
    PickerTitle(Res.string.crew_picker_title),
    PickerEmptyHeadline(Res.string.crew_picker_empty_headline),
    PickerEmptySubtext(Res.string.crew_picker_empty_subtext),
    PickerHeroSubtitle(Res.string.crew_picker_hero_subtitle),
    PickerCreateCta(Res.string.crew_picker_create_cta),
    PickerJoinCta(Res.string.crew_picker_join_cta),
    PickerBrandName(Res.string.crew_picker_brand_name),
    PickerCrewButton(Res.string.crew_picker_crew_button),
    CreateNameLabel(Res.string.crew_create_name_label),
    CreateSubmit(Res.string.crew_create_submit),
    JoinCodeLabel(Res.string.crew_join_code_label),
    JoinSubmit(Res.string.crew_join_submit),
    SettingsTitle(Res.string.crew_settings_title),
    SettingsBackCta(Res.string.crew_settings_back_cta),
    SettingsMembersSection(Res.string.crew_settings_members_section),
    SettingsOwnerBadge(Res.string.crew_settings_owner_badge),
    SettingsMembersCount(Res.string.crew_settings_members_count),
    SettingsRoleOwner(Res.string.crew_settings_role_owner),
    SettingsRoleMember(Res.string.crew_settings_role_member),
    SettingsShareCode(Res.string.crew_settings_share_code),
    SettingsLeaveCta(Res.string.crew_settings_leave_cta),
    SettingsCrewSection(Res.string.crew_settings_crew_section),
    SettingsActionsSection(Res.string.crew_settings_actions_section),
    SettingsDangerSection(Res.string.crew_settings_danger_section),
    SettingsCrewNameLabel(Res.string.crew_settings_crew_name_label),
    SettingsSave(Res.string.crew_settings_save),
    SettingsBlindVotingSection(Res.string.crew_settings_blind_voting_section),
    SettingsBlindVotingLabel(Res.string.crew_settings_blind_voting_label),
    SettingsBlindVotingDescription(Res.string.crew_settings_blind_voting_description),
    SettingsTaglineSection(Res.string.crew_settings_tagline_section),
    SettingsTaglineLabel(Res.string.crew_settings_tagline_label),
    SettingsTaglinePlaceholder(Res.string.crew_settings_tagline_placeholder),
    ErrorValidationTaglineTooLong(Res.string.crew_error_validation_tagline_too_long),
    SettingsSwitchCrew(Res.string.crew_settings_switch_crew),
    SettingsInviteCode(Res.string.crew_settings_invite_code),
    SettingsShare(Res.string.crew_settings_share),
    SettingsShareLink(Res.string.crew_settings_share_link),
    SettingsShowQr(Res.string.crew_settings_show_qr),
    SettingsQrCaption(Res.string.crew_settings_qr_caption),
    SettingsQrClose(Res.string.crew_settings_qr_close),
    SettingsCopyCta(Res.string.crew_settings_copy_cta),
    InviteShareMessage(Res.string.crew_invite_share_message),
    InviteEyebrow(Res.string.crew_invite_eyebrow),
    InviteSubtitle(Res.string.crew_invite_subtitle),
    InviteJoinCta(Res.string.crew_invite_join_cta),
    InviteDeclineCta(Res.string.crew_invite_decline_cta),
    InviteBackCta(Res.string.crew_invite_back_cta),
    SettingsDeleteCta(Res.string.crew_settings_delete_cta),
    SettingsDeleteTitle(Res.string.crew_settings_delete_title),
    SettingsDeleteBody(Res.string.crew_settings_delete_body),
    SettingsDeleteConfirm(Res.string.crew_settings_delete_confirm),
    SettingsCancel(Res.string.crew_settings_cancel),
    ErrorNameBlank(Res.string.crew_error_name_blank),
    ErrorNameTooLong(Res.string.crew_error_name_too_long),
    ErrorCodeMalformed(Res.string.crew_error_code_malformed),
    ErrorCodeUnknown(Res.string.crew_error_code_unknown),
    ErrorFull(Res.string.crew_error_full),
    ErrorAlreadyMember(Res.string.crew_error_already_member),
    ErrorNotMember(Res.string.crew_error_not_member),
    ErrorNotFound(Res.string.crew_error_not_found),
    ErrorNetwork(Res.string.crew_error_network),
    ErrorPermission(Res.string.crew_error_permission),
    ErrorCollision(Res.string.crew_error_collision),
    ErrorUnknown(Res.string.crew_error_unknown),
    ErrorAuthorizationNotOwner(Res.string.crew_error_authorization_not_owner),
    ErrorValidationDisplayNameBlank(Res.string.crew_error_validation_display_name_blank),
    ErrorValidationDisplayNameTooLong(Res.string.crew_error_validation_display_name_too_long),
    ErrorRemoveMemberNotOwner(Res.string.crew_error_remove_member_not_owner),
    ErrorRemoveMemberCannotRemoveSelf(Res.string.crew_error_remove_member_cannot_remove_self),
    ErrorRemoveMemberMemberNotFound(Res.string.crew_error_remove_member_member_not_found),
    SettingsRemoveMemberCta(Res.string.crew_settings_remove_member_cta),
    SettingsRemoveMemberConfirmTitle(Res.string.crew_settings_remove_member_confirm_title),
    SettingsRemoveMemberConfirmBody(Res.string.crew_settings_remove_member_confirm_body),
    SettingsMemberRemoved(Res.string.crew_settings_member_removed),
    MemberDeleted(Res.string.crew_member_deleted),
    SettingsWelcomeMessageSection(Res.string.crew_settings_welcome_message_section),
    SettingsWelcomeMessageLabel(Res.string.crew_settings_welcome_message_label),
    SettingsWelcomeMessagePlaceholder(Res.string.crew_settings_welcome_message_placeholder),
    ErrorValidationWelcomeMessageTooLong(Res.string.crew_error_validation_welcome_message_too_long),
}
