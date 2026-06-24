package es.schsebastian.foodrats.feature.crew.presentation

import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.i18n.CrewStringKey
import kotlin.test.Test
import kotlin.test.assertEquals

class CrewErrorToStringKeyTest {
    @Test fun nameBlank() = assertEquals(CrewStringKey.ErrorNameBlank, CrewError.Validation.NameBlank.toStringKey())
    @Test fun nameTooLong() = assertEquals(CrewStringKey.ErrorNameTooLong, CrewError.Validation.NameTooLong.toStringKey())
    @Test fun codeMalformed() = assertEquals(CrewStringKey.ErrorCodeMalformed, CrewError.Validation.CodeMalformed.toStringKey())
    @Test fun notFound() = assertEquals(CrewStringKey.ErrorNotFound, CrewError.Membership.NotFound.toStringKey())
    @Test fun full() = assertEquals(CrewStringKey.ErrorFull, CrewError.Membership.Full.toStringKey())
    @Test fun notInvited() = assertEquals(CrewStringKey.ErrorPermission, CrewError.Membership.NotInvited.toStringKey())
    @Test fun alreadyMember() = assertEquals(CrewStringKey.ErrorAlreadyMember, CrewError.Membership.AlreadyMember.toStringKey())
    @Test fun notMember() = assertEquals(CrewStringKey.ErrorNotMember, CrewError.Membership.NotMember.toStringKey())
    @Test fun codeUnknown() = assertEquals(CrewStringKey.ErrorCodeUnknown, CrewError.Invite.CodeUnknown.toStringKey())
    @Test fun codeExpired() = assertEquals(CrewStringKey.ErrorCodeUnknown, CrewError.Invite.Expired.toStringKey())
    @Test fun collision() = assertEquals(CrewStringKey.ErrorCollision, CrewError.Create.CodeCollisionRetriesExhausted.toStringKey())
    @Test fun backendNetwork() = assertEquals(CrewStringKey.ErrorNetwork, CrewError.Backend.Network.toStringKey())
    @Test fun backendPermission() = assertEquals(CrewStringKey.ErrorPermission, CrewError.Backend.PermissionDenied.toStringKey())
    @Test fun backendUnavailable() = assertEquals(CrewStringKey.ErrorUnknown, CrewError.Backend.Unavailable.toStringKey())
    @Test fun maps_authorization_not_owner() = assertEquals(CrewStringKey.ErrorAuthorizationNotOwner, CrewError.Authorization.NotOwner.toStringKey())
    @Test fun maps_validation_display_name_blank() = assertEquals(CrewStringKey.ErrorValidationDisplayNameBlank, CrewError.Validation.DisplayNameBlank.toStringKey())
    @Test fun maps_validation_display_name_too_long() = assertEquals(CrewStringKey.ErrorValidationDisplayNameTooLong, CrewError.Validation.DisplayNameTooLong.toStringKey())
    @Test fun maps_remove_member_not_owner() = assertEquals(CrewStringKey.ErrorRemoveMemberNotOwner, CrewError.RemoveMember.NotOwner.toStringKey())
    @Test fun maps_remove_member_cannot_remove_self() = assertEquals(CrewStringKey.ErrorRemoveMemberCannotRemoveSelf, CrewError.RemoveMember.CannotRemoveSelf.toStringKey())
    @Test fun maps_remove_member_member_not_found() = assertEquals(CrewStringKey.ErrorRemoveMemberMemberNotFound, CrewError.RemoveMember.MemberNotFound.toStringKey())
    @Test fun maps_transfer_not_owner() = assertEquals(CrewStringKey.ErrorTransferNotOwner, CrewError.Transfer.NotOwner.toStringKey())
    @Test fun maps_transfer_target_not_member() = assertEquals(CrewStringKey.ErrorTransferTargetNotMember, CrewError.Transfer.TargetNotMember.toStringKey())
    @Test fun maps_validation_tagline_too_long() = assertEquals(CrewStringKey.ErrorValidationTaglineTooLong, CrewError.Validation.TaglineTooLong.toStringKey())
    @Test fun maps_validation_welcome_message_too_long() = assertEquals(CrewStringKey.ErrorValidationWelcomeMessageTooLong, CrewError.Validation.WelcomeMessageTooLong.toStringKey())
    @Test fun maps_validation_weekly_challenge_too_long() = assertEquals(CrewStringKey.ErrorValidationWeeklyChallengeTooLong, CrewError.Validation.WeeklyChallengeTooLong.toStringKey())
    // C9 — crew banner
    @Test fun maps_banner_upload_failed() = assertEquals(CrewStringKey.ErrorBannerUploadFailed, CrewError.Banner.UploadFailed.toStringKey())
    @Test fun maps_banner_delete_failed() = assertEquals(CrewStringKey.ErrorBannerDeleteFailed, CrewError.Banner.DeleteFailed.toStringKey())
}
