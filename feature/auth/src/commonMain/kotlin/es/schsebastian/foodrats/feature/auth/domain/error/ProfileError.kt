package es.schsebastian.foodrats.feature.auth.domain.error

import es.schsebastian.foodrats.core.domain.account.AccountDeletionError
import es.schsebastian.foodrats.core.domain.account.AccountWriteError
import es.schsebastian.foodrats.core.domain.account.DataExportError
import es.schsebastian.foodrats.core.domain.account.DisplayNameError
import es.schsebastian.foodrats.core.domain.preferences.AccentPaletteError
import es.schsebastian.foodrats.core.domain.preferences.AiPreferenceError
import es.schsebastian.foodrats.core.domain.preferences.LocalePreferenceError
import es.schsebastian.foodrats.core.domain.preferences.MealReminderPreferenceError
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
        data object BioTooLong : Validation
        data object EmptyBytes : Validation
    }

    sealed interface Backend : ProfileError {
        data object Unavailable : Backend

        /** Lost connectivity — the write couldn't reach the backend. Distinct user message. */
        data object Offline : Backend
    }

    sealed interface Session : ProfileError {
        /** Signed out / token expired — route the user to re-auth, don't offer endless retry. */
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

    sealed interface Reminders : ProfileError {
        data object PersistFailed : Reminders
    }

    sealed interface Delete : ProfileError {
        data object PhraseMismatch : Delete

        data object Unavailable : Delete

        /** Transient, retryable: server couldn't reassign an owned crew. Replaces OwnerOfActiveCrew. */
        data object OwnerReassignFailed : Delete
    }

    sealed interface Export : ProfileError {
        /** The data export couldn't be assembled/signed/uploaded; nothing destroyed, retryable. */
        data object Unavailable : Export
    }

    sealed interface Ai : ProfileError {
        data object PersistFailed : Ai
    }

    sealed interface Avatar : ProfileError {
        data object RemoveFailed : Avatar
    }

    /**
     * Getting a picked photo ready for upload failed BEFORE any backend call — the picker errored
     * or the on-device compressor refused the bytes (`AvatarCompression` never passes originals
     * through, so an unfit photo stops here instead of tripping the 1 MB avatars Storage rule).
     * Narrower than [Avatar] so the pick-flow intent can only carry prepare reasons.
     */
    sealed interface AvatarPrepare : ProfileError {
        /** Even maximum compression couldn't fit the photo under the upload byte cap. */
        data object TooLarge : AvatarPrepare

        /** The platform codec couldn't decode/re-encode the picked bytes. */
        data object Unreadable : AvatarPrepare

        /** The photo picker itself failed to deliver bytes. */
        data object PickFailed : AvatarPrepare
    }

    sealed interface Accent : ProfileError {
        data object PersistFailed : Accent
    }
}

internal fun AccountWriteError.toProfileError(): ProfileError = when (this) {
    AccountWriteError.Session.Expired -> ProfileError.Session.SignedOut
    AccountWriteError.Backend.Network -> ProfileError.Backend.Offline
    AccountWriteError.Backend.Unavailable -> ProfileError.Backend.Unavailable
    // Permission-denied on your OWN account write is a doc-shape bug, not a user-actionable state —
    // surface the generic backend message (it's also recorded as a non-fatal at the mapping seam).
    AccountWriteError.Backend.PermissionDenied -> ProfileError.Backend.Unavailable
    AccountWriteError.Backend.Unknown -> ProfileError.Backend.Unavailable
}

internal fun DisplayNameError.toProfileError(): ProfileError = when (this) {
    DisplayNameError.Validation.Blank -> ProfileError.Validation.DisplayNameBlank
    DisplayNameError.Validation.TooLong -> ProfileError.Validation.DisplayNameTooLong
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

internal fun MealReminderPreferenceError.toProfileError(): ProfileError = when (this) {
    MealReminderPreferenceError.Persist.Unavailable -> ProfileError.Reminders.PersistFailed
}

internal fun AccountDeletionError.toProfileError(): ProfileError = when (this) {
    AccountDeletionError.Validation.PhraseMismatch -> ProfileError.Delete.PhraseMismatch
    AccountDeletionError.Backend.Unavailable -> ProfileError.Delete.Unavailable
    AccountDeletionError.Deletion.OwnerReassignFailed -> ProfileError.Delete.OwnerReassignFailed
}

internal fun DataExportError.toProfileError(): ProfileError = when (this) {
    DataExportError.Session.Unauthenticated -> ProfileError.Session.SignedOut
    DataExportError.Backend.Unavailable -> ProfileError.Export.Unavailable
}

internal fun AiPreferenceError.toProfileError(): ProfileError = when (this) {
    AiPreferenceError.Persist.Unavailable -> ProfileError.Ai.PersistFailed
}

internal fun AccentPaletteError.toProfileError(): ProfileError = when (this) {
    AccentPaletteError.Persist.Unavailable -> ProfileError.Accent.PersistFailed
}

internal fun AccountWriteError.toRemoveAvatarProfileError(): ProfileError = when (this) {
    // Session/connectivity get their own routes; only genuine backend/permission failures are a
    // generic "couldn't remove avatar".
    AccountWriteError.Session.Expired -> ProfileError.Session.SignedOut
    AccountWriteError.Backend.Network -> ProfileError.Backend.Offline
    AccountWriteError.Backend.Unavailable,
    AccountWriteError.Backend.PermissionDenied,
    AccountWriteError.Backend.Unknown -> ProfileError.Avatar.RemoveFailed
}
