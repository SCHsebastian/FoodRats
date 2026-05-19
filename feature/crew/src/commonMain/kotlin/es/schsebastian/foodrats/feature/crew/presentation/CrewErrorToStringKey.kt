package es.schsebastian.foodrats.feature.crew.presentation

import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.i18n.CrewStringKey

fun CrewError.toStringKey(): CrewStringKey = when (this) {
    CrewError.Validation.NameBlank          -> CrewStringKey.ErrorNameBlank
    CrewError.Validation.NameTooLong        -> CrewStringKey.ErrorNameTooLong
    CrewError.Validation.CodeMalformed      -> CrewStringKey.ErrorCodeMalformed
    CrewError.Validation.DisplayNameBlank   -> CrewStringKey.ErrorValidationDisplayNameBlank
    CrewError.Validation.DisplayNameTooLong -> CrewStringKey.ErrorValidationDisplayNameTooLong
    CrewError.Authorization.NotOwner        -> CrewStringKey.ErrorAuthorizationNotOwner
    CrewError.Membership.NotFound           -> CrewStringKey.ErrorNotFound
    CrewError.Membership.Full               -> CrewStringKey.ErrorFull
    CrewError.Membership.NotInvited         -> CrewStringKey.ErrorPermission
    CrewError.Membership.AlreadyMember      -> CrewStringKey.ErrorAlreadyMember
    CrewError.Membership.NotMember          -> CrewStringKey.ErrorNotMember
    CrewError.Invite.CodeUnknown            -> CrewStringKey.ErrorCodeUnknown
    CrewError.Invite.Expired                -> CrewStringKey.ErrorCodeUnknown
    CrewError.Create.CodeCollisionRetriesExhausted -> CrewStringKey.ErrorCollision
    CrewError.Backend.Network               -> CrewStringKey.ErrorNetwork
    CrewError.Backend.PermissionDenied      -> CrewStringKey.ErrorPermission
    CrewError.Backend.Unavailable           -> CrewStringKey.ErrorUnknown
}
