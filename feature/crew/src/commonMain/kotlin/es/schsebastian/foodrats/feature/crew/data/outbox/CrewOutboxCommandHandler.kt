package es.schsebastian.foodrats.feature.crew.data.outbox

import es.schsebastian.foodrats.core.domain.crew.CrewScoreStyle
import es.schsebastian.foodrats.core.domain.outbox.OutboxCommandHandler
import es.schsebastian.foodrats.core.domain.outbox.OutboxExecuteResult
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository

/**
 * Replays the crew-admin + crew-settings outbox commands (offline-first P2 §1 T6) —
 * [PendingCommand.RenameCrew], [PendingCommand.SetBlindVoting],
 * [PendingCommand.RemoveMember], [PendingCommand.LeaveCrew], plus the C-series settings
 * [PendingCommand.SetCrewTagline], [PendingCommand.SetCrewWelcomeMessage],
 * [PendingCommand.SetCrewWeeklyChallenge], [PendingCommand.SetCrewScoreStyle], and
 * [PendingCommand.SetCrewBannerFocalY] — against [CrewRepository]. All are idempotent
 * server-side (each sets a value; remove/leave a no-longer-present member succeeds), so a
 * replay never applies twice. (The banner IMAGE bytes are NOT queued — only its focal point.)
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
        is PendingCommand.LeaveCrew,
        is PendingCommand.SetCrewTagline,
        is PendingCommand.SetCrewWelcomeMessage,
        is PendingCommand.SetCrewWeeklyChallenge,
        is PendingCommand.SetCrewScoreStyle,
        is PendingCommand.SetCrewBannerFocalY -> true
        is PendingCommand.RateMeal,
        is PendingCommand.PostComment,
        is PendingCommand.EditComment,
        is PendingCommand.DeleteComment,
        is PendingCommand.ToggleReaction,
        is PendingCommand.SetDisplayName,
        is PendingCommand.SetBio -> false
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
        is PendingCommand.SetCrewTagline ->
            crews.setTagline(cmd.crewId, cmd.requestedBy, cmd.tagline).toExecuteResult()
        is PendingCommand.SetCrewWelcomeMessage ->
            crews.setWelcomeMessage(cmd.crewId, cmd.requestedBy, cmd.message).toExecuteResult()
        is PendingCommand.SetCrewWeeklyChallenge ->
            crews.setWeeklyChallenge(cmd.crewId, cmd.requestedBy, cmd.challenge, cmd.setAtMillis).toExecuteResult()
        is PendingCommand.SetCrewScoreStyle ->
            // Re-parse the persisted style key. A key a NEWER build wrote that this build doesn't
            // know is terminal (can't replay it) — mirrors ToggleReaction's unknown-kind handling.
            CrewScoreStyle.fromKey(cmd.styleKey)
                ?.let { crews.setScoreStyle(cmd.crewId, cmd.requestedBy, it).toExecuteResult() }
                ?: OutboxExecuteResult.Terminal("crew.error.scoreStyleUnknown")
        is PendingCommand.SetCrewBannerFocalY ->
            crews.setBannerFocalY(cmd.crewId, cmd.requestedBy, cmd.focalY).toExecuteResult()
        // Not ours — [handles] returns false for these, so the runner never routes
        // them here. Mapped to a terminal so an accidental dispatch can't loop.
        is PendingCommand.RateMeal,
        is PendingCommand.PostComment,
        is PendingCommand.EditComment,
        is PendingCommand.DeleteComment,
        is PendingCommand.ToggleReaction,
        is PendingCommand.SetDisplayName,
        is PendingCommand.SetBio -> OutboxExecuteResult.Terminal("outbox.error.wrongHandler")
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
            // Unclassifiable backend failure — terminal so it can't loop forever (the transient
            // codes are mapped to Backend.Unavailable above; this is the genuine catch-all).
            CrewError.Backend.Unknown -> OutboxExecuteResult.Terminal("crew.error.backendUnknown")
            // Session loss — the command can't replay without a signed-in account; the root
            // navigator routes the user to sign-in. Terminal here.
            CrewError.Session.NotSignedIn -> OutboxExecuteResult.Terminal("crew.error.notSignedIn")
            CrewError.Session.Expired -> OutboxExecuteResult.Terminal("crew.error.sessionExpired")
            CrewError.Backend.PermissionDenied -> OutboxExecuteResult.Terminal("crew.error.permissionDenied")
            CrewError.Authorization.NotOwner -> OutboxExecuteResult.Terminal("crew.error.notOwner")
            CrewError.Validation.NameBlank -> OutboxExecuteResult.Terminal("crew.error.nameBlank")
            CrewError.Validation.NameTooLong -> OutboxExecuteResult.Terminal("crew.error.nameTooLong")
            CrewError.Validation.CodeMalformed -> OutboxExecuteResult.Terminal("crew.error.codeMalformed")
            CrewError.Validation.DisplayNameBlank -> OutboxExecuteResult.Terminal("crew.error.displayNameBlank")
            CrewError.Validation.DisplayNameTooLong -> OutboxExecuteResult.Terminal("crew.error.displayNameTooLong")
            CrewError.Validation.TaglineTooLong -> OutboxExecuteResult.Terminal("crew.error.taglineTooLong")
            CrewError.Validation.WelcomeMessageTooLong -> OutboxExecuteResult.Terminal("crew.error.welcomeMessageTooLong")
            CrewError.Validation.WeeklyChallengeTooLong -> OutboxExecuteResult.Terminal("crew.error.weeklyChallengeTooLong")
            CrewError.Membership.NotFound -> OutboxExecuteResult.Terminal("crew.error.crewNotFound")
            CrewError.Membership.Full -> OutboxExecuteResult.Terminal("crew.error.crewFull")
            CrewError.Membership.NotInvited -> OutboxExecuteResult.Terminal("crew.error.notInvited")
            CrewError.Membership.AlreadyMember -> OutboxExecuteResult.Terminal("crew.error.alreadyMember")
            CrewError.Invite.CodeUnknown -> OutboxExecuteResult.Terminal("crew.error.codeUnknown")
            CrewError.Invite.Expired -> OutboxExecuteResult.Terminal("crew.error.codeExpired")
            CrewError.Invite.AlreadyRequested -> OutboxExecuteResult.Terminal("crew.error.alreadyRequested")
            CrewError.Create.CodeCollisionRetriesExhausted -> OutboxExecuteResult.Terminal("crew.error.codeCollision")
            CrewError.RemoveMember.NotOwner -> OutboxExecuteResult.Terminal("crew.error.removeNotOwner")
            CrewError.RemoveMember.CannotRemoveSelf -> OutboxExecuteResult.Terminal("crew.error.removeSelf")
            // Transfer-ownership is online-only (never queued), but the when over CrewError must
            // stay exhaustive — a transfer error reaching here is unreplayable, so terminal.
            CrewError.Transfer.NotOwner -> OutboxExecuteResult.Terminal("crew.error.transferNotOwner")
            CrewError.Transfer.TargetNotMember -> OutboxExecuteResult.Terminal("crew.error.transferTargetNotMember")
            CrewError.Transfer.CannotTransferToSelf -> OutboxExecuteResult.Terminal("crew.error.transferToSelf")
            // C9 — banner IMAGE errors are never queued (bytes not serializable; the focal point IS
            // queued via SetCrewBannerFocalY). Listed so the when stays exhaustive.
            CrewError.Banner.UploadFailed -> OutboxExecuteResult.Terminal("crew.error.bannerUploadFailed")
            CrewError.Banner.DeleteFailed -> OutboxExecuteResult.Terminal("crew.error.bannerDeleteFailed")
            CrewError.Banner.ImageTooLarge -> OutboxExecuteResult.Terminal("crew.error.bannerImageTooLarge")
            CrewError.Banner.ImageUnreadable -> OutboxExecuteResult.Terminal("crew.error.bannerImageUnreadable")
            CrewError.Banner.PickFailed -> OutboxExecuteResult.Terminal("crew.error.bannerPickFailed")
        }
    }
}
