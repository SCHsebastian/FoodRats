package es.schsebastian.foodrats.feature.auth.data.firebase

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * The PUBLIC `accounts/{uid}` document — readable by any authenticated user so
 * crew-mates can render names + avatars (see `firestore.rules`). It deliberately
 * carries no PII: `email` is owned by Firebase Auth and must never live on a
 * world-readable doc. If email display is ever reintroduced it belongs under the
 * owner-only `accounts/{uid}/private/{doc}` subcollection.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AccountDto(
    val id: String? = null,
    val handle: String? = null,
    val displayName: String? = null,
    // Storage object PATH of the avatar (content-versioned `avatars/{uid}/{token}.jpg`), NOT a URL —
    // non-null signals "has avatar" and is resolved to a signed URL at read time (AccountReadPort
    // impl). The path changes on every distinct image so the snapshot re-emits and the UI refreshes.
    val avatarPath: String? = null,
    // Personal tagline / bio (≤ 100 chars). Null-pinned default: GitLive encodeDefaults=true
    // serializes this field as `null` on every write, which is safe because the accounts/{uid}
    // write rule allows it.
    val bio: String? = null,
    val createdAtEpochMs: Long? = null,
    // Reserved data-consent fields (spec §13); default 0 / null = "no consent recorded".
    val dataConsentVersion: Int = 0,
    val dataConsentGrantedAtEpochMs: Long? = null,
    // Server-assigned badge id — written only by the Admin SDK (onMealCreated Cloud Function).
    // @EncodeDefault(NEVER) prevents this field from being included in client writes even when
    // the GitLive encoder uses encodeDefaults=true, so the client can never clobber a
    // server-assigned badge by sending null. The field IS decoded from Firestore on reads.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val badgeId: String? = null,
)
