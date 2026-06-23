package es.schsebastian.foodrats.app.root

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.app.navigation.DeepLinkBus
import es.schsebastian.foodrats.app.navigation.Route
import es.schsebastian.foodrats.app.navigation.parseDeepLink
import es.schsebastian.foodrats.core.domain.analytics.ConsentPort
import es.schsebastian.foodrats.core.domain.analytics.needsDecision
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.preferences.CURRENT_EULA_VERSION
import es.schsebastian.foodrats.core.domain.preferences.EulaPort
import es.schsebastian.foodrats.core.domain.preferences.NoopEulaAcceptance
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferencePort
import es.schsebastian.foodrats.core.domain.preferences.needsEulaAcceptance
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
    // UGC compliance §6 re-acceptance gate — defaults to noop so existing tests compile without
    // providing this port; the Koin module always passes the real EulaRepository.
    private val eulaPort: EulaPort = NoopEulaAcceptance,
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
            val eulaNeeded = eulaPort.acceptedVersion
                .map { needsEulaAcceptance(CURRENT_EULA_VERSION, it) }
                .distinctUntilChanged()
            combine(
                session.current,
                activeCrew.current,
                notifications.prompted,
                consentNeeded,
                eulaNeeded,
            ) { sess, crewId, prompted, needsConsent, needsEula ->
                when {
                    sess == null   -> RootStage.NeedsSignIn
                    !prompted      -> RootStage.NeedsNotificationPermission
                    crewId == null -> RootStage.NeedsCrew
                    needsConsent   -> RootStage.NeedsConsent
                    needsEula      -> RootStage.NeedsEulaGate
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
            RootStage.NeedsCrew                   -> emitNeedsCrew()
            RootStage.NeedsConsent                -> emit(RootNavEffect.NavigateTopLevel(Route.Consent))
            RootStage.NeedsEulaGate               -> emit(RootNavEffect.NavigateTopLevel(Route.EulaGate))
            RootStage.Ready                       -> emitReady()
        }
    }

    /**
     * On reaching NeedsCrew, route to the crew picker — unless an invite deep link is pending. An
     * invite is itself the crew-acquisition action (the AcceptInvite flow joins and switches the
     * active crew, which clears this very gate), so honor it now instead of stashing it until Ready:
     * Ready is unreachable without a crew, and the crew is exactly what the invite provides. Other
     * pending deep links (a meal/crew-settings link to a crew the user isn't in yet) stay stashed —
     * they're meaningless until the user has a crew, so they wait for Ready. The picker is the base
     * so backing out of the invite returns there, not to an empty Feed.
     */
    private suspend fun emitNeedsCrew() {
        val pending = currentState.pendingDeepLink
        if (pending is Route.InvitePreview) {
            update { it.copy(pendingDeepLink = null) }
            FrLog.d(FrLog.Tags.RootNav) { "needsCrew: resuming invite deep link" }
            emit(RootNavEffect.NavigateDeepLink(pending, base = Route.CrewPicker))
        } else {
            emit(RootNavEffect.NavigateTopLevel(Route.CrewPicker))
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
                    val stage = currentState.stage
                    // All routes returned by parseDeepLink are Route.Protected, so requiresSession()
                    // is always true here. Navigate immediately when Ready. An invite is the one
                    // deep link that's also valid at NeedsCrew — it joins + switches the active crew
                    // itself, so it must not be trapped behind a gate it would clear (see
                    // emitNeedsCrew). Everything else stashes until Ready.
                    when {
                        stage == RootStage.Ready -> {
                            FrLog.d(FrLog.Tags.RootNav) { "deep link → navigate now: ${route::class.simpleName}" }
                            emit(RootNavEffect.NavigateDeepLink(route))
                        }
                        route is Route.InvitePreview && stage == RootStage.NeedsCrew -> {
                            FrLog.d(FrLog.Tags.RootNav) { "invite deep link → navigate now over crew picker" }
                            emit(RootNavEffect.NavigateDeepLink(route, base = Route.CrewPicker))
                        }
                        else -> {
                            FrLog.d(FrLog.Tags.RootNav) { "deep link stashed until Ready: ${route::class.simpleName}" }
                            update { it.copy(pendingDeepLink = route) }
                        }
                    }
                }
            }
        }
    }

    override suspend fun handle(intent: RootNavIntent) = Unit
}
