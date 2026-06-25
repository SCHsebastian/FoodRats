package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.account.AccountWriteError
import es.schsebastian.foodrats.core.domain.account.AccountWritePort
import es.schsebastian.foodrats.core.domain.account.Bio
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
 * Updates the user's personal bio. Validates the raw string via [Bio.of] (trims, cap 100 chars,
 * blank = clear). A blank value is treated as "remove bio" — persists `null`.
 *
 * OFFLINE-FIRST (mirrors `RenameCrewUseCase` / [UpdateMyDisplayNameUseCase]): offline — or on a
 * connectivity-class write failure — the edit is durably queued via [OutboxPort] and the use case
 * returns [Result.Ok]; the runner replays it (idempotently) on reconnect.
 */
class UpdateMyBioUseCase(
    private val accountWrite: AccountWritePort,
    private val session: SessionProvider,
    private val connectivity: ConnectivityPort,
    private val outbox: OutboxPort,
) {
    suspend operator fun invoke(raw: String): Result<Unit, ProfileError> {
        val bio = when (val v = Bio.of(raw)) {
            is Result.Ok -> v.value   // Bio? — null means "clear"
            is Result.Err -> return Result.failure(ProfileError.Validation.BioTooLong)
        }

        val accountId = when (val r = session.requireCurrent()) {
            is Result.Ok -> r.value.accountId
            is Result.Err -> return Result.failure(ProfileError.Session.SignedOut)
        }

        if (!connectivity.isOnline().first()) {
            return enqueue(accountId, bio)
        }
        return when (val r = accountWrite.updateBio(accountId, bio)) {
            is Result.Ok -> r
            is Result.Err -> when (r.error) {
                AccountWriteError.Backend.Network,
                AccountWriteError.Backend.Unavailable -> enqueue(accountId, bio)
                else -> Result.failure(r.error.toProfileError())
            }
        }
    }

    private suspend fun enqueue(accountId: AccountId, bio: Bio?): Result<Unit, ProfileError> {
        outbox.enqueue(PendingCommand.SetBio(accountId, bio))
        return Result.success(Unit)
    }
}
