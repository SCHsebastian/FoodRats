package es.schsebastian.foodrats.feature.crew.presentation.settings

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AppSetting
import es.schsebastian.foodrats.core.domain.analytics.RecordingAnalyticsTracker
import es.schsebastian.foodrats.core.domain.crew.CrewScoreStyle
import es.schsebastian.foodrats.core.domain.crew.CrewWelcomePort
import es.schsebastian.foodrats.core.domain.crew.WeeklyChallengeSnapshot
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.model.CrewTagline
import es.schsebastian.foodrats.feature.crew.domain.model.Member
import es.schsebastian.foodrats.feature.crew.domain.test.FakeConnectivityPort
import es.schsebastian.foodrats.feature.crew.domain.test.FakeCrewRepository
import es.schsebastian.foodrats.feature.crew.domain.test.RecordingOutboxPort
import es.schsebastian.foodrats.feature.crew.domain.test.aid
import es.schsebastian.foodrats.feature.crew.domain.test.cid
import es.schsebastian.foodrats.feature.crew.domain.usecase.DeleteCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.LeaveCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.ObserveCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.RemoveMemberUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.RemoveCrewBannerUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.RenameCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewBannerFocalUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewBannerUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SetBlindVotingUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewScoreStyleUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewTaglineUseCase
import es.schsebastian.foodrats.core.domain.time.FixedClock
import es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewWelcomeMessageUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewWeeklyChallengeUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CrewSettingsViewModelTest {

    private val ownerId = aid("uid-owner")
    private val memberId = aid("uid-other")
    private val crewId = cid("c-1")
    private val sampleCrew = Crew.of(
        id = crewId,
        name = "My Crew",
        code = (CrewCode.of("ABCD23") as Result.Ok).value,
        ownerId = ownerId,
        createdAt = Instant.fromEpochMilliseconds(0L),
        members = listOf(
            Member(ownerId, Instant.fromEpochMilliseconds(0L)),
            Member(memberId, Instant.fromEpochMilliseconds(0L)),
        ),
    )

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun owner_confirms_remove_member_removes_member_without_error() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val vm = buildVm(ownerId, repo)
        assertEquals(true, vm.state.value.isOwner)
        assertEquals(ownerId, vm.state.value.myAccountId)

        vm.onIntent(CrewSettingsIntent.RemoveMemberConfirmed(memberId))

        assertEquals(null, vm.state.value.error)
        assertEquals(Triple(crewId, ownerId, memberId), repo.lastRemoveMember)
    }

    @Test
    fun owner_remove_member_emits_success_effect_clears_progress_and_tracks_analytics() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val analytics = RecordingAnalyticsTracker()
        val vm = buildVm(ownerId, repo, analytics)

        vm.effects.test {
            vm.onIntent(CrewSettingsIntent.RemoveMemberConfirmed(memberId))
            assertEquals(CrewSettingsEffect.MemberRemoved(displayName = null), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.removingMemberIds.isEmpty())
        assertEquals(Triple(crewId, ownerId, memberId), repo.lastRemoveMember)
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.CrewMemberRemoved(crewId)), analytics.events.toList())
    }

    @Test
    fun non_owner_remove_member_surfaces_not_owner_and_does_not_call_port_or_track() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val analytics = RecordingAnalyticsTracker()
        val vm = buildVm(memberId, repo, analytics)

        vm.onIntent(CrewSettingsIntent.RemoveMemberConfirmed(ownerId))

        assertEquals(CrewError.RemoveMember.NotOwner, vm.state.value.error)
        assertTrue(vm.state.value.removingMemberIds.isEmpty())
        assertNull(repo.lastRemoveMember)
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun owner_removing_self_surfaces_cannot_remove_self_and_does_not_track() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val analytics = RecordingAnalyticsTracker()
        val vm = buildVm(ownerId, repo, analytics)

        vm.onIntent(CrewSettingsIntent.RemoveMemberConfirmed(ownerId))

        assertEquals(CrewError.RemoveMember.CannotRemoveSelf, vm.state.value.error)
        assertTrue(vm.state.value.removingMemberIds.isEmpty())
        assertNull(repo.lastRemoveMember)
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun owner_removing_non_member_surfaces_member_not_found_and_does_not_track() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val analytics = RecordingAnalyticsTracker()
        val vm = buildVm(ownerId, repo, analytics)

        vm.onIntent(CrewSettingsIntent.RemoveMemberConfirmed(aid("uid-stranger")))

        assertEquals(CrewError.RemoveMember.MemberNotFound, vm.state.value.error)
        assertTrue(vm.state.value.removingMemberIds.isEmpty())
        assertNull(repo.lastRemoveMember)
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun non_owner_state_excludes_my_id_from_being_owner() = runTest {
        val vm = buildVm(memberId)
        assertEquals(false, vm.state.value.isOwner)
        assertEquals(memberId, vm.state.value.myAccountId)
    }

    @Test
    fun save_crew_name_success_keeps_state_clean_and_tracks_crew_renamed() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val analytics = RecordingAnalyticsTracker()
        val vm = buildVm(ownerId, repo, analytics)

        vm.onIntent(CrewSettingsIntent.CrewNameChanged("Renamed Crew"))
        vm.onIntent(CrewSettingsIntent.SaveCrewName)

        assertEquals(false, vm.state.value.isSavingCrewName)
        assertNull(vm.state.value.error)
        assertEquals(Pair(crewId, "Renamed Crew"), repo.lastRename)
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.CrewRenamed(crewId)), analytics.events.toList())
    }

    @Test
    fun save_crew_name_fails_with_authorization_not_owner_when_non_owner_and_does_not_track() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val analytics = RecordingAnalyticsTracker()
        val vm = buildVm(memberId, repo, analytics)

        vm.onIntent(CrewSettingsIntent.CrewNameChanged("Hostile Rename"))
        vm.onIntent(CrewSettingsIntent.SaveCrewName)

        assertEquals(CrewError.Authorization.NotOwner, vm.state.value.error)
        assertEquals(false, vm.state.value.isSavingCrewName)
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun save_crew_name_blank_is_rejected_via_validation() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val vm = buildVm(ownerId, repo)

        vm.onIntent(CrewSettingsIntent.CrewNameChanged("   "))
        vm.onIntent(CrewSettingsIntent.SaveCrewName)

        assertEquals(CrewError.Validation.NameBlank, vm.state.value.error)
        assertEquals(false, vm.state.value.isSavingCrewName)
    }

    @Test
    fun owner_toggles_blind_voting_on_and_state_reflects_it_and_tracks_setting_changed() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val analytics = RecordingAnalyticsTracker()
        val vm = buildVm(ownerId, repo, analytics)
        assertEquals(false, vm.state.value.crew?.blindVoting)

        vm.onIntent(CrewSettingsIntent.ToggleBlindVoting(true))

        assertEquals(Pair(crewId, true), repo.lastSetBlindVoting)
        assertEquals(true, vm.state.value.crew?.blindVoting)
        assertEquals(false, vm.state.value.isSavingBlindVoting)
        assertNull(vm.state.value.error)
        assertEquals(
            listOf<AnalyticsEvent>(AnalyticsEvent.SettingChanged(AppSetting.BLIND_VOTING, enabled = true)),
            analytics.events.toList(),
        )
    }

    @Test
    fun non_owner_toggle_blind_voting_surfaces_authorization_error_and_does_not_track() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val analytics = RecordingAnalyticsTracker()
        val vm = buildVm(memberId, repo, analytics)

        vm.onIntent(CrewSettingsIntent.ToggleBlindVoting(true))

        assertEquals(CrewError.Authorization.NotOwner, vm.state.value.error)
        assertEquals(false, vm.state.value.isSavingBlindVoting)
        assertNull(repo.lastSetBlindVoting)
        assertEquals(false, vm.state.value.crew?.blindVoting)
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun owner_saves_tagline_and_writes_trimmed_value_to_repo() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val vm = buildVm(ownerId, repo)

        vm.onIntent(CrewSettingsIntent.TaglineChanged("  only home-cooked  "))
        vm.onIntent(CrewSettingsIntent.SaveTagline)

        assertEquals(crewId to "only home-cooked", repo.lastSetTagline)
        assertEquals(false, vm.state.value.isSavingTagline)
        assertNull(vm.state.value.error)
    }

    @Test
    fun owner_clearing_tagline_is_not_clobbered_by_a_later_crew_emission() = runTest {
        // Regression: editingTagline must seed exactly once. An empty edit (owner cleared the
        // field) must survive subsequent observeCrew re-emissions — isEmpty() is NOT the
        // "not seeded" sentinel because empty is a legitimate edited value.
        val crewWithTagline = sampleCrew.copy(tagline = (CrewTagline.of("old rules") as Result.Ok).value)
        val repo = FakeCrewRepository(listOf(crewWithTagline))
        val vm = buildVm(ownerId, repo)
        // Seeded from the crew on first load.
        assertEquals("old rules", vm.state.value.editingTagline)

        // Owner clears the field but does NOT save yet.
        vm.onIntent(CrewSettingsIntent.TaglineChanged(""))
        assertEquals("", vm.state.value.editingTagline)

        // A later crew-doc change re-emits (e.g. blind-voting toggled by another path). The
        // tagline edit must NOT snap back to "old rules".
        repo.crews.value = listOf(crewWithTagline.copy(blindVoting = true))
        assertEquals("", vm.state.value.editingTagline)
    }

    @Test
    fun owner_tagline_too_long_surfaces_error_and_rolls_back_to_saved_value() = runTest {
        val crewWithTagline = sampleCrew.copy(tagline = (CrewTagline.of("keep me") as Result.Ok).value)
        val repo = FakeCrewRepository(listOf(crewWithTagline))
        val vm = buildVm(ownerId, repo)

        vm.onIntent(CrewSettingsIntent.TaglineChanged("x".repeat(CrewTagline.MAX_LEN + 1)))
        vm.onIntent(CrewSettingsIntent.SaveTagline)

        assertEquals(CrewError.Validation.TaglineTooLong, vm.state.value.error)
        assertEquals(false, vm.state.value.isSavingTagline)
        // Rolled back to the value saved on the crew before the failed write.
        assertEquals("keep me", vm.state.value.editingTagline)
        assertNull(repo.lastSetTagline)
    }

    @Test
    fun non_owner_save_tagline_surfaces_authorization_error() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val vm = buildVm(memberId, repo)

        vm.onIntent(CrewSettingsIntent.TaglineChanged("sneaky"))
        vm.onIntent(CrewSettingsIntent.SaveTagline)

        assertEquals(CrewError.Authorization.NotOwner, vm.state.value.error)
        assertEquals(false, vm.state.value.isSavingTagline)
        assertNull(repo.lastSetTagline)
    }

    @Test
    fun share_link_tapped_tracks_crew_invite_shared() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val analytics = RecordingAnalyticsTracker()
        val vm = buildVm(ownerId, repo, analytics)

        vm.onIntent(CrewSettingsIntent.ShareLinkTapped)

        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.CrewInviteShared(crewId)), analytics.events.toList())
    }

    @Test
    fun leave_crew_emits_left_effect_on_success_and_tracks_crew_left() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew)).apply {
            nextLeave = Result.success(Unit)
        }
        val analytics = RecordingAnalyticsTracker()
        val vm = buildVm(memberId, repo, analytics)

        vm.effects.test {
            vm.onIntent(CrewSettingsIntent.Leave)
            assertEquals(CrewSettingsEffect.Left, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(false, vm.state.value.isLeaving)
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.CrewLeft(crewId)), analytics.events.toList())
        // Note: after a successful leave the crew is removed from the fake repo, so
        // observeCrew re-emits NotFound — that error surfacing back onto state is the
        // expected behavior (the screen has navigated away by then).
    }

    @Test
    fun leave_crew_terminal_error_surfaces_on_state_and_does_not_track() = runTest {
        // A non-connectivity (terminal) failure still surfaces to state. Connectivity-class
        // failures (Backend.Network/Unavailable) now fall back to the outbox instead — covered
        // in LeaveCrewUseCaseTest.
        val repo = FakeCrewRepository(listOf(sampleCrew)).apply {
            nextLeave = Result.failure(CrewError.Backend.PermissionDenied)
        }
        val analytics = RecordingAnalyticsTracker()
        val vm = buildVm(memberId, repo, analytics)

        vm.onIntent(CrewSettingsIntent.Leave)

        assertEquals(CrewError.Backend.PermissionDenied, vm.state.value.error)
        assertEquals(false, vm.state.value.isLeaving)
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun confirm_delete_emits_deleted_effect_on_success_for_owner_and_tracks_crew_deleted() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        val analytics = RecordingAnalyticsTracker()
        val vm = buildVm(ownerId, repo, analytics)

        vm.effects.test {
            vm.onIntent(CrewSettingsIntent.ConfirmDelete)
            assertEquals(CrewSettingsEffect.Deleted, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(crewId, repo.lastDelete)
        assertEquals(false, vm.state.value.isDeleting)
        assertEquals(false, vm.state.value.showDeleteConfirm)
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.CrewDeleted(crewId)), analytics.events.toList())
    }

    @Test
    fun request_delete_sets_dialog_flag() = runTest {
        val vm = buildVm(ownerId)

        vm.onIntent(CrewSettingsIntent.RequestDelete)

        assertTrue(vm.state.value.showDeleteConfirm)
    }

    @Test
    fun cancel_delete_clears_dialog_flag() = runTest {
        val vm = buildVm(ownerId)
        vm.onIntent(CrewSettingsIntent.RequestDelete)
        assertTrue(vm.state.value.showDeleteConfirm)

        vm.onIntent(CrewSettingsIntent.CancelDelete)

        assertEquals(false, vm.state.value.showDeleteConfirm)
    }

    @Test
    fun switch_crew_emits_navigate_effect() = runTest {
        val vm = buildVm(ownerId)

        vm.effects.test {
            vm.onIntent(CrewSettingsIntent.SwitchCrew)
            assertEquals(CrewSettingsEffect.NavigateToCrewPicker, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun buildVm(
        actingAs: AccountId,
        repo: FakeCrewRepository = FakeCrewRepository(listOf(sampleCrew)),
        analytics: RecordingAnalyticsTracker = RecordingAnalyticsTracker(),
    ): CrewSettingsViewModel {
        val session = FixedSessionProvider(Session(accountId = actingAs, activeCrewId = crewId))
        val connectivity = FakeConnectivityPort(online = true)
        val outbox = RecordingOutboxPort()
        return CrewSettingsViewModel(
            crewId = crewId,
            observeCrew = ObserveCrewUseCase(repo),
            renameCrew = RenameCrewUseCase(repo, session, connectivity, outbox),
            deleteCrew = DeleteCrewUseCase(repo, session),
            setBlindVoting = SetBlindVotingUseCase(repo, session, connectivity, outbox),
            setCrewTagline = SetCrewTaglineUseCase(repo, session),
            setCrewWelcomeMessage = SetCrewWelcomeMessageUseCase(repo, session),
            setCrewWeeklyChallenge = SetCrewWeeklyChallengeUseCase(repo, session, FixedClock(Instant.fromEpochMilliseconds(1700000000000))),
            setCrewScoreStyle = SetCrewScoreStyleUseCase(repo, session),
            leaveCrew = LeaveCrewUseCase(repo, connectivity, outbox),
            removeMember = RemoveMemberUseCase(repo, session, connectivity, outbox),
            setCrewBanner = SetCrewBannerUseCase(repo, session),
            removeCrewBanner = RemoveCrewBannerUseCase(repo, session),
            setCrewBannerFocal = SetCrewBannerFocalUseCase(repo, session),
            welcomePort = TestCrewWelcomePort,
            session = session,
            accountRead = EmptyAccountReadPort,
            analytics = analytics,
        )
    }

    /** Minimal [CrewWelcomePort] for the VM under test — only the banner-URL flow is exercised. */
    private object TestCrewWelcomePort : CrewWelcomePort {
        override fun observeWelcomeMessage(crewId: CrewId): Flow<String?> = flowOf(null)
        override fun isWelcomeDismissed(crewId: CrewId): Flow<Boolean> = flowOf(false)
        override suspend fun dismissWelcome(crewId: CrewId) = Unit
        override fun observeWeeklyChallenge(crewId: CrewId): Flow<WeeklyChallengeSnapshot?> = flowOf(null)
        override fun observeScoreStyle(crewId: CrewId): Flow<CrewScoreStyle> = flowOf(CrewScoreStyle.Stars)
        override fun observeBannerImageUrl(crewId: CrewId): Flow<String?> = flowOf(null)
        override fun observeBannerFocalY(crewId: CrewId): Flow<Float> = flowOf(0.5f)
    }

    private class FixedSessionProvider(private val session: Session?) : SessionProvider {
        override val current: Flow<Session?> = flowOf(session)
        override suspend fun requireCurrent(): Result<Session, SessionError> =
            session?.let { Result.success(it) } ?: Result.failure(SessionError.NotSignedIn)
    }

    private object EmptyAccountReadPort : AccountReadPort {
        override fun observe(id: AccountId): Flow<Account?> = MutableStateFlow(null)
    }
}
