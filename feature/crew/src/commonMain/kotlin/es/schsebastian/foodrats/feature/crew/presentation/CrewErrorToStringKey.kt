package es.schsebastian.foodrats.feature.crew.presentation

import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.i18n.CrewStringKey

fun CrewError.toStringKey(): CrewStringKey = when (this) {
    CrewError.Validation.NameBlank          -> CrewStringKey.ErrorNameBlank
    CrewError.Validation.NameTooLong        -> CrewStringKey.ErrorNameTooLong
    CrewError.Validation.CodeMalformed      -> CrewStringKey.ErrorCodeMalformed
    CrewError.Validation.DisplayNameBlank   -> CrewStringKey.ErrorValidationDisplayNameBlank
    CrewError.Validation.DisplayNameTooLong -> CrewStringKey.ErrorValidationDisplayNameTooLong
    CrewError.Validation.TaglineTooLong     -> CrewStringKey.ErrorValidationTaglineTooLong
    CrewError.Validation.WelcomeMessageTooLong -> CrewStringKey.ErrorValidationWelcomeMessageTooLong
    CrewError.Validation.WeeklyChallengeTooLong -> CrewStringKey.ErrorValidationWeeklyChallengeTooLong
    CrewError.Authorization.NotOwner        -> CrewStringKey.ErrorAuthorizationNotOwner
    CrewError.Membership.NotFound           -> CrewStringKey.ErrorNotFound
    CrewError.Membership.Full               -> CrewStringKey.ErrorFull
    CrewError.Membership.NotInvited         -> CrewStringKey.ErrorPermission
    CrewError.Membership.AlreadyMember      -> CrewStringKey.ErrorAlreadyMember
    CrewError.Membership.NotMember          -> CrewStringKey.ErrorNotMember
    CrewError.Invite.CodeUnknown            -> CrewStringKey.ErrorCodeUnknown
    CrewError.Invite.Expired                -> CrewStringKey.ErrorCodeUnknown
    CrewError.Invite.AlreadyRequested       -> CrewStringKey.ErrorInviteAlreadyRequested
    CrewError.Create.CodeCollisionRetriesExhausted -> CrewStringKey.ErrorCollision
    CrewError.Backend.Network               -> CrewStringKey.ErrorNetwork
    CrewError.Backend.PermissionDenied      -> CrewStringKey.ErrorPermission
    CrewError.Backend.Unavailable           -> CrewStringKey.ErrorUnknown
    CrewError.Backend.Unknown               -> CrewStringKey.ErrorUnknown
    CrewError.Session.NotSignedIn           -> CrewStringKey.ErrorSessionNotSignedIn
    CrewError.Session.Expired               -> CrewStringKey.ErrorSessionExpired
    CrewError.RemoveMember.NotOwner         -> CrewStringKey.ErrorRemoveMemberNotOwner
    CrewError.RemoveMember.CannotRemoveSelf -> CrewStringKey.ErrorRemoveMemberCannotRemoveSelf
    CrewError.RemoveMember.MemberNotFound   -> CrewStringKey.ErrorRemoveMemberMemberNotFound
    CrewError.Transfer.NotOwner             -> CrewStringKey.ErrorTransferNotOwner
    CrewError.Transfer.TargetNotMember      -> CrewStringKey.ErrorTransferTargetNotMember
    CrewError.Transfer.CannotTransferToSelf -> CrewStringKey.ErrorTransferCannotTransferToSelf
    CrewError.Banner.UploadFailed           -> CrewStringKey.ErrorBannerUploadFailed
    CrewError.Banner.DeleteFailed           -> CrewStringKey.ErrorBannerDeleteFailed
    CrewError.Banner.ImageTooLarge          -> CrewStringKey.ErrorBannerImageTooLarge
    CrewError.Banner.ImageUnreadable        -> CrewStringKey.ErrorBannerImageUnreadable
    CrewError.Banner.PickFailed             -> CrewStringKey.ErrorBannerPickFailed
}
