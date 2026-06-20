package es.schsebastian.foodrats.feature.meal.data.sync

import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.meal.FeedSyncStatusPort
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.meal.data.firebase.MealFirestore
import es.schsebastian.foodrats.feature.meal.data.local.MealLocalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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
) : FeedSyncStatusPort {
    // One running collector per crew, so re-driving the same crew (e.g. the active-crew flow
    // re-emits the same id) never spawns a duplicate listener. Touched ONLY from appScope: the
    // active-crew driver + syncCrew both run there, and refresh() marshals its cancel→restart onto
    // appScope before touching the map, so no lock is needed.
    private val jobs = mutableMapOf<CrewId, Job>()

    /**
     * Per-crew "freshness" stamp (P4-T2): the wall-clock [Instant] of the LAST window write for each
     * crew, surfaced to the feed via [lastSyncedAt] so it can render "synced X ago" and offer a
     * pull-to-refresh. App-lifetime, in-memory (not durable across process death) — a `StateFlow`
     * so the feed re-renders the relative label live as fresh snapshots fold in. Keyed by the raw
     * crew id string so the map is a plain value type. Written only from the appScope's single
     * dispatcher (the snapshot collector), so no lock is needed.
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
     */
    fun syncCrew(crewId: CrewId) {
        if (jobs[crewId]?.isActive == true) return
        val today = MealDay.today(clock, zone)
        val from = MealDay(today.date.minus(DatePeriod(days = STATS_WINDOW_DAYS - 1)), zone)
        val fromKey = from.toKey()
        val toKey = today.toKey()
        jobs[crewId] = appScope.launch {
            firestore.observeForRange(crewId, from, today)
                .onEach { dtos ->
                    local.replaceCrewWindow(crewId.value, fromKey, toKey, dtos)
                    // Stamp freshness AFTER the window write commits — the feed's "synced X ago"
                    // must only advance once the local store actually reflects this snapshot.
                    lastSyncedAt.value = lastSyncedAt.value + (crewId.value to clock.now())
                }
                // PERMISSION_DENIED-on-signout (or any upstream throw) STOPS this crew's sync
                // without wiping its rows — the cached window must survive sign-out. The job
                // completes here; a fresh syncCrew on the next active-crew emission re-listens.
                .catch { t ->
                    FrLog.w("MealSync", t) { "crew ${crewId.value} sync stopped: ${t.message}" }
                }
                .collect()
        }
    }

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
     * Called from the feed's ViewModel (the Main thread), NOT from appScope — so it must NOT touch
     * [jobs] inline. It marshals the cancel→restart onto [appScope] (the only place [jobs] is ever
     * mutated, matching [syncCrew]/[start]) and `.join()`s so the caller can await completion. This
     * keeps the no-lock invariant intact: every read/write of [jobs] still happens on appScope.
     */
    override suspend fun refresh(crewId: CrewId) {
        appScope.launch {
            jobs.remove(crewId)?.cancel()
            syncCrew(crewId)
        }.join()
    }

    /**
     * Drives [syncCrew] off [ActiveCrewProvider.current] for the lifetime of [appScope]: each new
     * active crew gets a sync job (a null selection — signed out / no crew — is skipped). Started
     * once at app boot via the eager Koin `single`.
     */
    fun start() {
        activeCrew.current
            .onEach { it?.let(::syncCrew) }
            .launchIn(appScope)
    }
}
