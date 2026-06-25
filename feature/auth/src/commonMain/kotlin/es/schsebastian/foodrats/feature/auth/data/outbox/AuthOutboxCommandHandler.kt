package es.schsebastian.foodrats.feature.auth.data.outbox

import es.schsebastian.foodrats.core.domain.account.AccountWriteError
import es.schsebastian.foodrats.core.domain.account.AccountWritePort
import es.schsebastian.foodrats.core.domain.outbox.OutboxCommandHandler
import es.schsebastian.foodrats.core.domain.outbox.OutboxExecuteResult
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result

/**
 * Replays the offline-first profile-TEXT outbox commands — [PendingCommand.SetDisplayName] and
 * [PendingCommand.SetBio] — against [AccountWritePort]. Both are idempotent server-side (each sets a
 * value; last-write-wins on `accounts/{uid}`), so a replay never applies twice. The avatar BYTES are
 * NOT queued (they can't ride the flattened-column outbox — see [PendingCommand]); only the text
 * fields are offline-first.
 *
 * The cross-feature `OutboxRunner` lives in `:core:data` (which must never import a `:feature:*`),
 * so this handler — which knows [AccountWritePort] — lives here and is contributed to the runner via
 * Koin `getAll()` (`single<OutboxCommandHandler>` in `authModule`). Sibling of
 * `CrewOutboxCommandHandler` / `MealOutboxCommandHandler`.
 *
 * Error classification ([AccountWriteError] → [OutboxExecuteResult]):
 *  - [AccountWriteError.Backend.Network] / [AccountWriteError.Backend.Unavailable] → Retryable.
 *  - [AccountWriteError.Session.Expired] → Terminal (can't replay without a signed-in account).
 *  - permission-denied / unknown → Terminal (retrying cannot fix it).
 */
class AuthOutboxCommandHandler(
    private val accountWrite: AccountWritePort,
) : OutboxCommandHandler {

    override fun handles(cmd: PendingCommand): Boolean = when (cmd) {
        is PendingCommand.SetDisplayName,
        is PendingCommand.SetBio -> true
        is PendingCommand.RateMeal,
        is PendingCommand.PostComment,
        is PendingCommand.EditComment,
        is PendingCommand.DeleteComment,
        is PendingCommand.ToggleReaction,
        is PendingCommand.RenameCrew,
        is PendingCommand.SetBlindVoting,
        is PendingCommand.RemoveMember,
        is PendingCommand.LeaveCrew,
        is PendingCommand.SetCrewTagline,
        is PendingCommand.SetCrewWelcomeMessage,
        is PendingCommand.SetCrewWeeklyChallenge,
        is PendingCommand.SetCrewScoreStyle,
        is PendingCommand.SetCrewBannerFocalY -> false
    }

    override suspend fun execute(cmd: PendingCommand): OutboxExecuteResult = when (cmd) {
        is PendingCommand.SetDisplayName ->
            accountWrite.updateDisplayName(cmd.accountId, cmd.displayName).toExecuteResult()
        is PendingCommand.SetBio ->
            accountWrite.updateBio(cmd.accountId, cmd.bio).toExecuteResult()
        // Not ours — [handles] returns false for these, so the runner never routes them here.
        // Mapped to terminal so an accidental dispatch can't loop.
        is PendingCommand.RateMeal,
        is PendingCommand.PostComment,
        is PendingCommand.EditComment,
        is PendingCommand.DeleteComment,
        is PendingCommand.ToggleReaction,
        is PendingCommand.RenameCrew,
        is PendingCommand.SetBlindVoting,
        is PendingCommand.RemoveMember,
        is PendingCommand.LeaveCrew,
        is PendingCommand.SetCrewTagline,
        is PendingCommand.SetCrewWelcomeMessage,
        is PendingCommand.SetCrewWeeklyChallenge,
        is PendingCommand.SetCrewScoreStyle,
        is PendingCommand.SetCrewBannerFocalY -> OutboxExecuteResult.Terminal("outbox.error.wrongHandler")
    }

    private fun Result<Unit, AccountWriteError>.toExecuteResult(): OutboxExecuteResult = when (this) {
        is Result.Ok -> OutboxExecuteResult.Success
        is Result.Err -> when (error) {
            // Transient — back off and retry.
            AccountWriteError.Backend.Network,
            AccountWriteError.Backend.Unavailable -> OutboxExecuteResult.Retryable("auth.error.backendUnavailable")
            // Session loss — the root navigator routes the user to sign-in; can't replay here.
            AccountWriteError.Session.Expired -> OutboxExecuteResult.Terminal("auth.error.sessionExpired")
            // Rules rejection / unclassified — retrying cannot fix it.
            AccountWriteError.Backend.PermissionDenied -> OutboxExecuteResult.Terminal("auth.error.permissionDenied")
            AccountWriteError.Backend.Unknown -> OutboxExecuteResult.Terminal("auth.error.backendUnknown")
        }
    }
}
