package es.schsebastian.foodrats.feature.auth.domain.error

import es.schsebastian.foodrats.core.domain.account.AccountWriteError

/**
 * User-facing error surface for the Profile screen. Aggregates failure modes from
 * [AccountWriteError] (canonical write path) and [es.schsebastian.foodrats.core.domain.session.SessionError]
 * (sign-out / signed-out checks). Mapped to a [es.schsebastian.foodrats.core.i18n.StringKey]
 * by `ProfileErrorToStringKey` for display.
 */
sealed interface ProfileError {
    sealed interface Validation : ProfileError {
        data object DisplayNameBlank : Validation
        data object DisplayNameTooLong : Validation
        data object EmptyBytes : Validation
    }

    sealed interface Backend : ProfileError {
        data object Unavailable : Backend
    }

    sealed interface Session : ProfileError {
        data object SignedOut : Session
    }
}

internal fun AccountWriteError.toProfileError(): ProfileError = when (this) {
    AccountWriteError.Validation.DisplayNameBlank -> ProfileError.Validation.DisplayNameBlank
    AccountWriteError.Validation.DisplayNameTooLong -> ProfileError.Validation.DisplayNameTooLong
    AccountWriteError.Validation.EmptyBytes -> ProfileError.Validation.EmptyBytes
    AccountWriteError.Backend.Unavailable -> ProfileError.Backend.Unavailable
}
