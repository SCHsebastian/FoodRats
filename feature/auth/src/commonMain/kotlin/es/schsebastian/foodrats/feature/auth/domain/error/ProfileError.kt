package es.schsebastian.foodrats.feature.auth.domain.error

import es.schsebastian.foodrats.core.domain.account.AccountDeletionError
import es.schsebastian.foodrats.core.domain.account.AccountWriteError
import es.schsebastian.foodrats.core.domain.preferences.LocalePreferenceError
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferenceError
import es.schsebastian.foodrats.core.domain.preferences.ThemePreferenceError

/**
 * User-facing error surface for the Profile / Settings screen. Aggregates failure
 * modes from every port the Settings VM talks to: identity writes, preference
 * persistence, account deletion. Mapped to a [es.schsebastian.foodrats.core.i18n.StringKey]
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

    sealed interface Theme : ProfileError {
        data object PersistFailed : Theme
    }

    sealed interface Locale : ProfileError {
        data object PersistFailed : Locale
    }

    sealed interface Notifications : ProfileError {
        data object PersistFailed : Notifications
        /** OS dialog returned a soft deny (Android: can re-prompt; iOS: not applicable). */
        data object PermissionDenied : Notifications
        /** OS dialog returned a hard deny — only system settings can re-enable. */
        data object PermissionDeniedForever : Notifications
    }

    sealed interface Delete : ProfileError {
        data object PhraseMismatch : Delete
        data object NotImplemented : Delete
        data object Unavailable : Delete
        data object OwnerOfActiveCrew : Delete
    }
}

internal fun AccountWriteError.toProfileError(): ProfileError = when (this) {
    AccountWriteError.Validation.DisplayNameBlank -> ProfileError.Validation.DisplayNameBlank
    AccountWriteError.Validation.DisplayNameTooLong -> ProfileError.Validation.DisplayNameTooLong
    AccountWriteError.Validation.EmptyBytes -> ProfileError.Validation.EmptyBytes
    AccountWriteError.Backend.Unavailable -> ProfileError.Backend.Unavailable
}

internal fun ThemePreferenceError.toProfileError(): ProfileError = when (this) {
    ThemePreferenceError.Persist.Unavailable -> ProfileError.Theme.PersistFailed
}

internal fun LocalePreferenceError.toProfileError(): ProfileError = when (this) {
    LocalePreferenceError.Persist.Unavailable -> ProfileError.Locale.PersistFailed
}

internal fun NotificationsPreferenceError.toProfileError(): ProfileError = when (this) {
    NotificationsPreferenceError.Persist.Unavailable -> ProfileError.Notifications.PersistFailed
}

internal fun AccountDeletionError.toProfileError(): ProfileError = when (this) {
    AccountDeletionError.Validation.PhraseMismatch -> ProfileError.Delete.PhraseMismatch
    AccountDeletionError.Backend.NotImplemented -> ProfileError.Delete.NotImplemented
    AccountDeletionError.Backend.Unavailable -> ProfileError.Delete.Unavailable
    AccountDeletionError.Ownership.OwnerOfActiveCrew -> ProfileError.Delete.OwnerOfActiveCrew
}
