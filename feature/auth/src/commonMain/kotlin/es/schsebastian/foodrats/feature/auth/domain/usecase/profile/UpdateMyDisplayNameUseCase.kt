package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.account.AccountWriteError
import es.schsebastian.foodrats.core.domain.account.AccountWritePort
import es.schsebastian.foodrats.core.domain.account.DisplayName
import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.outbox.OutboxPort
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.domain.error.toProfileError
import kotlinx.coroutines.flow.first

/**
 * Updates the user's canonical display name. Validation (trim / blank / 40-char cap) lives in the
 * [DisplayName] value object — the single place the rule is enforced (it used to be duplicated in
 * the data source).
 *
 * OFFLINE-FIRST (mirrors `RenameCrewUseCase`): when the device is offline — or the direct write
 * fails with a connectivity-class error — the edit is durably parked in the [OutboxPort] and the
 * use case returns [Result.Ok]; the `OutboxRunner` replays it (idempotently — sets the name) when
 * connectivity returns. The crew members list resolves identity live via `AccountReadPort`, so a
 * single write to `accounts/{uid}` is enough.
 */
class UpdateMyDisplayNameUseCase(
    private val accountWrite: AccountWritePort,
    private val session: SessionProvider,
    private val connectivity: ConnectivityPort,
    private val outbox: OutboxPort,
) {
    suspend operator fun invoke(name: String): Result<Unit, ProfileError> {
        val displayName = when (val v = DisplayName.of(name)) {
            is Result.Ok -> v.value
            is Result.Err -> return Result.failure(v.error.toProfileError())
        }

        val accountId = when (val r = session.requireCurrent()) {
            is Result.Ok -> r.value.accountId
            is Result.Err -> return Result.failure(ProfileError.Session.SignedOut)
        }

        if (!connectivity.isOnline().first()) {
            return enqueue(accountId, displayName)
        }
        return when (val r = accountWrite.updateDisplayName(accountId, displayName)) {
            is Result.Ok -> r
            is Result.Err -> when (r.error) {
                AccountWriteError.Backend.Network,
                AccountWriteError.Backend.Unavailable -> enqueue(accountId, displayName)
                else -> Result.failure(r.error.toProfileError())
            }
        }
    }

    private suspend fun enqueue(accountId: AccountId, displayName: DisplayName): Result<Unit, ProfileError> {
        outbox.enqueue(PendingCommand.SetDisplayName(accountId, displayName))
        return Result.success(Unit)
    }
}
