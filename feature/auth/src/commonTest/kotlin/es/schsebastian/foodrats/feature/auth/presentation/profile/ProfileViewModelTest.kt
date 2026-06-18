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
import es.schsebastian.foodrats.core.domain.analytics.ConsentDecision
import es.schsebastian.foodrats.core.domain.analytics.ConsentPort
import es.schsebastian.foodrats.core.domain.analytics.RecordingAnalyticsTracker
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.notifications.NotificationPermissionPort
import es.schsebastian.foodrats.core.domain.notifications.NotificationPermissionStatus
import es.schsebastian.foodrats.core.domain.preferences.AppLocale
import es.schsebastian.foodrats.core.domain.preferences.LocalePort
import es.schsebastian.foodrats.core.domain.preferences.LocalePreferenceError
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferenceError
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferencePort
import es.schsebastian.foodrats.core.domain.preferences.ThemeMode
import es.schsebastian.foodrats.core.domain.preferences.ThemeModePort
import es.schsebastian.foodrats.core.domain.preferences.ThemePreferenceError
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SignOutPort
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.DeleteMyAccountUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.EnableNotificationsUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.ExportMyDataUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.SetLocaleUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.SetNotificationsEnabledUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.SetThemeModeUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.UpdateMyAvatarUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.UpdateMyDisplayNameUseCase
import es.schsebastian.foodrats.feature.auth.i18n.AuthStringKey
import es.schsebastian.foodrats.feature.auth.testdoubles.FakeAccountWritePort
import es.schsebastian.foodrats.feature.auth.testdoubles.FixedSessionProvider
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

    private fun buildViewModel(
        deletionResult: Result<Unit, AccountDeletionError>,
        analytics: RecordingAnalyticsTracker,
        signOut: RecordingSignOutPort,
        consent: ConsentPort = FakeConsentPort(),
        exportPort: DataExportPort = FakeDataExportPort(
            Result.success(ExportReady(downloadUrl = "https://example.test/x", expiresAtMs = 0L)),
        ),
    ): ProfileViewModel {
        val session = FixedSessionProvider(Session(accountId = accountId, activeCrewId = null))
        val writePort = FakeAccountWritePort()
        return ProfileViewModel(
            accountRead = FakeAccountReadPort(account),
            session = session,
            themePort = NoopThemeModePort,
            localePort = NoopLocalePort,
            notificationsPort = NoopNotificationsPreferencePort,
            updateDisplayName = UpdateMyDisplayNameUseCase(writePort, session),
            updateAvatar = UpdateMyAvatarUseCase(writePort, session),
            signOut = signOut,
            setThemeMode = SetThemeModeUseCase(NoopThemeModePort),
            setLocale = SetLocaleUseCase(NoopLocalePort),
            setNotificationsEnabled = SetNotificationsEnabledUseCase(NoopNotificationsPreferencePort),
            enableNotifications = EnableNotificationsUseCase(NoopNotificationPermissionPort, NoopNotificationsPreferencePort),
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
}
