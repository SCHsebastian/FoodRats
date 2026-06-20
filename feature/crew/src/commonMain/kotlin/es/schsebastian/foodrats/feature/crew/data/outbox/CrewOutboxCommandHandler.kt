package es.schsebastian.foodrats.feature.crew.data.outbox

import es.schsebastian.foodrats.core.domain.outbox.OutboxCommandHandler
import es.schsebastian.foodrats.core.domain.outbox.OutboxExecuteResult
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository

/**
 * Replays the crew-admin outbox commands (offline-first P2 §1 T6) —
 * [PendingCommand.RenameCrew], [PendingCommand.SetBlindVoting],
 * [PendingCommand.RemoveMember], [PendingCommand.LeaveCrew] — against
 * [CrewRepository]. All four are idempotent server-side (rename/blind set a value;
 * remove/leave a no-longer-present member succeeds), so a replay never applies
 * twice.
 *
 * The cross-feature `OutboxRunner` lives in `:core:data`, which must NEVER import a
 * `:feature:*` module, so this handler (which DOES know [CrewRepository]) lives
 * here and is contributed to the runner via Koin `getAll()`
 * (`single<OutboxCommandHandler>` in `crewModule`). The meal commands are handled
 * by the sibling `MealOutboxCommandHandler` in `:feature:meal`.
 *
 * Error classification ([CrewError] → [OutboxExecuteResult]):
 *  - [CrewError.Backend.Network] / [CrewError.Backend.Unavailable] → [OutboxExecuteResult.Retryable]
 *    (transient; back off and re-attempt).
 *  - [CrewError.Membership.NotMember] / [CrewError.RemoveMember.MemberNotFound] →
 *    [OutboxExecuteResult.AlreadyApplied] (the member is already gone — the goal is met).
 *  - everything else (authorization, validation, permission-denied, …) →
 *    [OutboxExecuteResult.Terminal] (retrying cannot fix it).
 */
class CrewOutboxCommandHandler(
    private val crews: CrewRepository,
) : OutboxCommandHandler {

    override fun handles(cmd: PendingCommand): Boolean = when (cmd) {
        is PendingCommand.RenameCrew,
        is PendingCommand.SetBlindVoting,
        is PendingCommand.RemoveMember,
        is PendingCommand.LeaveCrew -> true
        is PendingCommand.RateMeal,
        is PendingCommand.PostComment,
        is PendingCommand.DeleteComment,
        is PendingCommand.ToggleReaction -> false
    }

    override suspend fun execute(cmd: PendingCommand): OutboxExecuteResult = when (cmd) {
        is PendingCommand.RenameCrew ->
            crews.renameCrew(cmd.crewId, cmd.requestedBy, cmd.newName).toExecuteResult()
        is PendingCommand.SetBlindVoting ->
            crews.setBlindVoting(cmd.crewId, cmd.requestedBy, cmd.enabled).toExecuteResult()
        is PendingCommand.RemoveMember ->
            crews.removeMember(cmd.crewId, cmd.requestedBy, cmd.target).toExecuteResult()
        is PendingCommand.LeaveCrew ->
            crews.leave(cmd.crewId, cmd.leaver).toExecuteResult()
        // Not ours — [handles] returns false for these, so the runner never routes
        // them here. Mapped to a terminal so an accidental dispatch can't loop.
        is PendingCommand.RateMeal,
        is PendingCommand.PostComment,
        is PendingCommand.DeleteComment,
        is PendingCommand.ToggleReaction -> OutboxExecuteResult.Terminal("outbox.error.wrongHandler")
    }

    private fun Result<Unit, CrewError>.toExecuteResult(): OutboxExecuteResult = when (this) {
        is Result.Ok -> OutboxExecuteResult.Success
        is Result.Err -> when (error) {
            // Transient backend trouble — back off and retry.
            CrewError.Backend.Network,
            CrewError.Backend.Unavailable -> OutboxExecuteResult.Retryable("crew.error.backendUnavailable")
            // The member is already gone — the goal is already met (idempotent remove/leave).
            CrewError.Membership.NotMember,
            CrewError.RemoveMember.MemberNotFound -> OutboxExecuteResult.AlreadyApplied
            // Everything else is a permanent failure that retrying cannot fix.
            CrewError.Backend.PermissionDenied -> OutboxExecuteResult.Terminal("crew.error.permissionDenied")
            CrewError.Authorization.NotOwner -> OutboxExecuteResult.Terminal("crew.error.notOwner")
            CrewError.Validation.NameBlank -> OutboxExecuteResult.Terminal("crew.error.nameBlank")
            CrewError.Validation.NameTooLong -> OutboxExecuteResult.Terminal("crew.error.nameTooLong")
            CrewError.Validation.CodeMalformed -> OutboxExecuteResult.Terminal("crew.error.codeMalformed")
            CrewError.Validation.DisplayNameBlank -> OutboxExecuteResult.Terminal("crew.error.displayNameBlank")
            CrewError.Validation.DisplayNameTooLong -> OutboxExecuteResult.Terminal("crew.error.displayNameTooLong")
            CrewError.Validation.TaglineTooLong -> OutboxExecuteResult.Terminal("crew.error.taglineTooLong")
            CrewError.Validation.WelcomeMessageTooLong -> OutboxExecuteResult.Terminal("crew.error.welcomeMessageTooLong")
            CrewError.Membership.NotFound -> OutboxExecuteResult.Terminal("crew.error.crewNotFound")
            CrewError.Membership.Full -> OutboxExecuteResult.Terminal("crew.error.crewFull")
            CrewError.Membership.NotInvited -> OutboxExecuteResult.Terminal("crew.error.notInvited")
            CrewError.Membership.AlreadyMember -> OutboxExecuteResult.Terminal("crew.error.alreadyMember")
            CrewError.Invite.CodeUnknown -> OutboxExecuteResult.Terminal("crew.error.codeUnknown")
            CrewError.Invite.Expired -> OutboxExecuteResult.Terminal("crew.error.codeExpired")
            CrewError.Create.CodeCollisionRetriesExhausted -> OutboxExecuteResult.Terminal("crew.error.codeCollision")
            CrewError.RemoveMember.NotOwner -> OutboxExecuteResult.Terminal("crew.error.removeNotOwner")
            CrewError.RemoveMember.CannotRemoveSelf -> OutboxExecuteResult.Terminal("crew.error.removeSelf")
        }
    }
}
