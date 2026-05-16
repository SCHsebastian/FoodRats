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
}
