package es.schsebastian.foodrats.core.data.image

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.functions
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.image.ImageUrlError
import es.schsebastian.foodrats.core.domain.image.ImageUrlPort
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.core.domain.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * [ImageUrlPort] over the `mintPlateUrls` callable Cloud Function (region `europe-west3`).
 *
 * Caches each resolved URL until [SAFETY_MS] before its server TTL, so a feed scroll that
 * re-resolves the same plates/avatars hits the cache instead of the network. Crew sizes are
 * tiny (≤ 8) so a process-wide map is plenty; no eviction beyond natural expiry overwrite.
 *
 * Best-effort by contract: on a backend failure it returns any still-fresh cached subset
 * rather than failing the whole call, so one flaky request can't blank an entire screen.
 */
class FirebaseImageUrlResolver(
    private val dispatchers: DispatcherProvider,
    private val clock: Clock,
    private val region: String = "europe-west3",
) : ImageUrlPort {

    private val functions by lazy { Firebase.functions(region) }
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, Cached>()

    private data class Cached(val url: String, val expiresAtMs: Long)

    override suspend fun resolve(
        crewId: CrewId,
        paths: List<String>,
    ): Result<Map<String, String>, ImageUrlError> = withContext(dispatchers.io) {
        mintCached(crewIdValue = crewId.value, paths = paths)
    }

    override suspend fun resolveOwnAvatar(path: String): Result<String?, ImageUrlError> =
        withContext(dispatchers.io) {
            // Empty crewId ⇒ the server authorizes ONLY the caller's own avatar paths (no crew check).
            when (val r = mintCached(crewIdValue = "", paths = listOf(path))) {
                is Result.Ok -> Result.success(r.value[path])
                is Result.Err -> Result.failure(r.error)
            }
        }

    /**
     * Cache-aware mint shared by [resolve] (crew-scoped) and [resolveOwnAvatar] (own-uid, empty
     * [crewIdValue]). Serves still-fresh cached URLs without a network call and degrades gracefully
     * on backend failure (returns whatever subset is still fresh).
     */
    private suspend fun mintCached(
        crewIdValue: String,
        paths: List<String>,
    ): Result<Map<String, String>, ImageUrlError> {
        if (paths.isEmpty()) return Result.success(emptyMap())
        val nowMs = clock.now().toEpochMilliseconds()
        val distinct = paths.distinct()

        val fresh = mutableMapOf<String, String>()
        val misses = mutableListOf<String>()
        mutex.withLock {
            for (p in distinct) {
                val c = cache[p]
                if (c != null && c.expiresAtMs - SAFETY_MS > nowMs) fresh[p] = c.url else misses += p
            }
        }
        if (misses.isEmpty()) return Result.success(fresh)

        return runCatching {
            functions.httpsCallable(CALLABLE)
                .invoke(MintRequest(crewId = crewIdValue, paths = misses))
                .data<MintResponse>()
        }.fold(
            onSuccess = { response ->
                mutex.withLock {
                    response.urls.forEach { (p, url) -> cache[p] = Cached(url, response.expiresAtMs) }
                }
                Result.success(fresh + response.urls)
            },
            onFailure = { t ->
                FrLog.w("ImageUrl", t) { "mintPlateUrls failed: ${t.message}" }
                // Degrade gracefully: serve whatever is still fresh; only hard-fail when we
                // have nothing at all to show.
                if (fresh.isNotEmpty()) Result.success(fresh) else Result.failure(t.toImageUrlError())
            },
        )
    }

    private fun Throwable.toImageUrlError(): ImageUrlError {
        val msg = message?.lowercase() ?: ""
        return when {
            "permission" in msg || "permission_denied" in msg -> ImageUrlError.PermissionDenied
            "unauthenticated" in msg || "sign-in" in msg -> ImageUrlError.NotSignedIn
            else -> ImageUrlError.Unavailable
        }
    }

    private companion object {
        const val CALLABLE = "mintPlateUrls"
        // Refresh a minute before the server's 15-min TTL so an in-flight image load never
        // races a URL that expires mid-request.
        const val SAFETY_MS = 60_000L
    }
}

@Serializable
private data class MintRequest(val crewId: String, val paths: List<String>)

@Serializable
private data class MintResponse(val expiresAtMs: Long, val urls: Map<String, String> = emptyMap())
