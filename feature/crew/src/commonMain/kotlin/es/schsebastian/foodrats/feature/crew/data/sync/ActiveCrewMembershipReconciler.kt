package es.schsebastian.foodrats.feature.crew.data.sync

import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach

/**
 * Invalidates the active-crew selection when the signed-in member is REMOVED from the active crew
 * remotely (owner kick, or `deleteAccount`-driven roster rewrites). Nothing else clears the
 * selection in that case — the stale id survives restarts, so the app cold-starts into the feed of
 * a crew the user is no longer in (stale cached meals, every write PERMISSION_DENIED).
 *
 * Proof-of-removal is deliberately strict: only a PRESENT crew document whose `memberIds` excludes
 * the signed-in account triggers the clear. A `null` emission from [CrewDataSource.observeCrew]
 * conflates not-found with transient listener errors (its upstream `.catch { emit(null) }`), so it
 * is IGNORED — a network blip or a remotely-deleted crew never clears the selection here (the
 * self-leave / self-delete paths are handled deterministically in `LeaveCrewUseCase` /
 * `DeleteCrewUseCase`). This also makes the crew-creation race safe: the founder is in
 * `memberIds` from the first committed snapshot, so a just-created active crew can't be bounced.
 *
 * Mirrors [CrewSyncEngine]'s lifecycle: pure orchestration on the app-lifetime scope, no IO
 * boundary of its own, started once at boot via the eager Koin single.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ActiveCrewMembershipReconciler(
    private val session: SessionProvider,
    private val activeCrew: ActiveCrewProvider,
    private val dataSource: CrewDataSource,
    private val appScope: CoroutineScope,
) {
    fun start() {
        combine(session.current, activeCrew.current) { s, crewId -> s?.accountId to crewId }
            .distinctUntilChanged()
            .flatMapLatest { (accountId, crewId) ->
                if (accountId == null || crewId == null) {
                    emptyFlow()
                } else {
                    dataSource.observeCrew(crewId)
                        .mapNotNull { dto ->
                            crewId.takeIf { dto != null && accountId.value !in dto.memberIds }
                        }
                }
            }
            .onEach { removedFrom: CrewId ->
                FrLog.d(FrLog.Tags.ActiveCrew) {
                    "no longer a member of active crew ${removedFrom.value} — clearing selection"
                }
                activeCrew.clear()
            }
            // Defensive: a throw here must not cancel the shared appScope's other jobs.
            .catch { t -> FrLog.w("Crew", t) { "active-crew reconciler stopped: ${t.message}" } }
            .launchIn(appScope)
    }
}
