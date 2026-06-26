package es.schsebastian.foodrats.core.data.image

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.functions
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.image.ImageUrlError
import es.schsebastian.foodrats.core.domain.image.ImageUrlPort
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.core.domain.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/** Mints signed URLs for a batch of object paths via the backend. Seam for testing (no Firebase). */
internal typealias MintUrls = suspend (crewId: String, paths: List<String>) -> MintResponse

/**
 * [ImageUrlPort] over the `mintPlateUrls` callable Cloud Function (region `europe-west3`).
 *
 * Two-tier cache so the dominant call in the app (signed-image-URL minting) stays cheap and the
 * underlying images aren't needlessly re-downloaded:
 *  - **L1 in-memory** — a feed scroll that re-resolves the same plates/avatars hits this instead of
 *    the network.
 *  - **L2 on disk** (DataStore, [Keys.PlateUrlCacheJson]) — survives a cold start, so a freshly
 *    launched app reuses a still-valid signed URL instead of re-calling `mintPlateUrls`. Because the
 *    URL string is then STABLE across launches, Coil serves the already-downloaded image instead of
 *    re-fetching it from Storage (a rotated URL is a Coil cache-miss → a full re-download = egress).
 *
 * Only IMMUTABLE object paths are persisted to L2: per-meal plates (`crews/{c}/meals/{m}.jpg`),
 * content-versioned avatars (`avatars/{uid}/{token}.jpg`), and content-versioned crew banners
 * (`crew_banners/{c}/{token}.jpg`) — for all three the path encodes the content, so a long-lived
 * signed URL is safe. A LEGACY fixed-path crew banner (`crew_banners/{c}/banner.jpg`, from a crew
 * predating IMAGE-2) is overwritten in place, so a long-lived cached URL would show a stale banner
 * for days after a change — it is therefore never persisted and is held only briefly in L1
 * ([MUTABLE_CLIENT_TTL_MS]) so a banner change surfaces quickly. Legacy unversioned avatars
 * (`avatars/{uid}.jpg`) are treated the same way.
 *
 * Best-effort by contract: on a backend failure it returns any still-fresh cached subset rather
 * than failing the whole call, so one flaky request can't blank an entire screen. The L2 cache is
 * account-scoped and wiped on sign-out by `LocalAccountDataEraser`.
 */
class FirebaseImageUrlResolver internal constructor(
    private val dispatchers: DispatcherProvider,
    private val clock: Clock,
    private val prefs: AppPreferences,
    private val json: Json,
    private val mint: MintUrls,
) : ImageUrlPort {

    /** Production constructor: mints via the real `mintPlateUrls` callable in [region]. */
    constructor(
        dispatchers: DispatcherProvider,
        clock: Clock,
        prefs: AppPreferences,
        json: Json,
        region: String = "europe-west3",
    ) : this(dispatchers, clock, prefs, json, mint = { crewId, paths ->
        Firebase.functions(region)
            .httpsCallable(CALLABLE)
            .invoke(MintRequest(crewId = crewId, paths = paths))
            .data<MintResponse>()
    })

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, Cached>()

    /** Whether the persisted L2 cache has been folded into [cache] yet (one-shot, lazy). */
    private var diskLoaded = false

    /** An in-memory cache entry. [freshUntilMs] already folds in [SAFETY_MS] and any mutable clamp. */
    private data class Cached(val url: String, val freshUntilMs: Long)

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
     * [crewIdValue]). Serves still-fresh cached URLs (L1+L2) without a network call and degrades
     * gracefully on backend failure (returns whatever subset is still fresh).
     */
    private suspend fun mintCached(
        crewIdValue: String,
        paths: List<String>,
    ): Result<Map<String, String>, ImageUrlError> {
        if (paths.isEmpty()) return Result.success(emptyMap())
        val nowMs = clock.now().toEpochMilliseconds()
        val distinct = paths.distinct()

        ensureDiskLoaded(nowMs)

        val fresh = mutableMapOf<String, String>()
        val misses = mutableListOf<String>()
        mutex.withLock {
            for (p in distinct) {
                val c = cache[p]
                if (c != null && c.freshUntilMs > nowMs) fresh[p] = c.url else misses += p
            }
        }
        if (misses.isEmpty()) return Result.success(fresh)

        return runCatching { mint(crewIdValue, misses) }.fold(
            onSuccess = { response ->
                var persistedChanged = false
                mutex.withLock {
                    response.urls.forEach { (p, url) ->
                        cache[p] = Cached(url, freshUntilFor(p, response.expiresAtMs, nowMs))
                        if (isImmutablePath(p)) persistedChanged = true
                    }
                }
                if (persistedChanged) persistToDisk(nowMs)
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

    /**
     * How long the client may serve a freshly-minted URL for [path]. Immutable paths ride the full
     * server TTL (minus a safety margin); mutable paths (banner / legacy avatar) are clamped to a
     * short window so an in-place overwrite surfaces quickly.
     */
    private fun freshUntilFor(path: String, serverExpiresAtMs: Long, nowMs: Long): Long {
        val serverFresh = serverExpiresAtMs - SAFETY_MS
        return if (isImmutablePath(path)) serverFresh
        else minOf(serverFresh, nowMs + MUTABLE_CLIENT_TTL_MS)
    }

    /** Folds the persisted L2 cache into [cache] exactly once, dropping expired/mutable entries. */
    private suspend fun ensureDiskLoaded(nowMs: Long) {
        if (diskLoaded) return
        val raw = runCatching { prefs.observe(Keys.PlateUrlCacheJson).first() }.getOrNull()
        val loaded: Map<String, PersistedUrl> = raw
            ?.let { runCatching { json.decodeFromString<Map<String, PersistedUrl>>(it) }.getOrNull() }
            ?: emptyMap()
        mutex.withLock {
            if (diskLoaded) return
            for ((p, e) in loaded) {
                // Defensive: only fresh, immutable paths are trusted (a hand-edited/old blob can't
                // smuggle a mutable path past the freshness rule). Don't clobber a fresher L1 entry.
                if (e.freshUntilMs > nowMs && isImmutablePath(p) && cache[p] == null) {
                    cache[p] = Cached(e.url, e.freshUntilMs)
                }
            }
            diskLoaded = true
        }
    }

    /** Writes the immutable, still-fresh subset of [cache] back to L2 (capped at [MAX_PERSISTED]). */
    private suspend fun persistToDisk(nowMs: Long) {
        val snapshot: Map<String, PersistedUrl> = mutex.withLock {
            cache.entries
                .filter { (p, c) -> isImmutablePath(p) && c.freshUntilMs > nowMs }
                .sortedByDescending { it.value.freshUntilMs }
                .take(MAX_PERSISTED)
                .associate { (p, c) -> p to PersistedUrl(c.url, c.freshUntilMs) }
        }
        runCatching {
            prefs.set(Keys.PlateUrlCacheJson, json.encodeToString(serializer<Map<String, PersistedUrl>>(), snapshot))
        }
            .onFailure { FrLog.w("ImageUrl", it) { "persist plate-url cache failed: ${it.message}" } }
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

        /** Refresh shortly before the server TTL so an in-flight image load never races an expiry. */
        const val SAFETY_MS = 60_000L

        /**
         * Client-side cache lifetime for MUTABLE, fixed-path objects (legacy fixed-path crew banner,
         * legacy unversioned avatar). Short on purpose: an in-place overwrite must surface within
         * ~this window rather than riding the multi-day server TTL. ~Matches the old uniform behaviour
         * for these paths.
         */
        const val MUTABLE_CLIENT_TTL_MS = 10 * 60_000L

        /** Cap on persisted entries — bounds the DataStore blob; oldest (soonest-expiring) drop first. */
        const val MAX_PERSISTED = 256

        /** Per-meal plate object: `crews/{crewId}/meals/{file}.jpg` (immutable — id encodes content). */
        private val IMMUTABLE_PLATE = Regex("""^crews/[^/]+/meals/[^/]+\.jpg$""")

        /** Content-versioned avatar: `avatars/{uid}/{token}.jpg` (immutable — token encodes content). */
        private val IMMUTABLE_AVATAR = Regex("""^avatars/[^/]+/[^/]+\.jpg$""")

        /**
         * Content-versioned crew banner: `crew_banners/{crewId}/{token}.jpg` (immutable — token
         * encodes content). The legacy fixed `crew_banners/{crewId}/banner.jpg` shares this shape but
         * is overwritten in place → MUTABLE, so [isImmutablePath] excludes that exact filename.
         */
        private val VERSIONED_BANNER = Regex("""^crew_banners/[^/]+/[^/]+\.jpg$""")

        /** True for object paths whose bytes never change, so a long-lived signed URL is safe to persist. */
        fun isImmutablePath(path: String): Boolean =
            IMMUTABLE_PLATE.matches(path) ||
                IMMUTABLE_AVATAR.matches(path) ||
                (VERSIONED_BANNER.matches(path) && !path.endsWith("/banner.jpg"))
    }
}

@Serializable
internal data class MintRequest(val crewId: String, val paths: List<String>)

@Serializable
internal data class MintResponse(val expiresAtMs: Long, val urls: Map<String, String> = emptyMap())

/** Persisted L2 entry. [freshUntilMs] is absolute epoch-ms (already includes the safety margin). */
@Serializable
internal data class PersistedUrl(val url: String, val freshUntilMs: Long)
