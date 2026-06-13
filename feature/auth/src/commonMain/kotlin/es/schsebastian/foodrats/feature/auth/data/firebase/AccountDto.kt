package es.schsebastian.foodrats.feature.auth.data.firebase

import kotlinx.serialization.Serializable

/**
 * The PUBLIC `accounts/{uid}` document — readable by any authenticated user so
 * crew-mates can render names + avatars (see `firestore.rules`). It deliberately
 * carries no PII: `email` is owned by Firebase Auth and must never live on a
 * world-readable doc. If email display is ever reintroduced it belongs under the
 * owner-only `accounts/{uid}/private/{doc}` subcollection.
 */
@Serializable
data class AccountDto(
    val id: String? = null,
    val handle: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val createdAtEpochMs: Long? = null,
    // Reserved data-consent fields (spec §13); default 0 / null = "no consent recorded".
    val dataConsentVersion: Int = 0,
    val dataConsentGrantedAtEpochMs: Long? = null,
)
