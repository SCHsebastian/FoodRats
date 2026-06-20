package es.schsebastian.foodrats.feature.auth.presentation.profile

import es.schsebastian.foodrats.core.i18n.StringKey
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.i18n.AuthStringKey

/**
 * Maps every [ProfileError] leaf to a user-facing [StringKey]. Exhaustive `when` so the
 * companion test catches any future branch that forgets a mapping.
 */
internal fun ProfileError.toStringKey(): StringKey = when (this) {
    ProfileError.Validation.DisplayNameBlank -> AuthStringKey.ProfileDisplayNameBlank
    ProfileError.Validation.DisplayNameTooLong -> AuthStringKey.ProfileDisplayNameTooLong
    ProfileError.Validation.BioTooLong -> AuthStringKey.ProfileBioTooLong
    ProfileError.Validation.EmptyBytes -> AuthStringKey.ProfileAvatarEmptyBytes
    ProfileError.Backend.Unavailable -> AuthStringKey.ProfileBackendUnavailable
    ProfileError.Session.SignedOut -> AuthStringKey.ProfileBackendUnavailable
    ProfileError.Theme.PersistFailed -> AuthStringKey.ProfileThemePersistFailed
    ProfileError.Locale.PersistFailed -> AuthStringKey.ProfileLanguagePersistFailed
    ProfileError.Notifications.PersistFailed -> AuthStringKey.ProfileNotificationsPersistFailed
    ProfileError.Notifications.PermissionDenied -> AuthStringKey.ProfileNotificationsPermissionDenied
    ProfileError.Notifications.PermissionDeniedForever -> AuthStringKey.ProfileNotificationsPermissionDeniedForever
    ProfileError.Reminders.PersistFailed -> AuthStringKey.ProfileRemindersPersistFailed
    ProfileError.Delete.PhraseMismatch -> AuthStringKey.DeleteAccountErrorPhrase
    ProfileError.Delete.Unavailable -> AuthStringKey.DeleteAccountErrorBackend
    ProfileError.Delete.OwnerReassignFailed -> AuthStringKey.DeleteAccountErrorOwnership
    ProfileError.Export.Unavailable -> AuthStringKey.ExportDataErrorBackend
    ProfileError.Ai.PersistFailed -> AuthStringKey.ProfileAiPersistFailed
    ProfileError.Avatar.RemoveFailed -> AuthStringKey.ProfileRemoveAvatarError
}
