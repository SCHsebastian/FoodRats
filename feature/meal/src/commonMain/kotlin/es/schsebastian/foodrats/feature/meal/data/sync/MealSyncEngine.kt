package es.schsebastian.foodrats.feature.meal.data.sync

import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.meal.FeedSyncStatusPort
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.core.domain.telemetry.NoopCrashReporter
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.meal.data.firebase.MealFirestore
import es.schsebastian.foodrats.feature.meal.data.local.MealLocalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlin.time.Instant

/**
 * The write side of the offline-first read-path inversion (P3a §3.2): the ONLY consumer of the
 * Firestore feed listener ([MealFirestore.observeForRange]). It mirrors the server's rolling
 * 30-day window into the local [MealLocalStore], from which the
 * [FirebaseMealRepository][es.schsebastian.foodrats.feature.meal.data.repository.FirebaseMealRepository]
 * now reads (P3a-T4). The feed never touches Firestore directly again — it observes the local DB,
 * and this engine keeps that DB fresh.
 *
 * For each active crew it collects `observeForRange(crewId, today-29d, today)` and folds every
 * snapshot into [MealLocalStore.replaceCrewWindow]: present rows are upserted, rows held in that
 * window but absent from the snapshot are deleted (delete-by-absence), and rows OUTSIDE the
 * window are left untouched — older history persists for stats.
 *
 * It owns NO IO boundary: [MealLocalStore] owns its single `withContext(io)` per write; this engine
 * is pure orchestration on the injected app-lifetime [CoroutineScope]
 * ([named("appScope")][org.koin.core.qualifier.named]).
 *
 * **Benign-on-signout:** when the auth token is revoked the Firestore listener throws
 * `PERMISSION_DENIED`; the per-crew flow's `.catch` swallows it and STOPS the job WITHOUT wiping
 * any rows — the cached feed must survive a sign-out so the next sign-in renders instantly. This
 * mirrors the repository's defensive `.catch { emit(emptyList()) }`.
 */
internal class MealSyncEngine(
    private val firestore: MealFirestore,
    private val local: MealLocalStore,
    private val activeCrew: ActiveCrewProvider,
    private val clock: Clock,
    private val zone: TimeZone,
    private val appScope: CoroutineScope,
    private val timestampStore: SyncTimestampPort? = null,  // null in tests; real store in prod
    private val crashReporter: CrashReporter = NoopCrashReporter,
) : FeedSyncStatusPort {
    // One running collector per crew, so re-driving the same crew (e.g. the active-crew flow
    // re-emits the same id) never spawns a duplicate listener.
    // Guarded by [mutex] — appScope is multi-threaded (Dispatchers.Default) so all reads/writes of
    // [jobs] must be lock-protected.
    private val jobs = mutableMapOf<CrewId, Job>()

    /**
     * Serialises all reads and writes of [jobs]. [appScope] uses [SupervisorJob] +
     * [kotlinx.coroutines.Dispatchers.Default] which is multi-threaded, so plain map access is not
     * thread-safe. Both [syncCrew] and [refresh] acquire this lock before touching the map.
     */
    private val mutex = Mutex()

    /**
     * Per-crew "freshness" stamp (P4-T2 / L3): the wall-clock [Instant] of the LAST window write for
     * each crew, surfaced to the feed via [lastSyncedAt] so it can render "synced X ago" and offer a
     * pull-to-refresh. Persisted via [SyncTimestampPort] (L3) so the stamp survives process death when
     * the local feed cache is present. Hydrated in [start]; kept in sync with every window write in
     * [syncCrew]. Keyed by the raw crew id string so the map is a plain value type.
     */
    private val lastSyncedAt = MutableStateFlow<Map<String, Instant>>(emptyMap())

    private companion object {
        const val STATS_WINDOW_DAYS = 30
    }

    /**
     * Starts mirroring [crewId]'s 30-day window into the local store on [appScope]. Idempotent: a
     * crew already being synced is a no-op (the live listener is the source of truth, so there is
     * nothing to re-trigger). The window is recomputed from [clock] at start, matching the
     * repository's read window.
     *
     * **Thread safety:** the entire idempotency check + job launch is guarded by [mutex] so
     * concurrent calls from [appScope]'s multi-threaded [kotlinx.coroutines.Dispatchers.Default]
     * cannot corrupt the [jobs] map.
     *
     * **Transient error retry (H4):** non-permission-denied errors are retried with exponential
     * backoff (1s, 2s, 4s … capped at 64s). `PERMISSION_DENIED` is terminal (sign-out) and falls
     * through to [catch] without retry, preserving the cached rows.
     */
    fun syncCrew(crewId: CrewId) {
        val today = MealDay.today(clock, zone)
        val from = MealDay(today.date.minus(DatePeriod(days = STATS_WINDOW_DAYS - 1)), zone)
        val fromKey = from.toKey()
        val toKey = today.toKey()
        appScope.launch {
            mutex.withLock {
                if (jobs[crewId]?.isActive == true) return@withLock
                jobs[crewId] = appScope.launch {
                    firestore.observeForRange(crewId, from, today)
                        .onEach { dtos ->
                            // A local-store failure (e.g. schema mismatch, malformed DTO, disk-full) is
                            // NOT a transient Firestore hiccup — retryWhen below only classifies
                            // PERMISSION_DENIED as terminal, so letting this throw propagate would have
                            // it retried forever: resubscribing just re-delivers the SAME snapshot into
                            // the SAME local write, which fails identically every time. Contain it here
                            // instead: report + skip this snapshot, reserving the retry budget below for
                            // genuine Firestore-side transient errors.
                            val wrote = try {
                                local.replaceCrewWindow(crewId.value, fromKey, toKey, dtos)
                                true
                            } catch (t: Throwable) {
                                FrLog.w("MealSync", t) {
                                    "crew ${crewId.value} local window write failed, skipping snapshot: ${t.message}"
                                }
                                crashReporter.recordNonFatal(t, tag = "meal-sync-local-write")
                                false
                            }
                            if (wrote) {
                                // Stamp freshness AFTER the window write commits — the feed's "synced X
                                // ago" must only advance once the local store actually reflects this
                                // snapshot. `updateAndGet` is an atomic CAS loop: syncCrew launches one
                                // long-lived job per crew on the multi-threaded appScope (Dispatchers
                                // .Default), so ≥2 crews' snapshots landing at the same instant must not
                                // race a plain read-modify-write and silently drop one crew's stamp.
                                val now = clock.now()
                                val newMap = lastSyncedAt.updateAndGet { it + (crewId.value to now) }
                                // L3: persist the updated stamp — timestampStore owns withContext(io).
                                timestampStore?.save(newMap)
                            }
                        }
                        // H4: auto-retry on transient errors; PERMISSION_DENIED stays terminal.
                        .retryWhen { cause, attempt ->
                            if (isPermissionDenied(cause)) {
                                false  // let it fall through to .catch
                            } else {
                                val backoffMs = minOf(1_000L * (1L shl attempt.toInt().coerceAtMost(6)), 64_000L)
                                FrLog.w("MealSync") { "crew ${crewId.value} transient error (attempt $attempt), retry in ${backoffMs}ms: ${cause.message}" }
                                delay(backoffMs)
                                true
                            }
                        }
                        // PERMISSION_DENIED-on-signout STOPS this crew's sync without wiping its rows
                        // — the cached window must survive sign-out. The job completes here; a fresh
                        // syncCrew on the next active-crew emission re-listens.
                        .catch { t ->
                            FrLog.w("MealSync", t) { "crew ${crewId.value} sync stopped (permission denied): ${t.message}" }
                        }
                        .collect()
                }
            }
        }
    }

    private fun isPermissionDenied(t: Throwable): Boolean =
        t.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true

    /**
     * The wall-clock [Instant] of [crewId]'s LAST window write this session, or `null` if it has not
     * synced yet (P4-T2). A live `StateFlow`-derived flow: the feed re-resolves its "synced X ago"
     * label whenever a fresh snapshot folds in. Keyed by raw crew id.
     */
    override fun lastSyncedAt(crewId: CrewId): Flow<Instant?> =
        lastSyncedAt.map { it[crewId.value] }

    /**
     * Forces a re-pull of [crewId]'s window (P4-T2): cancels the running per-crew collector (if any)
     * and restarts it, so the Firestore listener re-subscribes and the next server snapshot is
     * re-fetched and re-stamped into [lastSyncedAt]. Idempotent beyond re-arming the listener;
     * cached rows are untouched by the cancel (the engine never wipes on stop).
     *
     * Called from the feed's ViewModel (the Main thread). Cancels the old job under [mutex] (so
     * the cancel is serialized with any concurrent [syncCrew] invocation), then calls [syncCrew]
     * which will also acquire the lock before starting the new job.
     */
    override suspend fun refresh(crewId: CrewId) {
        appScope.launch {
            mutex.withLock { jobs.remove(crewId)?.cancel() }
            syncCrew(crewId)
        }.join()
    }

    /**
     * Drives [syncCrew] off [ActiveCrewProvider.current] for the lifetime of [appScope]: each new
     * active crew gets a sync job (a null selection — signed out / no crew — is skipped). Started
     * once at app boot via the eager Koin `single`.
     *
     * Also hydrates [lastSyncedAt] from [SyncTimestampPort] (L3) so "synced X ago" survives
     * process death when the local feed cache is present. Hydration runs before subscribing to
     * [ActiveCrewProvider.current]; [timestampStore] owns the single [withContext] IO boundary.
     */
    fun start() {
        // L3: hydrate the in-memory stamp from the persisted store so "synced X ago" survives
        // process death. Runs on appScope (Dispatchers.Default) — timestampStore owns withContext(io).
        if (timestampStore != null) {
            appScope.launch {
                val persisted = timestampStore.load()
                if (persisted.isNotEmpty()) {
                    // Merge with fresh-wins (right operand wins on key collision): a window write
                    // that stamps a crew BEFORE this async DataStore load completes must NOT be
                    // clobbered back to the older persisted value. update {} is an atomic CAS loop.
                    lastSyncedAt.update { current -> persisted + current }
                }
            }
        }
        activeCrew.current
            .onEach { it?.let(::syncCrew) }
            .launchIn(appScope)
    }
}
