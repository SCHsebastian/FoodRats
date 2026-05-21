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
    // EmptyBytes is structurally unreachable from the UI (picker won't return zero bytes),
    // but the mapping has to exist for exhaustiveness. Fall back to generic backend message.
    ProfileError.Validation.EmptyBytes -> AuthStringKey.ProfileBackendUnavailable
    ProfileError.Backend.Unavailable -> AuthStringKey.ProfileBackendUnavailable
    ProfileError.Session.SignedOut -> AuthStringKey.ProfileBackendUnavailable
}
