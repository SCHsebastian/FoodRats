package es.schsebastian.foodrats.feature.crew.data.sync

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewDataSource
import es.schsebastian.foodrats.feature.crew.data.local.CrewLocalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The write side of the offline-first crew-list inversion (P3b §P3b-T7): the ONLY consumer of the
 * crew-list Firestore listener ([CrewDataSource.observeMyCrews]). It mirrors the signed-in member's
 * crew list into the local [CrewLocalStore], from which the
 * [FirebaseCrewRepository][es.schsebastian.foodrats.feature.crew.data.repository.FirebaseCrewRepository]
 * now reads — the crew picker never touches Firestore directly again, it observes the local DB, and
 * this engine keeps that DB fresh. Mirrors the meal feed's `MealSyncEngine`.
 *
 * Driven off [SessionProvider.current]: each new signed-in account gets a sync job; a sign-out
 * (`null` session) is skipped. For the active account it collects `observeMyCrews(accountId)` and
 * folds every snapshot into [CrewLocalStore.replaceAll] (full replace — the whole crew set is the
 * snapshot).
 *
 * It owns NO IO boundary: [CrewLocalStore] owns its single `withContext(io)` per write; this engine
 * is pure orchestration on the injected app-lifetime [CoroutineScope]
 * ([named("appScope")][org.koin.core.qualifier.named]).
 *
 * **Benign-on-signout:** when the auth token is revoked the Firestore listener throws
 * `PERMISSION_DENIED`; the per-account flow's `.catch` swallows it and STOPS the job WITHOUT wiping
 * any rows — the cached crew list must survive a sign-out so the next sign-in renders instantly.
 */
internal class CrewSyncEngine(
    private val session: SessionProvider,
    private val dataSource: CrewDataSource,
    private val local: CrewLocalStore,
    private val appScope: CoroutineScope,
) {
    // One running collector per account, so re-driving the same account (the session flow re-emits
    // the same id) never spawns a duplicate listener. Touched only from the appScope's single
    // dispatcher (the session driver + syncAccount both run there), so no lock is needed.
    private val jobs = mutableMapOf<AccountId, Job>()

    /**
     * Starts mirroring [accountId]'s crew list into the local store on [appScope]. Idempotent: an
     * account already being synced is a no-op (the live listener is the source of truth, so there is
     * nothing to re-trigger).
     */
    fun syncAccount(accountId: AccountId) {
        if (jobs[accountId]?.isActive == true) return
        jobs[accountId] = appScope.launch {
            dataSource.observeMyCrews(accountId)
                .onEach { dtos -> local.replaceAll(dtos) }
                // PERMISSION_DENIED-on-signout (or any upstream throw) STOPS this account's sync
                // WITHOUT wiping its rows — the cached crew list must survive sign-out. The job
                // completes here; a fresh syncAccount on the next session emission re-listens.
                .catch { t ->
                    FrLog.w("CrewSync", t) { "account ${accountId.value} crew sync stopped: ${t.message}" }
                }
                .collect()
        }
    }

    /**
     * Drives [syncAccount] off [SessionProvider.current] for the lifetime of [appScope]: each new
     * signed-in account gets a sync job (a `null` session — signed out — is skipped). Started once at
     * app boot via the eager Koin `single`.
     */
    fun start() {
        session.current
            .map { it?.accountId }
            .distinctUntilChanged()
            .onEach { it?.let(::syncAccount) }
            .launchIn(appScope)
    }
}
