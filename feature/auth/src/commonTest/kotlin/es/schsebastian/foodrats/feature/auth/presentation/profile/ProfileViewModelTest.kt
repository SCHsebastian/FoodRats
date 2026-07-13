package es.schsebastian.foodrats.feature.auth.presentation.profile

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.account.AccountDeletionError
import es.schsebastian.foodrats.core.domain.account.AccountDeletionPort
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.account.DataExportError
import es.schsebastian.foodrats.core.domain.account.DataExportPort
import es.schsebastian.foodrats.core.domain.account.ExportReady
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsConfig
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AppSetting
import es.schsebastian.foodrats.core.domain.analytics.ConsentDecision
import es.schsebastian.foodrats.core.domain.analytics.ConsentPort
import es.schsebastian.foodrats.core.domain.analytics.RecordingAnalyticsTracker
import es.schsebastian.foodrats.core.domain.preferences.AccentPalette
import es.schsebastian.foodrats.core.domain.preferences.AccentPaletteError
import es.schsebastian.foodrats.core.domain.preferences.AccentPalettePort
import es.schsebastian.foodrats.core.domain.preferences.AiPreferenceError
import es.schsebastian.foodrats.core.domain.preferences.AiPreferencePort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.notifications.NotificationPermissionPort
import es.schsebastian.foodrats.core.domain.notifications.NotificationPermissionStatus
import es.schsebastian.foodrats.core.domain.preferences.AppLocale
import es.schsebastian.foodrats.core.domain.preferences.LocalePort
import es.schsebastian.foodrats.core.domain.preferences.LocalePreferenceError
import es.schsebastian.foodrats.core.domain.preferences.MealReminderPreferenceError
import es.schsebastian.foodrats.core.domain.preferences.MealReminderSchedulePort
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferenceError
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferencePort
import es.schsebastian.foodrats.core.domain.preferences.ThemeMode
import es.schsebastian.foodrats.core.domain.preferences.ThemeModePort
import es.schsebastian.foodrats.core.domain.preferences.ThemePreferenceError
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SignOutPort
import es.schsebastian.foodrats.core.domain.account.AccountWriteError
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.DeleteMyAccountUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.EnableNotificationsUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.ExportMyDataUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.RemoveMyAvatarUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.SetAccentPaletteUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.SetAiEnabledUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.SetLocaleUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.SetMealRemindersUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.SetNotificationsEnabledUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.SetThemeModeUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.UpdateMyAvatarUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.UpdateMyBioUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.UpdateMyDisplayNameUseCase
import es.schsebastian.foodrats.feature.auth.i18n.AuthStringKey
import es.schsebastian.foodrats.feature.auth.testdoubles.FakeAccountWritePort
import es.schsebastian.foodrats.feature.auth.testdoubles.FakeConnectivityPort
import es.schsebastian.foodrats.feature.auth.testdoubles.FixedSessionProvider
import es.schsebastian.foodrats.feature.auth.testdoubles.RecordingOutboxPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val accountId = (AccountId.of("u1") as Result.Ok).value
    private val account = Account(
        id = accountId,
        handle = "ana",
        displayName = "Ana",
        email = "ana@example.test",
        avatarUrl = null,
    )
    private val expectedPhrase = "DELETE Ana"

    private companion object {
        val FIXED_AT: Instant = Instant.parse("2026-06-14T12:00:00Z")
    }

    private class FakeAccountReadPort(private val account: Account) : AccountReadPort {
        override fun observe(id: AccountId): Flow<Account?> = flowOf(account)
    }

    private object NoopThemeModePort : ThemeModePort {
        override val mode: Flow<ThemeMode> = flowOf(ThemeMode.System)
        override suspend fun set(mode: ThemeMode): Result<Unit, ThemePreferenceError> = Result.success(Unit)
    }

    private object NoopLocalePort : LocalePort {
        override val locale: Flow<AppLocale> = flowOf(AppLocale.System)
        override suspend fun set(locale: AppLocale): Result<Unit, LocalePreferenceError> = Result.success(Unit)
    }

    private object NoopNotificationsPreferencePort : NotificationsPreferencePort {
        override val enabled: Flow<Boolean> = flowOf(true)
        override val prompted: Flow<Boolean> = flowOf(true)
        override suspend fun set(enabled: Boolean): Result<Unit, NotificationsPreferenceError> = Result.success(Unit)
        override suspend fun markPrompted(): Result<Unit, NotificationsPreferenceError> = Result.success(Unit)
    }

    /** In-memory [AiPreferencePort]: `set` updates the observed flow (single source of truth). */
    private class FakeAiPreferencePort(initial: Boolean = true) : AiPreferencePort {
        private val state = MutableStateFlow(initial)
        override val enabled: Flow<Boolean> = state.asStateFlow()
        override suspend fun set(enabled: Boolean): Result<Unit, AiPreferenceError> {
            state.value = enabled
            return Result.success(Unit)
        }
    }

    /** [AiPreferencePort] whose `set` always fails — for asserting the error branch. */
    private class FailingAiPreferencePort : AiPreferencePort {
        override val enabled: Flow<Boolean> = flowOf(true)
        override suspend fun set(enabled: Boolean): Result<Unit, AiPreferenceError> =
            Result.failure(AiPreferenceError.Persist.Unavailable)
    }

    /** In-memory [AccentPalettePort]: `set` updates the observed flow (single source of truth). */
    private class FakeAccentPalettePort(initial: AccentPalette = AccentPalette.Ember) : AccentPalettePort {
        private val state = MutableStateFlow(initial)
        override val palette: Flow<AccentPalette> = state.asStateFlow()
        override suspend fun set(palette: AccentPalette): Result<Unit, AccentPaletteError> {
            state.value = palette
            return Result.success(Unit)
        }
    }

    /** [AccentPalettePort] whose `set` always fails — for asserting the rollback branch. */
    private class FailingAccentPalettePort : AccentPalettePort {
        override val palette: Flow<AccentPalette> = flowOf(AccentPalette.Ember)
        override suspend fun set(palette: AccentPalette): Result<Unit, AccentPaletteError> =
            Result.failure(AccentPaletteError.Persist.Unavailable)
    }

    /** In-memory [MealReminderSchedulePort]: `set` updates the observed flow (single source of truth). */
    private class FakeMealReminderSchedulePort(
        initial: List<LocalTime> = MealReminderSchedulePort.DEFAULT_TIMES,
    ) : MealReminderSchedulePort {
        private val state = MutableStateFlow(initial)
        override val times: Flow<List<LocalTime>> = state.asStateFlow()
        override suspend fun set(times: List<LocalTime>): Result<Unit, MealReminderPreferenceError> {
            state.value = times
            return Result.success(Unit)
        }
    }

    private object NoopNotificationPermissionPort : NotificationPermissionPort {
        override suspend fun current() = NotificationPermissionStatus.Granted
        override suspend fun request() = NotificationPermissionStatus.Granted
        override fun openSystemSettings() = Unit
    }

    private class RecordingSignOutPort : SignOutPort {
        var signOutCount = 0
            private set
        override suspend fun signOut(): Result<Unit, SessionError> {
            signOutCount++
            return Result.success(Unit)
        }
    }

    /**
     * Mutable [ConsentPort] double: `grant`/`revoke` push a current-version decision into [decision]
     * (the same source the VM observes) and bump the call counters. Mirrors the real repository's
     * local-first behaviour without DataStore.
     */
    private class FakeConsentPort(initial: ConsentDecision = ConsentDecision.Unknown) : ConsentPort {
        private val state = MutableStateFlow(initial)
        override val decision: Flow<ConsentDecision> = state.asStateFlow()
        var grantCount = 0
            private set
        var revokeCount = 0
            private set
        var denyCount = 0
            private set
        override suspend fun grant() {
            grantCount++
            state.value = ConsentDecision.Granted(AnalyticsConfig.CURRENT_CONSENT_VERSION, FIXED_AT)
        }
        override suspend fun deny() {
            denyCount++
            state.value = ConsentDecision.Denied(AnalyticsConfig.CURRENT_CONSENT_VERSION, FIXED_AT)
        }
        override suspend fun revoke() {
            revokeCount++
            state.value = ConsentDecision.Denied(AnalyticsConfig.CURRENT_CONSENT_VERSION, FIXED_AT)
        }
    }

    private class FakeDataExportPort(
        private val result: Result<ExportReady, DataExportError>,
    ) : DataExportPort {
        var calls = 0
            private set
        override suspend fun exportMyData(): Result<ExportReady, DataExportError> {
            calls++
            return result
        }
    }

    private class FakeAccountDeletionPort(
        private val result: Result<Unit, AccountDeletionError>,
    ) : AccountDeletionPort {
        val calls: MutableList<Pair<AccountId, String>> = mutableListOf()
        override suspend fun requestDeletion(
            accountId: AccountId,
            confirmation: String,
        ): Result<Unit, AccountDeletionError> {
            calls += accountId to confirmation
            return result
        }
    }

    /** [ThemeModePort] whose `set` always fails — for asserting the error branch fires no event. */
    private object FailingThemeModePort : ThemeModePort {
        override val mode: Flow<ThemeMode> = flowOf(ThemeMode.System)
        override suspend fun set(mode: ThemeMode): Result<Unit, ThemePreferenceError> =
            Result.failure(ThemePreferenceError.Persist.Unavailable)
    }

    /** [LocalePort] whose `set` always fails. */
    private object FailingLocalePort : LocalePort {
        override val locale: Flow<AppLocale> = flowOf(AppLocale.System)
        override suspend fun set(locale: AppLocale): Result<Unit, LocalePreferenceError> =
            Result.failure(LocalePreferenceError.Persist.Unavailable)
    }

    /** [MealReminderSchedulePort] whose `set` always fails (the observed times never change). */
    private class FailingMealReminderSchedulePort(
        initial: List<LocalTime> = MealReminderSchedulePort.DEFAULT_TIMES,
    ) : MealReminderSchedulePort {
        private val state = MutableStateFlow(initial)
        override val times: Flow<List<LocalTime>> = state.asStateFlow()
        override suspend fun set(times: List<LocalTime>): Result<Unit, MealReminderPreferenceError> =
            Result.failure(MealReminderPreferenceError.Persist.Unavailable)
    }

    private fun buildViewModel(
        deletionResult: Result<Unit, AccountDeletionError>,
        analytics: RecordingAnalyticsTracker,
        signOut: RecordingSignOutPort,
        consent: ConsentPort = FakeConsentPort(),
        exportPort: DataExportPort = FakeDataExportPort(
            Result.success(ExportReady(downloadUrl = "https://example.test/x", expiresAtMs = 0L)),
        ),
        reminders: MealReminderSchedulePort = FakeMealReminderSchedulePort(),
        themePort: ThemeModePort = NoopThemeModePort,
        localePort: LocalePort = NoopLocalePort,
        aiPreferencePort: AiPreferencePort = FakeAiPreferencePort(),
        accentPalettePort: AccentPalettePort = FakeAccentPalettePort(),
        writePort: FakeAccountWritePort = FakeAccountWritePort(),
    ): ProfileViewModel {
        val session = FixedSessionProvider(Session(accountId = accountId, activeCrewId = null))
        return ProfileViewModel(
            accountRead = FakeAccountReadPort(account),
            session = session,
            themePort = themePort,
            localePort = localePort,
            notificationsPort = NoopNotificationsPreferencePort,
            aiPreferencePort = aiPreferencePort,
            accentPalettePort = accentPalettePort,
            mealRemindersPort = reminders,
            updateDisplayName = UpdateMyDisplayNameUseCase(writePort, session, FakeConnectivityPort(), RecordingOutboxPort()),
            updateBio = UpdateMyBioUseCase(writePort, session, FakeConnectivityPort(), RecordingOutboxPort()),
            updateAvatar = UpdateMyAvatarUseCase(writePort, session),
            removeAvatar = RemoveMyAvatarUseCase(writePort, session),
            signOut = signOut,
            setThemeMode = SetThemeModeUseCase(themePort),
            setLocale = SetLocaleUseCase(localePort),
            setMealReminders = SetMealRemindersUseCase(reminders),
            setNotificationsEnabled = SetNotificationsEnabledUseCase(NoopNotificationsPreferencePort),
            enableNotifications = EnableNotificationsUseCase(NoopNotificationPermissionPort, NoopNotificationsPreferencePort),
            setAiEnabled = SetAiEnabledUseCase(aiPreferencePort),
            setAccentPalette = SetAccentPaletteUseCase(accentPalettePort),
            notificationPermission = NoopNotificationPermissionPort,
            deleteMyAccount = DeleteMyAccountUseCase(session, FakeAccountDeletionPort(deletionResult)),
            exportMyData = ExportMyDataUseCase(exportPort),
            consent = consent,
            analytics = analytics,
        )
    }

    @Test fun ok_runs_finish_sequence_event_then_clear_identity_then_sign_out() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val signOut = RecordingSignOutPort()
        val vm = buildViewModel(Result.success(Unit), analytics, signOut)

        vm.state.test {
            // Seed the confirmation phrase, then confirm.
            vm.onIntent(ProfileIntent.OpenDeleteAccount)
            vm.onIntent(ProfileIntent.DeleteConfirmationChanged(expectedPhrase))
            vm.onIntent(ProfileIntent.DeleteDialogConfirm(expectedPhrase))

            val final = expectMostRecentItem()
            assertEquals(false, final.isDeletingAccount)
            assertEquals(false, final.deleteScreenOpen)
            assertEquals("", final.deleteConfirmation)
            assertNull(final.deleteError)
            cancelAndIgnoreRemainingEvents()
        }

        // The event fires exactly once, BEFORE the identity is cleared.
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.AccountDeleted), analytics.events)
        // setUserId(null) + resetData() severed the analytics identity.
        assertEquals(listOf<AccountId?>(null), analytics.userIds)
        assertEquals(1, analytics.resetCount)
        // Local sign-out happened.
        assertEquals(1, signOut.signOutCount)
    }

    @Test fun err_sets_error_and_fires_nothing() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val signOut = RecordingSignOutPort()
        val vm = buildViewModel(
            Result.failure(AccountDeletionError.Deletion.OwnerReassignFailed),
            analytics,
            signOut,
        )

        vm.state.test {
            vm.onIntent(ProfileIntent.OpenDeleteAccount)
            vm.onIntent(ProfileIntent.DeleteConfirmationChanged(expectedPhrase))
            vm.onIntent(ProfileIntent.DeleteDialogConfirm(expectedPhrase))

            val final = expectMostRecentItem()
            assertEquals(false, final.isDeletingAccount)
            // Screen stays open so the user can retry; phrase preserved.
            assertTrue(final.deleteScreenOpen)
            assertEquals(AuthStringKey.DeleteAccountErrorOwnership, final.deleteError)
            cancelAndIgnoreRemainingEvents()
        }

        // No teardown fired on the error path — the session must stay valid for a retry.
        assertTrue(analytics.events.isEmpty())
        assertTrue(analytics.userIds.isEmpty())
        assertEquals(0, analytics.resetCount)
        assertEquals(0, signOut.signOutCount)
    }

    // auth-01 regression: the expected phrase is carried from the screen in the locale the
    // user actually saw, so the Spanish "BORRAR <name>" must confirm just like English would.
    @Test fun spanish_phrase_confirms_deletion() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val signOut = RecordingSignOutPort()
        val vm = buildViewModel(Result.success(Unit), analytics, signOut)
        val spanishPhrase = "BORRAR Ana"

        vm.state.test {
            vm.onIntent(ProfileIntent.OpenDeleteAccount)
            vm.onIntent(ProfileIntent.DeleteConfirmationChanged(spanishPhrase))
            vm.onIntent(ProfileIntent.DeleteDialogConfirm(spanishPhrase))

            val final = expectMostRecentItem()
            assertEquals(false, final.isDeletingAccount)
            assertEquals(false, final.deleteScreenOpen)
            assertNull(final.deleteError)
            cancelAndIgnoreRemainingEvents()
        }

        // The deletion ran end-to-end: the account-deleted event fired and we signed out.
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.AccountDeleted), analytics.events)
        assertEquals(1, signOut.signOutCount)
    }

    // A typed input that doesn't match the displayed phrase must NOT delete — it surfaces a
    // phrase-mismatch error and fires no teardown (the use case re-validates as defense in depth).
    @Test fun wrong_phrase_does_not_confirm_deletion() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val signOut = RecordingSignOutPort()
        val vm = buildViewModel(Result.success(Unit), analytics, signOut)

        vm.state.test {
            vm.onIntent(ProfileIntent.OpenDeleteAccount)
            // User typed the English phrase but the displayed (expected) phrase was Spanish.
            vm.onIntent(ProfileIntent.DeleteConfirmationChanged("DELETE Ana"))
            vm.onIntent(ProfileIntent.DeleteDialogConfirm("BORRAR Ana"))

            val final = expectMostRecentItem()
            assertEquals(false, final.isDeletingAccount)
            // Screen stays open; the mismatch error is shown.
            assertTrue(final.deleteScreenOpen)
            assertEquals(AuthStringKey.DeleteAccountErrorPhrase, final.deleteError)
            cancelAndIgnoreRemainingEvents()
        }

        // Nothing destructive happened.
        assertTrue(analytics.events.isEmpty())
        assertEquals(0, signOut.signOutCount)
    }

    @Test fun consent_toggle_on_grants_records_event_and_row_reflects_decision() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val consent = FakeConsentPort(ConsentDecision.Unknown)
        val vm = buildViewModel(Result.success(Unit), analytics, RecordingSignOutPort(), consent)

        vm.state.test {
            // Starts off (no decision yet).
            assertFalse(expectMostRecentItem().analyticsConsentGranted)

            vm.onIntent(ProfileIntent.AnalyticsConsentToggled(true))

            // Row now reflects the observed Granted decision (single source of truth).
            assertTrue(expectMostRecentItem().analyticsConsentGranted)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, consent.grantCount)
        assertEquals(0, consent.revokeCount)
        // ConsentGranted fires once, AFTER the grant lands.
        assertEquals(
            listOf<AnalyticsEvent>(AnalyticsEvent.ConsentGranted(AnalyticsConfig.CURRENT_CONSENT_VERSION)),
            analytics.events,
        )
    }

    @Test fun consent_toggle_off_revokes_records_no_event_and_row_reflects_decision() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val granted = ConsentDecision.Granted(AnalyticsConfig.CURRENT_CONSENT_VERSION, FIXED_AT)
        val consent = FakeConsentPort(granted)
        val vm = buildViewModel(Result.success(Unit), analytics, RecordingSignOutPort(), consent)

        vm.state.test {
            // Starts on (current-version grant).
            assertTrue(expectMostRecentItem().analyticsConsentGranted)

            vm.onIntent(ProfileIntent.AnalyticsConsentToggled(false))

            // Row reflects the withdrawal.
            assertFalse(expectMostRecentItem().analyticsConsentGranted)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, consent.revokeCount)
        assertEquals(0, consent.grantCount)
        // Withdrawal records NOTHING — tracking is off, so an event would be a consent violation.
        assertTrue(analytics.events.isEmpty())
    }

    @Test fun reminders_seeded_from_persisted_times() = runTest {
        val vm = buildViewModel(
            Result.success(Unit), RecordingAnalyticsTracker(), RecordingSignOutPort(),
            reminders = FakeMealReminderSchedulePort(listOf(LocalTime(8, 0), LocalTime(20, 0))),
        )
        vm.state.test {
            assertEquals(listOf(LocalTime(8, 0), LocalTime(20, 0)), expectMostRecentItem().reminderTimes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun adding_a_reminder_hour_appends_and_sorts() = runTest {
        val vm = buildViewModel(
            Result.success(Unit), RecordingAnalyticsTracker(), RecordingSignOutPort(),
            reminders = FakeMealReminderSchedulePort(listOf(LocalTime(14, 0))),
        )
        vm.state.test {
            vm.onIntent(ProfileIntent.ReminderAddOpen)
            vm.onIntent(ProfileIntent.ReminderHourSelected(8))
            val final = expectMostRecentItem()
            assertEquals(listOf(LocalTime(8, 0), LocalTime(14, 0)), final.reminderTimes)
            assertFalse(final.reminderPickerOpen)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun editing_a_reminder_replaces_that_slot() = runTest {
        val vm = buildViewModel(
            Result.success(Unit), RecordingAnalyticsTracker(), RecordingSignOutPort(),
            reminders = FakeMealReminderSchedulePort(listOf(LocalTime(9, 0), LocalTime(18, 0))),
        )
        vm.state.test {
            vm.onIntent(ProfileIntent.ReminderEditOpen(0))
            vm.onIntent(ProfileIntent.ReminderHourSelected(7))
            assertEquals(listOf(LocalTime(7, 0), LocalTime(18, 0)), expectMostRecentItem().reminderTimes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun removing_a_reminder_drops_that_slot() = runTest {
        val vm = buildViewModel(
            Result.success(Unit), RecordingAnalyticsTracker(), RecordingSignOutPort(),
            reminders = FakeMealReminderSchedulePort(listOf(LocalTime(9, 0), LocalTime(18, 0))),
        )
        vm.state.test {
            vm.onIntent(ProfileIntent.ReminderRemove(0))
            assertEquals(listOf(LocalTime(18, 0)), expectMostRecentItem().reminderTimes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun duplicate_hour_is_not_added_twice() = runTest {
        val vm = buildViewModel(
            Result.success(Unit), RecordingAnalyticsTracker(), RecordingSignOutPort(),
            reminders = FakeMealReminderSchedulePort(listOf(LocalTime(14, 0))),
        )
        vm.state.test {
            vm.onIntent(ProfileIntent.ReminderAddOpen)
            vm.onIntent(ProfileIntent.ReminderHourSelected(14))
            assertEquals(listOf(LocalTime(14, 0)), expectMostRecentItem().reminderTimes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun export_ok_exposes_download_url_and_clears_in_flight() = runTest {
        val ready = ExportReady(downloadUrl = "https://export.test/u1.json", expiresAtMs = 99L)
        val export = FakeDataExportPort(Result.success(ready))
        val vm = buildViewModel(
            Result.success(Unit),
            RecordingAnalyticsTracker(),
            RecordingSignOutPort(),
            exportPort = export,
        )

        vm.state.test {
            vm.onIntent(ProfileIntent.ExportMyData)

            val final = expectMostRecentItem()
            assertFalse(final.isExportingData)
            assertEquals("https://export.test/u1.json", final.exportDownloadUrl)
            assertEquals(99L, final.exportExpiresAtMs)
            assertNull(final.exportError)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, export.calls)
    }

    @Test fun export_err_sets_error_and_no_url() = runTest {
        val export = FakeDataExportPort(Result.failure(DataExportError.Backend.Unavailable))
        val vm = buildViewModel(
            Result.success(Unit),
            RecordingAnalyticsTracker(),
            RecordingSignOutPort(),
            exportPort = export,
        )

        vm.state.test {
            vm.onIntent(ProfileIntent.ExportMyData)

            val final = expectMostRecentItem()
            assertFalse(final.isExportingData)
            assertNull(final.exportDownloadUrl)
            assertEquals(AuthStringKey.ExportDataErrorBackend, final.exportError)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, export.calls)
    }

    // ─────────────────────── setting_changed analytics ───────────────────────

    @Test fun theme_change_ok_fires_setting_changed_theme() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val vm = buildViewModel(Result.success(Unit), analytics, RecordingSignOutPort())

        vm.state.test {
            vm.onIntent(ProfileIntent.ThemeSelected(ThemeMode.Dark))
            expectMostRecentItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(
            listOf<AnalyticsEvent>(AnalyticsEvent.SettingChanged(AppSetting.THEME)),
            analytics.events,
        )
    }

    @Test fun theme_change_err_fires_nothing() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val vm = buildViewModel(
            Result.success(Unit), analytics, RecordingSignOutPort(),
            themePort = FailingThemeModePort,
        )

        vm.state.test {
            vm.onIntent(ProfileIntent.ThemeSelected(ThemeMode.Dark))
            assertEquals(AuthStringKey.ProfileThemePersistFailed, expectMostRecentItem().themeError)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(analytics.events.isEmpty())
    }

    @Test fun locale_change_ok_fires_setting_changed_language() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val vm = buildViewModel(Result.success(Unit), analytics, RecordingSignOutPort())

        vm.state.test {
            vm.onIntent(ProfileIntent.LocaleSelected(AppLocale.En))
            expectMostRecentItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(
            listOf<AnalyticsEvent>(AnalyticsEvent.SettingChanged(AppSetting.LANGUAGE)),
            analytics.events,
        )
    }

    @Test fun locale_change_err_fires_nothing() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val vm = buildViewModel(
            Result.success(Unit), analytics, RecordingSignOutPort(),
            localePort = FailingLocalePort,
        )

        vm.state.test {
            vm.onIntent(ProfileIntent.LocaleSelected(AppLocale.En))
            assertEquals(AuthStringKey.ProfileLanguagePersistFailed, expectMostRecentItem().localeError)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(analytics.events.isEmpty())
    }

    @Test fun notifications_toggle_ok_fires_setting_changed_with_enabled() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val vm = buildViewModel(Result.success(Unit), analytics, RecordingSignOutPort())

        vm.state.test {
            // Disable path persists the pref (no OS gate) and resolves Ok.
            vm.onIntent(ProfileIntent.NotificationsToggled(false))
            expectMostRecentItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(
            listOf<AnalyticsEvent>(AnalyticsEvent.SettingChanged(AppSetting.NOTIFICATIONS, enabled = false)),
            analytics.events,
        )
    }

    @Test fun reminder_add_ok_fires_setting_changed_meal_reminders() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val vm = buildViewModel(
            Result.success(Unit), analytics, RecordingSignOutPort(),
            reminders = FakeMealReminderSchedulePort(listOf(LocalTime(14, 0))),
        )

        vm.state.test {
            vm.onIntent(ProfileIntent.ReminderAddOpen)
            vm.onIntent(ProfileIntent.ReminderHourSelected(8))
            expectMostRecentItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(
            listOf<AnalyticsEvent>(AnalyticsEvent.SettingChanged(AppSetting.MEAL_REMINDERS)),
            analytics.events,
        )
    }

    @Test fun reminder_remove_ok_fires_setting_changed_meal_reminders() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val vm = buildViewModel(
            Result.success(Unit), analytics, RecordingSignOutPort(),
            reminders = FakeMealReminderSchedulePort(listOf(LocalTime(9, 0), LocalTime(18, 0))),
        )

        vm.state.test {
            vm.onIntent(ProfileIntent.ReminderRemove(0))
            expectMostRecentItem()
            cancelAndIgnoreRemainingEvents()
        }

        // doSetReminders is shared by add/edit and remove — the remove path emits MEAL_REMINDERS too.
        assertEquals(
            listOf<AnalyticsEvent>(AnalyticsEvent.SettingChanged(AppSetting.MEAL_REMINDERS)),
            analytics.events,
        )
    }

    @Test fun reminder_change_err_fires_nothing() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val vm = buildViewModel(
            Result.success(Unit), analytics, RecordingSignOutPort(),
            reminders = FailingMealReminderSchedulePort(listOf(LocalTime(14, 0))),
        )

        vm.state.test {
            vm.onIntent(ProfileIntent.ReminderAddOpen)
            vm.onIntent(ProfileIntent.ReminderHourSelected(8))
            assertEquals(AuthStringKey.ProfileRemindersPersistFailed, expectMostRecentItem().reminderError)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(analytics.events.isEmpty())
    }

    // ─────────────────────── AI opt-out toggle ───────────────────────

    @Test fun ai_toggled_off_persists_and_reflects_in_state() = runTest {
        val aiPort = FakeAiPreferencePort(initial = true)
        val vm = buildViewModel(
            Result.success(Unit), RecordingAnalyticsTracker(), RecordingSignOutPort(),
            aiPreferencePort = aiPort,
        )

        vm.state.test {
            // Starts enabled (default).
            assertTrue(expectMostRecentItem().aiEnabled)

            vm.onIntent(ProfileIntent.AiToggled(false))

            // State reflects the disabled toggle (persisted + collector fires).
            val final = expectMostRecentItem()
            assertFalse(final.aiEnabled)
            assertNull(final.aiError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─────────────────────── bio update ───────────────────────

    @Test fun bio_change_updates_editing_field() = runTest {
        val vm = buildViewModel(Result.success(Unit), RecordingAnalyticsTracker(), RecordingSignOutPort())
        vm.state.test {
            vm.onIntent(ProfileIntent.BioChanged("Home cook"))
            val state = expectMostRecentItem()
            assertEquals("Home cook", state.editingBio)
            assertNull(state.saveBioError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun bio_save_ok_clears_in_flight_and_no_error() = runTest {
        val vm = buildViewModel(Result.success(Unit), RecordingAnalyticsTracker(), RecordingSignOutPort())
        vm.state.test {
            vm.onIntent(ProfileIntent.BioChanged("Home cook, Barcelona"))
            vm.onIntent(ProfileIntent.SaveBio)
            val state = expectMostRecentItem()
            assertFalse(state.isSavingBio)
            assertNull(state.saveBioError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun bio_save_too_long_shows_error() = runTest {
        val vm = buildViewModel(Result.success(Unit), RecordingAnalyticsTracker(), RecordingSignOutPort())
        vm.state.test {
            // 101 characters — exceeds the 100-char cap
            val tooLong = "a".repeat(101)
            vm.onIntent(ProfileIntent.BioChanged(tooLong))
            vm.onIntent(ProfileIntent.SaveBio)
            val state = expectMostRecentItem()
            assertFalse(state.isSavingBio)
            assertEquals(AuthStringKey.ProfileBioTooLong, state.saveBioError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun ai_toggled_err_rolls_back_and_shows_error() = runTest {
        val vm = buildViewModel(
            Result.success(Unit), RecordingAnalyticsTracker(), RecordingSignOutPort(),
            aiPreferencePort = FailingAiPreferencePort(),
        )

        vm.state.test {
            // Starts enabled.
            assertTrue(expectMostRecentItem().aiEnabled)

            vm.onIntent(ProfileIntent.AiToggled(false))

            val final = expectMostRecentItem()
            // Rolled back to previous value.
            assertTrue(final.aiEnabled)
            assertEquals(AuthStringKey.ProfileAiPersistFailed, final.aiError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── remove avatar ────────────────────────────────────────────────────────

    @Test fun remove_avatar_confirm_flow_removes_and_clears_state() = runTest {
        val writePort = FakeAccountWritePort()
        val vm = buildViewModel(
            deletionResult = Result.success(Unit),
            analytics = RecordingAnalyticsTracker(),
            signOut = RecordingSignOutPort(),
            writePort = writePort,
        )

        vm.state.test {
            // Confirm dialog opens on request.
            vm.onIntent(ProfileIntent.RemoveAvatarRequested)
            assertTrue(expectMostRecentItem().removeAvatarConfirmOpen)

            // Confirm closes the dialog, sets in-flight, then clears on success.
            vm.onIntent(ProfileIntent.RemoveAvatarConfirmed)
            val final = expectMostRecentItem()
            assertFalse(final.isRemovingAvatar)
            assertFalse(final.removeAvatarConfirmOpen)
            assertNull(final.removeAvatarError)
            cancelAndIgnoreRemainingEvents()
        }
        // The port was called exactly once with the correct accountId.
        assertEquals(1, writePort.avatarRemovals.size)
        assertEquals(accountId, writePort.avatarRemovals[0])
    }

    @Test fun remove_avatar_dismiss_closes_dialog_without_calling_port() = runTest {
        val writePort = FakeAccountWritePort()
        val vm = buildViewModel(
            deletionResult = Result.success(Unit),
            analytics = RecordingAnalyticsTracker(),
            signOut = RecordingSignOutPort(),
            writePort = writePort,
        )

        vm.state.test {
            vm.onIntent(ProfileIntent.RemoveAvatarRequested)
            assertTrue(expectMostRecentItem().removeAvatarConfirmOpen)

            vm.onIntent(ProfileIntent.RemoveAvatarDismissed)
            assertFalse(expectMostRecentItem().removeAvatarConfirmOpen)
            cancelAndIgnoreRemainingEvents()
        }
        // Port never called.
        assertTrue(writePort.avatarRemovals.isEmpty())
    }

    @Test fun remove_avatar_failure_surfaces_error_key() = runTest {
        val writePort = FakeAccountWritePort().also {
            it.nextRemoveAvatarError = AccountWriteError.Backend.Unavailable
        }
        val vm = buildViewModel(
            deletionResult = Result.success(Unit),
            analytics = RecordingAnalyticsTracker(),
            signOut = RecordingSignOutPort(),
            writePort = writePort,
        )

        vm.state.test {
            vm.onIntent(ProfileIntent.RemoveAvatarRequested)
            vm.onIntent(ProfileIntent.RemoveAvatarConfirmed)
            val final = expectMostRecentItem()
            assertFalse(final.isRemovingAvatar)
            assertEquals(AuthStringKey.ProfileRemoveAvatarError, final.removeAvatarError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── avatar prepare failures (picker error / compressor refused) ─────────────────────────────

    @Test fun avatar_prepare_failed_too_large_surfaces_upload_error() = runTest {
        val vm = buildViewModel(Result.success(Unit), RecordingAnalyticsTracker(), RecordingSignOutPort())
        vm.state.test {
            vm.onIntent(ProfileIntent.AvatarPrepareFailed(ProfileError.AvatarPrepare.TooLarge))
            val final = expectMostRecentItem()
            assertFalse(final.isUploadingAvatar)
            assertEquals(AuthStringKey.ProfileAvatarTooLarge, final.uploadAvatarError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun avatar_prepare_failed_unreadable_surfaces_upload_error() = runTest {
        val vm = buildViewModel(Result.success(Unit), RecordingAnalyticsTracker(), RecordingSignOutPort())
        vm.state.test {
            vm.onIntent(ProfileIntent.AvatarPrepareFailed(ProfileError.AvatarPrepare.Unreadable))
            assertEquals(AuthStringKey.ProfileAvatarUnreadable, expectMostRecentItem().uploadAvatarError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun avatar_prepare_failed_pick_failed_surfaces_upload_error() = runTest {
        val vm = buildViewModel(Result.success(Unit), RecordingAnalyticsTracker(), RecordingSignOutPort())
        vm.state.test {
            vm.onIntent(ProfileIntent.AvatarPrepareFailed(ProfileError.AvatarPrepare.PickFailed))
            assertEquals(AuthStringKey.ProfileAvatarPickFailed, expectMostRecentItem().uploadAvatarError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun prepare_failure_never_reaches_the_write_port() = runTest {
        val writePort = FakeAccountWritePort()
        val vm = buildViewModel(
            deletionResult = Result.success(Unit),
            analytics = RecordingAnalyticsTracker(),
            signOut = RecordingSignOutPort(),
            writePort = writePort,
        )
        vm.state.test {
            vm.onIntent(ProfileIntent.AvatarPrepareFailed(ProfileError.AvatarPrepare.TooLarge))
            expectMostRecentItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(writePort.avatarUploads.isEmpty())
    }

    @Test fun next_successful_pick_clears_the_prepare_error() = runTest {
        val writePort = FakeAccountWritePort()
        val vm = buildViewModel(
            deletionResult = Result.success(Unit),
            analytics = RecordingAnalyticsTracker(),
            signOut = RecordingSignOutPort(),
            writePort = writePort,
        )
        vm.state.test {
            vm.onIntent(ProfileIntent.AvatarPrepareFailed(ProfileError.AvatarPrepare.Unreadable))
            assertEquals(AuthStringKey.ProfileAvatarUnreadable, expectMostRecentItem().uploadAvatarError)

            vm.onIntent(ProfileIntent.AvatarPicked(byteArrayOf(1, 2, 3)))
            val final = expectMostRecentItem()
            assertNull(final.uploadAvatarError)
            assertFalse(final.isUploadingAvatar)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, writePort.avatarUploads.size)
    }

    // ── Accent palette tests ──────────────────────────────────────────────────────────────────────

    @Test fun accent_picker_open_sets_flag() = runTest {
        val vm = buildViewModel(Result.success(Unit), RecordingAnalyticsTracker(), RecordingSignOutPort())
        vm.state.test {
            vm.onIntent(ProfileIntent.AccentPickerOpen)
            val s = expectMostRecentItem()
            assertTrue(s.accentPickerOpen)
            assertNull(s.accentError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun accent_picker_dismiss_clears_flag() = runTest {
        val vm = buildViewModel(Result.success(Unit), RecordingAnalyticsTracker(), RecordingSignOutPort())
        vm.state.test {
            vm.onIntent(ProfileIntent.AccentPickerOpen)
            vm.onIntent(ProfileIntent.AccentPickerDismiss)
            val s = expectMostRecentItem()
            assertFalse(s.accentPickerOpen)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun accent_selected_updates_state_and_closes_picker() = runTest {
        val port = FakeAccentPalettePort(AccentPalette.Ember)
        val vm = buildViewModel(Result.success(Unit), RecordingAnalyticsTracker(), RecordingSignOutPort(),
            accentPalettePort = port)
        vm.state.test {
            vm.onIntent(ProfileIntent.AccentPickerOpen)
            vm.onIntent(ProfileIntent.AccentSelected(AccentPalette.Steel))
            val s = expectMostRecentItem()
            assertFalse(s.accentPickerOpen)
            assertEquals(AccentPalette.Steel, s.accentPalette)
            assertNull(s.accentError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun accent_persist_failure_rolls_back_and_surfaces_error() = runTest {
        val failingPort = FailingAccentPalettePort()
        val vm = buildViewModel(Result.success(Unit), RecordingAnalyticsTracker(), RecordingSignOutPort(),
            accentPalettePort = failingPort)
        vm.state.test {
            vm.onIntent(ProfileIntent.AccentSelected(AccentPalette.Moss))
            val s = expectMostRecentItem()
            // rolled back to the initial Ember value
            assertEquals(AccentPalette.Ember, s.accentPalette)
            assertEquals(AuthStringKey.ProfileAccentPersistFailed, s.accentError)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
