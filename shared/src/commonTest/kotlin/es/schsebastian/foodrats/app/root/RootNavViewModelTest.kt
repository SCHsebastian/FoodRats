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
import es.schsebastian.foodrats.core.domain.preferences.CURRENT_EULA_VERSION
import es.schsebastian.foodrats.core.domain.preferences.EulaError
import es.schsebastian.foodrats.core.domain.preferences.EulaPort
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

    // Default to CURRENT_EULA_VERSION (pre-accepted) so the EULA gate is satisfied for tests that
    // don't exercise it; the EULA-gate test drives this flow explicitly with a stale version.
    private val eulaVersionFlow = MutableStateFlow<Int?>(CURRENT_EULA_VERSION)
    private val eula = object : EulaPort {
        override val acceptedVersion = eulaVersionFlow
        override suspend fun accept(version: Int): Result<Unit, EulaError> {
            eulaVersionFlow.value = version
            return Result.success(Unit)
        }
    }

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun buildVm() = RootNavViewModel(session, activeCrew, notifications, consent, bus, eula)

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
    fun invite_deep_link_while_needing_a_crew_navigates_over_the_picker() = runTest {
        // The reported bug's preconditions: signed in + prompted, but no active crew yet → NeedsCrew.
        // An invite is itself the crew-acquisition action, so it must navigate immediately (over the
        // picker as its base) instead of being stashed until a Ready state it can't reach yet.
        sessionFlow.value = Session(accountId("u1"), null)
        promptedFlow.value = true
        crewFlow.value = null

        val vm = buildVm()
        vm.effects.test {
            assertEquals(RootNavEffect.NavigateTopLevel(Route.CrewPicker), awaitItem())

            bus.publish("https://foodrats-de4ec.web.app/invite/AB2K9P")
            assertEquals(
                RootNavEffect.NavigateDeepLink(Route.InvitePreview("AB2K9P"), base = Route.CrewPicker),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun invite_stashed_pre_auth_resumes_at_needs_crew_not_waiting_for_ready() = runTest {
        // Crewless preconditions are met EXCEPT auth — prompted upfront so signing in lands straight
        // on NeedsCrew. A pre-auth invite tap is stashed, then must resume the moment we reach
        // NeedsCrew (it would otherwise be trapped: Ready needs a crew the invite itself provides).
        promptedFlow.value = true

        val vm = buildVm()
        vm.effects.test {
            assertEquals(RootNavEffect.NavigateTopLevel(Route.SignIn), awaitItem())

            bus.publish("https://foodrats-de4ec.web.app/invite/AB2K9P")
            sessionFlow.value = Session(accountId("u1"), null) // signs in; still no crew → NeedsCrew

            var eff = awaitItem()
            while (eff is RootNavEffect.NavigateTopLevel) eff = awaitItem()
            assertEquals(
                RootNavEffect.NavigateDeepLink(Route.InvitePreview("AB2K9P"), base = Route.CrewPicker),
                eff,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun non_invite_deep_link_while_needing_a_crew_stays_stashed_until_ready() = runTest {
        // Guard the other side: a meal link is meaningless until the user has a crew, so it must NOT
        // ride the NeedsCrew shortcut — it stays stashed and resumes (base Main, the default) at Ready.
        sessionFlow.value = Session(accountId("u1"), null)
        promptedFlow.value = true
        crewFlow.value = null

        val vm = buildVm()
        vm.effects.test {
            assertEquals(RootNavEffect.NavigateTopLevel(Route.CrewPicker), awaitItem())

            bus.publish("https://foodrats-de4ec.web.app/meal/m1/2026-05-26")
            expectNoEvents() // crewless: held, not navigated

            crewFlow.value = crewId("c1") // acquires a crew → Ready
            var eff = awaitItem()
            while (eff is RootNavEffect.NavigateTopLevel) eff = awaitItem()
            assertEquals(RootNavEffect.NavigateDeepLink(Route.MealDetail("m1", "2026-05-26")), eff)
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

        val vm = RootNavViewModel(resolvingSession, activeCrew, notifications, consent, bus, eula)
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

    @Test
    fun stale_eula_version_routes_to_gate_accepting_proceeds_to_main() = runTest {
        // All onboarding gates satisfied EXCEPT the EULA: accepted version is stale (0 < 1).
        // The stage machine must route to EulaGate and hold there until accept() is called.
        eulaVersionFlow.value = 0  // below CURRENT_EULA_VERSION — triggers NeedsEulaGate
        sessionFlow.value = Session(accountId("u1"), crewId("c1"))
        promptedFlow.value = true
        crewFlow.value = crewId("c1")

        val vm = buildVm()
        vm.effects.test {
            assertEquals(RootNavEffect.NavigateTopLevel(Route.EulaGate), awaitItem())
            expectNoEvents() // held on EulaGate — no Main while stale (b: no Main-flash before gate)

            // User taps "Accept & Continue" → EulaPort.accept() writes CURRENT_EULA_VERSION.
            eula.accept(CURRENT_EULA_VERSION)
            assertEquals(RootNavEffect.NavigateTopLevel(Route.Main), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun current_version_eula_goes_straight_to_main_without_gate() = runTest {
        // All gates satisfied AND EULA is at current version (the default in buildVm()).
        // A user who already accepted CURRENT_EULA_VERSION must NEVER see EulaGate.
        // This is the regression guard for the C1 fix: if viewModelOf short-circuits EulaPort
        // resolution, NoopEulaAcceptance emits CURRENT_EULA_VERSION anyway — but this test
        // combined with RootNavModuleVerifyTest proves the real EulaPort is wired.
        makeReady() // eulaVersionFlow defaults to CURRENT_EULA_VERSION

        val vm = buildVm()
        vm.effects.test {
            val eff = awaitItem()
            // Must land on Main, never route through EulaGate.
            assertEquals(RootNavEffect.NavigateTopLevel(Route.Main), eff)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun null_eula_version_routes_to_gate_then_main_after_accept() = runTest {
        // acceptedVersion = null means the user has NEVER accepted (fresh install).
        // Must behave identically to a stale version.
        eulaVersionFlow.value = null
        sessionFlow.value = Session(accountId("u1"), crewId("c1"))
        promptedFlow.value = true
        crewFlow.value = crewId("c1")

        val vm = buildVm()
        vm.effects.test {
            assertEquals(RootNavEffect.NavigateTopLevel(Route.EulaGate), awaitItem())
            expectNoEvents()

            eula.accept(CURRENT_EULA_VERSION)
            assertEquals(RootNavEffect.NavigateTopLevel(Route.Main), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun accountId(v: String) = (AccountId.of(v) as Result.Ok).value
    private fun crewId(v: String) = (CrewId.of(v) as Result.Ok).value
}
