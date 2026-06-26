package es.schsebastian.foodrats.feature.crew.data.firebase

import kotlinx.serialization.Serializable

@Serializable
data class CrewDto(
    val id: String? = null,
    val name: String? = null,
    val code: String? = null,           // mirrors the crewCodes/{code} doc ID, denormalized for read convenience
    val ownerId: String? = null,
    val createdAtEpochMs: Long? = null,
    val memberIds: List<String> = emptyList(),
    val members: Map<String, MemberDto> = emptyMap(),  // accountId -> MemberDto
    // Owner-settable crew policy: when true, a meal's author identity is masked from a
    // crewmate until they've cast their own score. Defaults false; old docs without the
    // field deserialize to false (pre-launch — no migration).
    val blindVoting: Boolean = false,
    // Owner-settable short tagline/house rules (≤ 120 chars). `null` means no tagline is set;
    // old crew docs without the field deserialize to null (no migration needed).
    // Null-pinned so GitLive encodeDefaults=true doesn't emit `null` and fail an affectedKeys
    // check it was not part of.
    val tagline: String? = null,
    // Owner-settable onboarding welcome message shown as a dismissible feed banner to new joiners
    // (≤ 200 chars). `null` means no message is set; old docs without the field deserialize to null
    // (no migration needed). Null-pinned for the same GitLive encodeDefaults reason as `tagline`.
    val welcomeMessage: String? = null,
    // Owner-settable weekly challenge text shown in the feed header (≤ 80 chars). Auto-expires
    // 7 days after weeklyChallengeSetAtMillis (client-side check). Both fields are always written
    // together (affectedKeys rule arm). Null-pinned to prevent GitLive encodeDefaults regression.
    val weeklyChallenge: String? = null,
    val weeklyChallengeSetAtMillis: Long? = null,
    // Owner-settable Score vocabulary (C8): "stars" | "emoji" | "numeric". Absent / unknown ⇒ "stars"
    // (pre-C8 crews). Non-null with a default so GitLive encodeDefaults=true sends the default "stars"
    // on create — this is INTENTIONAL: the create whitelist now includes `scoreStyle`, and the default
    // value "stars" is a valid allowed string, so the initial create write is always permitted.
    val scoreStyle: String = "stars",
    // Owner-settable hero/banner image Storage path (C9). `null` means no banner is set.
    // Content-versioned (`crew_banners/{crewId}/{token}.jpg`) for banners set after IMAGE-2; a legacy
    // fixed `crew_banners/{crewId}/banner.jpg` for crews predating it.
    // Null-pinned so GitLive encodeDefaults=true doesn't emit `null` and fail an affectedKeys check it
    // was not part of (same GitLive regression guard as `tagline`, `welcomeMessage`, etc.).
    val bannerPath: String? = null,
    // Content-version token of the current banner (IMAGE-2) — the same hash that names the object in
    // [bannerPath]. `null` ⇒ no banner OR a legacy fixed-path banner (pre-IMAGE-2 crew) whose path is
    // overwritten in place and must NOT be treated as immutable. Non-null ⇒ `bannerPath` is the
    // content-versioned, upload-immutable path, so the resolver may persist its signed URL and the UI
    // may key Coil on the stable path. Written together with `bannerPath` on every set/clear.
    // Null-pinned for the same GitLive encodeDefaults reason as the other optional fields above.
    val bannerToken: String? = null,
    // Owner-settable vertical focal point for the banner crop (C9), 0..1 (top..bottom). Firestore
    // stores numbers as doubles. `null` means unset ⇒ 0.5 (center) on read. Null-pinned for the same
    // GitLive encodeDefaults reason as the other optional fields above.
    val bannerFocalY: Double? = null,
)
