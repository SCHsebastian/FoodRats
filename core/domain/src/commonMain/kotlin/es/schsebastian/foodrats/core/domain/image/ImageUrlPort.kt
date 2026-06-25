package es.schsebastian.foodrats.core.domain.image

import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result

/**
 * Resolves Storage object PATHS (e.g. `crews/{crewId}/meals/{mealId}.jpg`,
 * `avatars/{uid}.jpg`) to short-lived, viewable URLs.
 *
 * Storage objects are no longer world-readable via download tokens — reads are denied by
 * the Storage rules — so the only legitimate way to view one is a membership-checked V4
 * signed URL minted server-side. Implementations call the `mintPlateUrls` callable for
 * [crewId] and cache the returned URLs until shortly before they expire.
 *
 * Resolution lives in the data layer (the meal-feed enrichment + the `AccountReadPort`
 * implementation), never in a ViewModel — keeping the one I/O boundary in the adapter.
 */
interface ImageUrlPort {
    /**
     * Returns a map of input path → signed URL for the subset of [paths] the caller may
     * read in [crewId]. Unauthorized or unknown paths are simply absent from the map.
     * An empty [paths] resolves to an empty map without a network call.
     */
    suspend fun resolve(crewId: CrewId, paths: List<String>): Result<Map<String, String>, ImageUrlError>

    /**
     * Resolves the signed-in caller's OWN avatar [path] (`avatars/{ownUid}/{token}.jpg`) WITHOUT an
     * active crew. The server authorizes own-uid avatar paths on a crew-less request, so a user with
     * no crew yet (just signed up, or left their only crew) can still see their own avatar — the
     * crew-scoped [resolve] returns nothing in that window. Returns `null` when the path can't be
     * resolved (unauthorized/unknown). Use ONLY for the caller's own avatar.
     */
    suspend fun resolveOwnAvatar(path: String): Result<String?, ImageUrlError>
}
