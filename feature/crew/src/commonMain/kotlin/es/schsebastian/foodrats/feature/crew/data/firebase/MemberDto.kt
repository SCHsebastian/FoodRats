package es.schsebastian.foodrats.feature.crew.data.firebase

import kotlinx.serialization.Serializable

/**
 * Crew-doc membership entry. Identity (name + avatar) is no longer modelled here — it
 * lives on `accounts/{uid}` and is resolved live via `AccountReadPort`.
 *
 * [displayName] and [avatarUrl] are retained as nullable legacy fields so older crew docs
 * still deserialize cleanly. Domain consumers ignore them; only `createCrew`/`joinByCode`
 * lifecycle writes still populate them so existing readers that haven't been migrated yet
 * (e.g. the meal-feed enrichment in `:feature:meal`) keep working until they move to live
 * reads. A future Firestore backfill can clear the columns.
 */
@Serializable
data class MemberDto(
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val joinedAtEpochMs: Long? = null,
)
