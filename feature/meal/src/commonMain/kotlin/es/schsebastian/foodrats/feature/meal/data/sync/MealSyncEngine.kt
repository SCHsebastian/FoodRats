package es.schsebastian.foodrats.feature.meal.data.sync

import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.meal.data.firebase.MealFirestore
import es.schsebastian.foodrats.feature.meal.data.local.MealLocalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

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
) {
    // One running collector per crew, so re-driving the same crew (e.g. the active-crew flow
    // re-emits the same id) never spawns a duplicate listener. Touched only from the appScope's
    // single dispatcher (the active-crew driver + syncCrew both run there), so no lock is needed.
    private val jobs = mutableMapOf<CrewId, Job>()

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
                .onEach { dtos -> local.replaceCrewWindow(crewId.value, fromKey, toKey, dtos) }
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
