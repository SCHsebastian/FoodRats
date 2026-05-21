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

    @Test fun empty_bytes_maps_to_backend_unavailable() =
        assertEquals(AuthStringKey.ProfileBackendUnavailable, ProfileError.Validation.EmptyBytes.toStringKey())

    @Test fun backend_maps_to_backend_unavailable() =
        assertEquals(AuthStringKey.ProfileBackendUnavailable, ProfileError.Backend.Unavailable.toStringKey())

    @Test fun signed_out_maps_to_backend_unavailable() =
        assertEquals(AuthStringKey.ProfileBackendUnavailable, ProfileError.Session.SignedOut.toStringKey())
}
