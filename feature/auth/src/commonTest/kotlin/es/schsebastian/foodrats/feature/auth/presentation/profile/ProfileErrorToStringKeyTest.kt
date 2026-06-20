package es.schsebastian.foodrats.feature.auth.presentation.profile

import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.i18n.AuthStringKey
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileErrorToStringKeyTest {
    @Test fun blank_maps_to_blank_key() =
        assertEquals(AuthStringKey.ProfileDisplayNameBlank, ProfileError.Validation.DisplayNameBlank.toStringKey())

    @Test fun too_long_maps_to_too_long_key() =
        assertEquals(AuthStringKey.ProfileDisplayNameTooLong, ProfileError.Validation.DisplayNameTooLong.toStringKey())

    @Test fun empty_bytes_maps_to_dedicated_key() =
        assertEquals(AuthStringKey.ProfileAvatarEmptyBytes, ProfileError.Validation.EmptyBytes.toStringKey())

    @Test fun backend_maps_to_backend_unavailable() =
        assertEquals(AuthStringKey.ProfileBackendUnavailable, ProfileError.Backend.Unavailable.toStringKey())

    @Test fun signed_out_maps_to_backend_unavailable() =
        assertEquals(AuthStringKey.ProfileBackendUnavailable, ProfileError.Session.SignedOut.toStringKey())

    @Test fun theme_persist_maps_to_dedicated_key() =
        assertEquals(AuthStringKey.ProfileThemePersistFailed, ProfileError.Theme.PersistFailed.toStringKey())

    @Test fun locale_persist_maps_to_dedicated_key() =
        assertEquals(AuthStringKey.ProfileLanguagePersistFailed, ProfileError.Locale.PersistFailed.toStringKey())

    @Test fun notifications_persist_maps_to_dedicated_key() =
        assertEquals(AuthStringKey.ProfileNotificationsPersistFailed, ProfileError.Notifications.PersistFailed.toStringKey())

    @Test fun notifications_permission_denied_maps_to_dedicated_key() =
        assertEquals(AuthStringKey.ProfileNotificationsPermissionDenied, ProfileError.Notifications.PermissionDenied.toStringKey())

    @Test fun notifications_permission_denied_forever_maps_to_dedicated_key() =
        assertEquals(AuthStringKey.ProfileNotificationsPermissionDeniedForever, ProfileError.Notifications.PermissionDeniedForever.toStringKey())

    @Test fun reminders_persist_maps_to_dedicated_key() =
        assertEquals(AuthStringKey.ProfileRemindersPersistFailed, ProfileError.Reminders.PersistFailed.toStringKey())

    @Test fun delete_phrase_mismatch_maps_to_phrase_key() =
        assertEquals(AuthStringKey.DeleteAccountErrorPhrase, ProfileError.Delete.PhraseMismatch.toStringKey())

    @Test fun delete_unavailable_maps_to_backend_key() =
        assertEquals(AuthStringKey.DeleteAccountErrorBackend, ProfileError.Delete.Unavailable.toStringKey())

    @Test fun delete_owner_reassign_failed_maps_to_ownership_key() =
        assertEquals(AuthStringKey.DeleteAccountErrorOwnership, ProfileError.Delete.OwnerReassignFailed.toStringKey())

    @Test fun export_unavailable_maps_to_export_backend_key() =
        assertEquals(AuthStringKey.ExportDataErrorBackend, ProfileError.Export.Unavailable.toStringKey())

    @Test fun ai_persist_failed_maps_to_ai_persist_failed_key() =
        assertEquals(AuthStringKey.ProfileAiPersistFailed, ProfileError.Ai.PersistFailed.toStringKey())

    @Test fun bio_too_long_maps_to_dedicated_key() =
        assertEquals(AuthStringKey.ProfileBioTooLong, ProfileError.Validation.BioTooLong.toStringKey())

    @Test fun avatar_remove_failed_maps_to_dedicated_key() =
        assertEquals(AuthStringKey.ProfileRemoveAvatarError, ProfileError.Avatar.RemoveFailed.toStringKey())
}
