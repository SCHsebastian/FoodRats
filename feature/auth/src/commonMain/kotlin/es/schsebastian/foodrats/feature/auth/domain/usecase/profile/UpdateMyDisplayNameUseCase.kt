package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.account.AccountWritePort
import es.schsebastian.foodrats.core.domain.crew.CrewMemberCacheWritePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.domain.error.toProfileError

/**
 * Updates the user's canonical display name and propagates it to the active crew's
 * denormalized member cache. The canonical write to `accounts/{uid}` is required —
 * a failure short-circuits and surfaces the error. The cache write is best-effort:
 * a failure is reported via [CrashReporter] but does not fail the use case.
 *
 * Trim and length validation live here (max 40 chars). The two-write split is intentional;
 * see docs/specs/2026-05-21-your-profile-split-design.md §4.3 for the atomicity rationale.
 */
class UpdateMyDisplayNameUseCase(
    private val accountWrite: AccountWritePort,
    private val memberCacheWrite: CrewMemberCacheWritePort,
    private val session: SessionProvider,
    private val crashReporter: CrashReporter,
) {
    suspend operator fun invoke(name: String): Result<Unit, ProfileError> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(ProfileError.Validation.DisplayNameBlank)
        if (trimmed.length > MAX) return Result.failure(ProfileError.Validation.DisplayNameTooLong)

        val current = when (val r = session.requireCurrent()) {
            is Result.Ok -> r.value
            is Result.Err -> return Result.failure(ProfileError.Session.SignedOut)
        }

        when (val r = accountWrite.updateDisplayName(current.accountId, trimmed)) {
            is Result.Err -> return Result.failure(r.error.toProfileError())
            is Result.Ok -> Unit
        }

        current.activeCrewId?.let { crewId ->
            val cacheResult = memberCacheWrite.setDisplayName(crewId, current.accountId, trimmed)
            if (cacheResult is Result.Err) {
                crashReporter.log(
                    "member cache drift: op=setDisplayName" +
                        " crewId=${crewId.value} accountId=${current.accountId.value}"
                )
            }
        }
        return Result.success(Unit)
    }

    companion object {
        const val MAX = 40
    }
}
