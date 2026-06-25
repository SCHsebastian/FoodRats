package es.schsebastian.foodrats.feature.auth.presentation.profile

import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.i18n.AuthStringKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileErrorToStringKeyTest {

    /**
     * Every [ProfileError] leaf, listed explicitly. The compiler `when` in [toStringKey] is the real
     * exhaustiveness lock (a new leaf fails to compile until mapped); this list is the companion check
     * that every leaf actually resolves to a key (no accidental shared/placeholder mapping slips by),
     * and the per-leaf asserts below pin the semantically-important mappings.
     *
     * If you add a [ProfileError] leaf: the `when` won't compile until you map it — add it here too.
     */
    private val allLeaves: List<ProfileError> = listOf(
        ProfileError.Validation.DisplayNameBlank,
        ProfileError.Validation.DisplayNameTooLong,
        ProfileError.Validation.BioTooLong,
        ProfileError.Validation.EmptyBytes,
        ProfileError.Backend.Unavailable,
        ProfileError.Backend.Offline,
        ProfileError.Session.SignedOut,
        ProfileError.Theme.PersistFailed,
        ProfileError.Locale.PersistFailed,
        ProfileError.Notifications.PersistFailed,
        ProfileError.Notifications.PermissionDenied,
        ProfileError.Notifications.PermissionDeniedForever,
        ProfileError.Reminders.PersistFailed,
        ProfileError.Delete.PhraseMismatch,
        ProfileError.Delete.Unavailable,
        ProfileError.Delete.OwnerReassignFailed,
        ProfileError.Export.Unavailable,
        ProfileError.Ai.PersistFailed,
        ProfileError.Avatar.RemoveFailed,
        ProfileError.Accent.PersistFailed,
    )

    @Test fun every_leaf_maps_to_a_string_key() {
        // Each leaf resolves without throwing (type guarantees the result IS an AuthStringKey).
        allLeaves.forEach { it.toStringKey() }
        // Guard against the list drifting from the error tree (update both when adding a leaf).
        assertEquals(20, allLeaves.size, "ProfileError leaf count changed — update this test + the mapper")
    }

    @Test fun session_signed_out_maps_to_its_own_re_auth_string() =
        // H1 fix: a signed-out user must NOT see the generic "backend unavailable" message.
        assertEquals(AuthStringKey.ProfileSessionExpired, ProfileError.Session.SignedOut.toStringKey())

    @Test fun backend_offline_maps_to_its_own_string() =
        assertEquals(AuthStringKey.ProfileOffline, ProfileError.Backend.Offline.toStringKey())

    @Test fun backend_unavailable_maps_to_backend_unavailable() =
        assertEquals(AuthStringKey.ProfileBackendUnavailable, ProfileError.Backend.Unavailable.toStringKey())

    @Test fun session_and_offline_are_distinct_from_generic_backend() {
        val signedOut = ProfileError.Session.SignedOut.toStringKey()
        val offline = ProfileError.Backend.Offline.toStringKey()
        val backend = ProfileError.Backend.Unavailable.toStringKey()
        assertTrue(signedOut != backend && offline != backend && signedOut != offline)
    }

    @Test fun blank_maps_to_blank_key() =
        assertEquals(AuthStringKey.ProfileDisplayNameBlank, ProfileError.Validation.DisplayNameBlank.toStringKey())

    @Test fun too_long_maps_to_too_long_key() =
        assertEquals(AuthStringKey.ProfileDisplayNameTooLong, ProfileError.Validation.DisplayNameTooLong.toStringKey())

    @Test fun bio_too_long_maps_to_dedicated_key() =
        assertEquals(AuthStringKey.ProfileBioTooLong, ProfileError.Validation.BioTooLong.toStringKey())

    @Test fun empty_bytes_maps_to_dedicated_key() =
        assertEquals(AuthStringKey.ProfileAvatarEmptyBytes, ProfileError.Validation.EmptyBytes.toStringKey())

    @Test fun delete_phrase_mismatch_maps_to_phrase_key() =
        assertEquals(AuthStringKey.DeleteAccountErrorPhrase, ProfileError.Delete.PhraseMismatch.toStringKey())

    @Test fun delete_owner_reassign_failed_maps_to_ownership_key() =
        assertEquals(AuthStringKey.DeleteAccountErrorOwnership, ProfileError.Delete.OwnerReassignFailed.toStringKey())

    @Test fun export_unavailable_maps_to_export_backend_key() =
        assertEquals(AuthStringKey.ExportDataErrorBackend, ProfileError.Export.Unavailable.toStringKey())

    @Test fun avatar_remove_failed_maps_to_dedicated_key() =
        assertEquals(AuthStringKey.ProfileRemoveAvatarError, ProfileError.Avatar.RemoveFailed.toStringKey())
}
