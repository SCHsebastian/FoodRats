package es.schsebastian.foodrats.app.root

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.app.navigation.DeepLinkBus
import es.schsebastian.foodrats.app.navigation.Route
import es.schsebastian.foodrats.app.navigation.parseDeepLink
import es.schsebastian.foodrats.app.navigation.requiresSession
import es.schsebastian.foodrats.core.domain.analytics.ConsentPort
import es.schsebastian.foodrats.core.domain.analytics.needsDecision
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferencePort
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Drives top-level navigation from a single source of truth.
 *
 * Two inputs feed it, each on its own collector:
 *  - the auth/onboarding **stage** (session × active-crew × notification-prompt), and
 *  - the **deep-link** stream from [DeepLinkBus].
 *
 * A [Mutex] serializes the "decide and emit" sections of both collectors so the stage machine and
 * a concurrently-arriving deep link can never race to clobber each other at the moment the user
 * becomes [RootStage.Ready] (cold-start-with-link). Navigation never happens directly here — the
 * VM only emits [RootNavEffect]s; `FoodRatsApp` performs them against the live NavController.
 */
class RootNavViewModel(
    private val session: SessionProvider,
    private val activeCrew: ActiveCrewProvider,
    private val notifications: NotificationsPreferencePort,
    private val consent: ConsentPort,
    private val deepLinks: DeepLinkBus,
) : MviViewModel<RootNavState, RootNavIntent, RootNavEffect>(RootNavState()) {

    private val navLock = Mutex()

    init {
        observeStage()
        observeDeepLinks()
    }

    private fun observeStage() {
        viewModelScope.launch {
            // Relies on the SessionProvider.current contract: the first emission is authoritative
            // (no placeholder null during auth restore). Until session.current emits, this combine
            // produces nothing and the app stays on Splash — so a logged-in user goes Splash → Main
            // without flashing SignIn. `sess == null` therefore means a real signed-out state.
            // The consent decision is settled-state: `needsDecision` is true for an unrecorded
            // (Unknown) decision OR a stored decision below the current consent-schema version, and
            // FALSE for a current-version Denied — so an explicit decline does NOT re-prompt. Bumping
            // AnalyticsConfig.CURRENT_CONSENT_VERSION re-arms it for free. Gated after crew so consent
            // is the last onboarding step before Main.
            val consentNeeded = consent.decision
                .map { it.needsDecision }
                .distinctUntilChanged()
            combine(
                session.current,
                activeCrew.current,
                notifications.prompted,
                consentNeeded,
            ) { sess, crewId, prompted, needsConsent ->
                when {
                    sess == null   -> RootStage.NeedsSignIn
                    !prompted      -> RootStage.NeedsNotificationPermission
                    crewId == null -> RootStage.NeedsCrew
                    needsConsent   -> RootStage.NeedsConsent
                    else           -> RootStage.Ready
                }
            }.collect { nextStage -> navLock.withLock { applyStage(nextStage) } }
        }
    }

    private suspend fun applyStage(nextStage: RootStage) {
        val current = currentState.stage
        if (current == nextStage) {
            FrLog.d(FrLog.Tags.RootNav) { "no-op (same stage=${nextStage::class.simpleName})" }
            return
        }
        FrLog.d(FrLog.Tags.RootNav) {
            "transition ${current::class.simpleName} → ${nextStage::class.simpleName}"
        }
        update { it.copy(stage = nextStage) }
        when (nextStage) {
            RootStage.Splash                      -> Unit  // never produced by the stage flow
            RootStage.NeedsSignIn                 -> emit(RootNavEffect.NavigateTopLevel(Route.SignIn))
            RootStage.NeedsNotificationPermission -> emit(RootNavEffect.NavigateTopLevel(Route.NotificationPermission))
            RootStage.NeedsCrew                   -> emit(RootNavEffect.NavigateTopLevel(Route.CrewPicker))
            RootStage.NeedsConsent                -> emit(RootNavEffect.NavigateTopLevel(Route.Consent))
            RootStage.Ready                       -> emitReady()
        }
    }

    /** On reaching Ready, resume a stashed deep link if one is pending, else land on Main. */
    private suspend fun emitReady() {
        val pending = currentState.pendingDeepLink
        if (pending != null) {
            update { it.copy(pendingDeepLink = null) }
            FrLog.d(FrLog.Tags.RootNav) { "ready: resuming deep link ${pending::class.simpleName}" }
            emit(RootNavEffect.NavigateDeepLink(pending))
        } else {
            emit(RootNavEffect.NavigateTopLevel(Route.Main))
        }
    }

    private fun observeDeepLinks() {
        viewModelScope.launch {
            deepLinks.uris.collect { uri ->
                val route = parseDeepLink(uri)
                if (route == null) {
                    FrLog.d(FrLog.Tags.RootNav) { "deep link ignored (unrecognised): $uri" }
                    return@collect
                }
                navLock.withLock {
                    val ready = currentState.stage == RootStage.Ready
                    if (!route.requiresSession() || ready) {
                        FrLog.d(FrLog.Tags.RootNav) { "deep link → navigate now: ${route::class.simpleName}" }
                        emit(RootNavEffect.NavigateDeepLink(route))
                    } else {
                        FrLog.d(FrLog.Tags.RootNav) { "deep link stashed until Ready: ${route::class.simpleName}" }
                        update { it.copy(pendingDeepLink = route) }
                    }
                }
            }
        }
    }

    override suspend fun handle(intent: RootNavIntent) = Unit
}
