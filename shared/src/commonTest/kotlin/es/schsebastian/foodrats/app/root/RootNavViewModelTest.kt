package es.schsebastian.foodrats.app.root

import app.cash.turbine.test
import es.schsebastian.foodrats.app.navigation.DeepLinkBus
import es.schsebastian.foodrats.app.navigation.Route
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsConfig
import es.schsebastian.foodrats.core.domain.analytics.ConsentDecision
import es.schsebastian.foodrats.core.domain.analytics.ConsentPort
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferenceError
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferencePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RootNavViewModelTest {

    private val sessionFlow = MutableStateFlow<Session?>(null)
    private val crewFlow = MutableStateFlow<CrewId?>(null)
    private val promptedFlow = MutableStateFlow(false)
    private val bus = DeepLinkBus()

    private val session = object : SessionProvider {
        override val current = sessionFlow
        override suspend fun requireCurrent(): Result<Session, SessionError> =
            sessionFlow.value?.let { Result.success(it) } ?: Result.failure(SessionError.NotSignedIn)
    }
    private val activeCrew = object : ActiveCrewProvider {
        override val current = crewFlow
        override suspend fun set(crewId: CrewId) { crewFlow.value = crewId }
        override suspend fun clear() { crewFlow.value = null }
    }
    private val notifications = object : NotificationsPreferencePort {
        override val enabled = MutableStateFlow(false)
        override suspend fun set(enabled: Boolean): Result<Unit, NotificationsPreferenceError> = Result.success(Unit)
        override val prompted = promptedFlow
        override suspend fun markPrompted(): Result<Unit, NotificationsPreferenceError> = Result.success(Unit)
    }
    // Default to a current-version grant so the consent gate is satisfied for tests that don't
    // exercise it; the consent-gate test drives this flow explicitly.
    private val consentFlow = MutableStateFlow<ConsentDecision>(
        ConsentDecision.Granted(AnalyticsConfig.CURRENT_CONSENT_VERSION, Instant.fromEpochSeconds(0)),
    )
    private val consent = object : ConsentPort {
        override val decision = consentFlow
        override suspend fun grant() {
            consentFlow.value = ConsentDecision.Granted(AnalyticsConfig.CURRENT_CONSENT_VERSION, Instant.fromEpochSeconds(0))
        }
        override suspend fun deny() {
            consentFlow.value = ConsentDecision.Denied(AnalyticsConfig.CURRENT_CONSENT_VERSION, Instant.fromEpochSeconds(0))
        }
        override suspend fun revoke() = deny()
    }

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun buildVm() = RootNavViewModel(session, activeCrew, notifications, consent, bus)

    /** Bring all gates to satisfied so the stage resolves to Ready. */
    private fun makeReady() {
        sessionFlow.value = Session(accountId("u1"), crewId("c1"))
        promptedFlow.value = true
        crewFlow.value = crewId("c1")
    }

    @Test
    fun emits_sign_in_when_signed_out() = runTest {
        val vm = buildVm()
        vm.effects.test {
            assertEquals(RootNavEffect.NavigateTopLevel(Route.SignIn), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun protected_deep_link_while_signed_out_is_stashed_then_resumed_when_ready() = runTest {
        val vm = buildVm()
        vm.effects.test {
            assertEquals(RootNavEffect.NavigateTopLevel(Route.SignIn), awaitItem())

            // Arrives before auth — must be stashed, never dropped, never navigated yet.
            bus.publish("https://foodrats-de4ec.web.app/meal/m1/2026-05-26")
            makeReady()

            // Drain the intermediate stage transitions; the resume is a NavigateDeepLink.
            var eff = awaitItem()
            while (eff is RootNavEffect.NavigateTopLevel) eff = awaitItem()
            assertEquals(RootNavEffect.NavigateDeepLink(Route.MealDetail("m1", "2026-05-26")), eff)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deep_link_while_ready_navigates_immediately() = runTest {
        makeReady()
        val vm = buildVm()
        vm.effects.test {
            assertEquals(RootNavEffect.NavigateTopLevel(Route.Main), awaitItem())

            bus.publish("foodrats://app/crew/c-42")
            assertEquals(RootNavEffect.NavigateDeepLink(Route.CrewSettings("c-42")), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun resolving_session_holds_on_splash_then_goes_to_main_without_sign_in_flash() = runTest {
        // Models the SessionProvider contract: while auth is restoring, `current` emits NOTHING
        // (no placeholder null). This is the regression guard for the login-flash bug — a signed-in
        // user must go Splash → Main, never Splash → SignIn → Main.
        val sessionEmissions = MutableSharedFlow<Session?>(replay = 1)
        val resolvingSession = object : SessionProvider {
            override val current = sessionEmissions
            override suspend fun requireCurrent(): Result<Session, SessionError> =
                sessionEmissions.replayCache.firstOrNull()?.let { Result.success(it) }
                    ?: Result.failure(SessionError.NotSignedIn)
        }
        crewFlow.value = crewId("c1")
        promptedFlow.value = true

        val vm = RootNavViewModel(resolvingSession, activeCrew, notifications, consent, bus)
        vm.effects.test {
            // Auth not resolved yet → no navigation at all (app stays on Splash).
            expectNoEvents()

            // Firebase reports the persisted user → straight to Main, no SignIn detour.
            sessionEmissions.emit(Session(accountId("u1"), crewId("c1")))
            assertEquals(RootNavEffect.NavigateTopLevel(Route.Main), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unrecognised_deep_link_is_ignored() = runTest {
        makeReady()
        val vm = buildVm()
        vm.effects.test {
            assertEquals(RootNavEffect.NavigateTopLevel(Route.Main), awaitItem())

            bus.publish("https://foodrats-de4ec.web.app/unknown/thing")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun shows_consent_when_decision_needed_then_proceeds_to_main_after_a_decision() = runTest {
        // All onboarding gates satisfied EXCEPT consent: an undecided (Unknown) decision must route to
        // the consent screen and hold there — never landing on Main until the user decides.
        consentFlow.value = ConsentDecision.Unknown
        sessionFlow.value = Session(accountId("u1"), crewId("c1"))
        promptedFlow.value = true
        crewFlow.value = crewId("c1")

        val vm = buildVm()
        vm.effects.test {
            assertEquals(RootNavEffect.NavigateTopLevel(Route.Consent), awaitItem())
            expectNoEvents() // held on Consent — no Main while undecided

            // User decides (grant or deny is equivalent for the gate; both settle needsDecision=false).
            consent.deny()
            assertEquals(RootNavEffect.NavigateTopLevel(Route.Main), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun settled_current_version_denial_does_not_re_show_consent() = runTest {
        // An explicit decline at the current consent version is a SETTLED decision: needsDecision is
        // false, so the gate must skip the consent screen and go straight to Main — no re-prompt.
        consentFlow.value = ConsentDecision.Denied(AnalyticsConfig.CURRENT_CONSENT_VERSION, Instant.fromEpochSeconds(0))
        sessionFlow.value = Session(accountId("u1"), crewId("c1"))
        promptedFlow.value = true
        crewFlow.value = crewId("c1")

        val vm = buildVm()
        vm.effects.test {
            assertEquals(RootNavEffect.NavigateTopLevel(Route.Main), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun accountId(v: String) = (AccountId.of(v) as Result.Ok).value
    private fun crewId(v: String) = (CrewId.of(v) as Result.Ok).value
}
