package es.schsebastian.foodrats.core.data.image

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.time.FixedClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Locks the cost-critical caching contract of [FirebaseImageUrlResolver]:
 *  - a resolved URL is served from cache (no re-mint) on a repeat request (L1),
 *  - immutable paths (plates, content-versioned avatars) are persisted so a cold start reuses them
 *    WITHOUT re-minting (L2) and WITHOUT rotating the URL (so Coil doesn't re-download),
 *  - the fixed-path crew banner (and legacy avatars) are NEVER persisted and re-mint after a short
 *    client TTL, so an in-place overwrite surfaces fast.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseImageUrlResolverTest {

    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val v = transform(state.value); state.value = v; return v
        }
    }

    private val dispatchers = object : DispatcherProvider {
        private val d: CoroutineDispatcher = UnconfinedTestDispatcher()
        override val main = d
        override val io = d
        override val default = d
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val crew = CrewId.of("c1").getOrNull()!!
    private val plate = "crews/c1/meals/c1_alice_2026-06-14_dinner.jpg"
    private val versionedAvatar = "avatars/alice/9f3c1a2b.jpg"
    private val legacyAvatar = "avatars/bob.jpg"
    private val banner = "crew_banners/c1/banner.jpg"

    private val sixDaysMs = 6L * 24 * 60 * 60 * 1000

    /** A mint fake that versions every URL by call count, so a cache hit (same URL) is distinguishable. */
    private class RecordingMint(private val nowMs: () -> Long, private val ttlMs: Long) {
        var calls = 0
            private set
        val mint: MintUrls = { _, paths ->
            calls += 1
            val tag = calls
            MintResponse(
                expiresAtMs = nowMs() + ttlMs,
                urls = paths.associateWith { "https://signed.example/$it?v=$tag" },
            )
        }
    }

    private fun clockAt(epochMs: Long) = FixedClock(Instant.fromEpochMilliseconds(epochMs))

    @Test
    fun repeat_request_is_served_from_L1_without_re_minting() = runTest {
        val clock = clockAt(1_000_000_000_000)
        val rec = RecordingMint({ clock.now().toEpochMilliseconds() }, sixDaysMs)
        val resolver = FirebaseImageUrlResolver(dispatchers, clock, AppPreferences(FakeDataStore()), json, rec.mint)

        val first = resolver.resolve(crew, listOf(plate)).getOrNull()
        val second = resolver.resolve(crew, listOf(plate)).getOrNull()

        assertEquals(1, rec.calls, "second resolve must hit cache, not re-mint")
        assertEquals(first?.get(plate), second?.get(plate), "same (un-rotated) URL on the hit")
    }

    @Test
    fun immutable_paths_persist_but_the_fixed_path_banner_does_not() = runTest {
        val clock = clockAt(1_000_000_000_000)
        val rec = RecordingMint({ clock.now().toEpochMilliseconds() }, sixDaysMs)
        val prefs = AppPreferences(FakeDataStore())
        val resolver = FirebaseImageUrlResolver(dispatchers, clock, prefs, json, rec.mint)

        resolver.resolve(crew, listOf(plate, versionedAvatar, legacyAvatar, banner)).getOrNull()

        val persisted = prefs.observe(Keys.PlateUrlCacheJson).first() ?: ""
        assertTrue(plate in persisted, "per-meal plate persisted")
        assertTrue(versionedAvatar in persisted, "content-versioned avatar persisted")
        assertFalse(banner in persisted, "fixed-path banner must NOT be persisted")
        assertFalse(legacyAvatar in persisted, "legacy unversioned avatar must NOT be persisted")
    }

    @Test
    fun cold_start_reuses_persisted_immutable_url_without_re_minting() = runTest {
        val clock = clockAt(1_000_000_000_000)
        val prefs = AppPreferences(FakeDataStore())

        // First process: mint + persist the plate.
        val rec1 = RecordingMint({ clock.now().toEpochMilliseconds() }, sixDaysMs)
        val warm = FirebaseImageUrlResolver(dispatchers, clock, prefs, json, rec1.mint)
        val original = warm.resolve(crew, listOf(plate)).getOrNull()?.get(plate)

        // Second process: fresh in-memory cache, SAME prefs → must serve from L2, no mint.
        val rec2 = RecordingMint({ clock.now().toEpochMilliseconds() }, sixDaysMs)
        val cold = FirebaseImageUrlResolver(dispatchers, clock, prefs, json, rec2.mint)
        val afterRestart = cold.resolve(crew, listOf(plate)).getOrNull()?.get(plate)

        assertEquals(0, rec2.calls, "cold start must reuse the persisted URL, not re-mint")
        assertEquals(original, afterRestart, "same URL after restart → Coil disk-cache hit, no re-download")
    }

    @Test
    fun cold_start_does_not_reuse_a_banner_url_it_never_persisted() = runTest {
        val clock = clockAt(1_000_000_000_000)
        val prefs = AppPreferences(FakeDataStore())

        val rec1 = RecordingMint({ clock.now().toEpochMilliseconds() }, sixDaysMs)
        FirebaseImageUrlResolver(dispatchers, clock, prefs, json, rec1.mint)
            .resolve(crew, listOf(banner)).getOrNull()

        val rec2 = RecordingMint({ clock.now().toEpochMilliseconds() }, sixDaysMs)
        FirebaseImageUrlResolver(dispatchers, clock, prefs, json, rec2.mint)
            .resolve(crew, listOf(banner)).getOrNull()

        assertEquals(1, rec2.calls, "banner was not persisted → cold start must re-mint it")
    }

    @Test
    fun banner_re_mints_after_the_short_mutable_ttl() = runTest {
        val clock = clockAt(1_000_000_000_000)
        val rec = RecordingMint({ clock.now().toEpochMilliseconds() }, sixDaysMs)
        val resolver = FirebaseImageUrlResolver(dispatchers, clock, AppPreferences(FakeDataStore()), json, rec.mint)

        resolver.resolve(crew, listOf(banner)).getOrNull()
        resolver.resolve(crew, listOf(banner)).getOrNull() // within TTL → cache hit
        assertEquals(1, rec.calls, "within the mutable TTL the banner is cached")

        clock.advanceBy(DateTimeUnit.MINUTE, 11) // past MUTABLE_CLIENT_TTL_MS (10 min)
        resolver.resolve(crew, listOf(banner)).getOrNull()
        assertEquals(2, rec.calls, "after the short TTL a banner change can surface → re-mint")
    }
}
