package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.account.AccountWritePort
import es.schsebastian.foodrats.core.domain.crew.CrewMemberCacheWritePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.domain.error.toProfileError

/**
 * Uploads the user's avatar bytes to Storage, writes the resulting URL to
 * `accounts/{uid}.avatarUrl` (canonical), and best-effort propagates it to the active
 * crew's denormalized member cache. Cache failures are logged via [CrashReporter] but do
 * not fail the use case. Returns the public URL on success so the caller can render the
 * new avatar immediately without waiting for the [AccountReadPort] flow to re-emit.
 */
class UpdateMyAvatarUseCase(
    private val accountWrite: AccountWritePort,
    private val memberCacheWrite: CrewMemberCacheWritePort,
    private val session: SessionProvider,
    private val crashReporter: CrashReporter,
) {
    suspend operator fun invoke(bytes: ByteArray): Result<String, ProfileError> {
        if (bytes.isEmpty()) return Result.failure(ProfileError.Validation.EmptyBytes)

        val current = when (val r = session.requireCurrent()) {
            is Result.Ok -> r.value
            is Result.Err -> return Result.failure(ProfileError.Session.SignedOut)
        }

        val url = when (val r = accountWrite.uploadAndSetAvatar(current.accountId, bytes)) {
            is Result.Ok -> r.value
            is Result.Err -> return Result.failure(r.error.toProfileError())
        }

        current.activeCrewId?.let { crewId ->
            val cacheResult = memberCacheWrite.setAvatarUrl(crewId, current.accountId, url)
            if (cacheResult is Result.Err) {
                crashReporter.log(
                    "member cache drift: op=setAvatarUrl" +
                        " crewId=${crewId.value} accountId=${current.accountId.value}"
                )
            }
        }
        return Result.success(url)
    }
}
