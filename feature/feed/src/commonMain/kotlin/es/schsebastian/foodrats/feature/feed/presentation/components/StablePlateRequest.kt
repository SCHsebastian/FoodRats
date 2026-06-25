package es.schsebastian.foodrats.feature.feed.presentation.components

import androidx.compose.runtime.Composable
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Builds a Coil [ImageRequest] that loads [url] but keys the disk + memory caches on the STABLE
 * Storage object path ([cacheKey]) instead of the rotating V4 signed [url]. Signed URLs are minted
 * per read and expire, so keying the cache on the URL makes every re-mint a cache miss — the cached
 * bytes are orphaned and the plate disappears when the device goes offline. Keying both caches on
 * the immutable path lets the same bytes serve every future (re-signed) URL for that object. (P1-T3)
 *
 * When [cacheKey] is blank (the meal has no known Storage path) the request falls back to Coil's
 * default URL-derived keying — i.e. exactly the prior behaviour.
 *
 * [crossfade] preserves the soft fade-in from the ThumbHash placeholder; the caller still supplies
 * the placeholder/error painters on the `AsyncImage` itself.
 *
 * **M4 — cache-key stability:** a published meal's plate Storage path is upload-immutable. There is
 * no replace-photo flow anywhere in the codebase (no `replacePlate`, `updatePlate`, or edit-photo
 * use case). The path is written once at publish time and never changes for that meal's lifetime, so
 * the stable-path cache key is always safe — no need to include `thumbHash` or `publishedAt` as a
 * discriminator to force re-invalidation on replace.
 */
@Composable
internal fun stablePlateRequest(url: String, cacheKey: String): ImageRequest =
    ImageRequest.Builder(LocalPlatformContext.current)
        .data(url)
        .apply {
            if (cacheKey.isNotBlank()) {
                diskCacheKey(cacheKey)
                memoryCacheKey(cacheKey)
            }
        }
        .crossfade(true)
        .build()
