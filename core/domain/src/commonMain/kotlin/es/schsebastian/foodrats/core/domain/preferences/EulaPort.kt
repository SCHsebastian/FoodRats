package es.schsebastian.foodrats.core.domain.preferences

import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Current EULA / Community-Guidelines revision. Bump when the legal text changes materially → users
 * below this version are re-prompted to accept (see [needsEulaAcceptance]). Starts at 1.
 */
const val CURRENT_EULA_VERSION: Int = 1

/**
 * Typed failures for recording EULA acceptance. Sealed (domain-error convention) so call sites exhaust
 * it. Local-first over DataStore, so the only realistic failure is the persistence write.
 */
sealed interface EulaError {
    /** The local store could not persist the acceptance. */
    data object PersistFailed : EulaError
}

/**
 * Reads and writes the user's EULA / Community-Guidelines acceptance (UGC compliance §6). Mirrors the
 * `ConsentPort` shape — local-first over `AppPreferences` (DataStore) so the login-screen acceptance
 * gate works before any network. Implemented in `:core:data` by `EulaRepository`.
 *
 * Acceptance is intentionally NOT cleared on sign-out: a EULA is accepted by the human/device, not the
 * account.
 */
interface EulaPort {
    /** The accepted EULA version, or `null` if the user has never accepted. */
    val acceptedVersion: Flow<Int?>

    /** Records acceptance at [version] (callers pass [CURRENT_EULA_VERSION]). */
    suspend fun accept(version: Int): Result<Unit, EulaError>
}

/**
 * No-op acceptance recorder — the default for ViewModels/previews/tests so fixtures that don't care
 * about EULA persistence stay green (mirrors `NoopAnalyticsTracker`). [acceptedVersion] never emits
 * (acceptance is irrelevant in those contexts) and [accept] always succeeds.
 */
object NoopEulaAcceptance : EulaPort {
    override val acceptedVersion: Flow<Int?> = emptyFlow()
    override suspend fun accept(version: Int): Result<Unit, EulaError> = Result.success(Unit)
}

/**
 * True when the login screen must require (re-)acceptance: [accepted] is `null` (never accepted) or
 * below [current] (the legal text was bumped → re-consent). The single predicate the acceptance gate
 * trusts.
 *
 * @param current the active EULA version, normally [CURRENT_EULA_VERSION].
 * @param accepted the user's stored accepted version (`null` = never accepted).
 */
fun needsEulaAcceptance(current: Int, accepted: Int?): Boolean = (accepted ?: 0) < current
