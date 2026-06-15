package es.schsebastian.foodrats.feature.auth.presentation.topbar

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TopBarAvatarViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun accountId(raw: String): AccountId = (AccountId.of(raw) as Result.Ok).value

    private fun account(
        id: AccountId,
        displayName: String,
        avatarUrl: String? = null,
    ) = Account(
        id = id,
        handle = "handle",
        displayName = displayName,
        email = null,
        avatarUrl = avatarUrl,
    )

    private class MutableSessionProvider(initial: Session?) : SessionProvider {
        private val state = MutableStateFlow(initial)
        override val current: StateFlow<Session?> = state.asStateFlow()
        fun emit(session: Session?) { state.value = session }
        override suspend fun requireCurrent(): Result<Session, SessionError> =
            state.value?.let { Result.success(it) } ?: Result.failure(SessionError.NotSignedIn)
    }

    /** One mutable account stream per id; tracks how many subscriptions are currently active. */
    private class MutableAccountReadPort : AccountReadPort {
        private val streams = mutableMapOf<AccountId, MutableStateFlow<Account?>>()
        private val active = mutableMapOf<AccountId, Int>()

        private fun streamFor(id: AccountId) = streams.getOrPut(id) { MutableStateFlow(null) }

        fun emit(account: Account) { streamFor(account.id).value = account }

        fun activeCount(id: AccountId): Int = active[id] ?: 0

        override fun observe(id: AccountId): Flow<Account?> =
            streamFor(id)
                .onStart { active[id] = (active[id] ?: 0) + 1 }
                .onCompletion { active[id] = (active[id] ?: 0) - 1 }
    }

    @Test fun initial_state_has_null_avatar_and_question_mark_initials() = runTest {
        val vm = TopBarAvatarViewModel(
            accountRead = MutableAccountReadPort(),
            session = MutableSessionProvider(null),
        )

        vm.state.test {
            val state = expectMostRecentItem()
            assertEquals(null, state.avatarUrl)
            assertEquals("?", state.initials)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun account_arrival_sets_uppercased_two_letter_initials() = runTest {
        val id = accountId("u1")
        val accounts = MutableAccountReadPort()
        val vm = TopBarAvatarViewModel(
            accountRead = accounts,
            session = MutableSessionProvider(Session(accountId = id, activeCrewId = null)),
        )

        vm.state.test {
            accounts.emit(account(id, displayName = "Ana"))

            val state = expectMostRecentItem()
            assertEquals("AN", state.initials)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun blank_display_name_yields_question_mark_initials() = runTest {
        val id = accountId("u1")
        val accounts = MutableAccountReadPort()
        val vm = TopBarAvatarViewModel(
            accountRead = accounts,
            session = MutableSessionProvider(Session(accountId = id, activeCrewId = null)),
        )

        vm.state.test {
            // "", " ", "  " all take(2) to a blank string -> "?".
            accounts.emit(account(id, displayName = ""))
            assertEquals("?", expectMostRecentItem().initials)

            accounts.emit(account(id, displayName = " "))
            assertEquals("?", expectMostRecentItem().initials)

            accounts.emit(account(id, displayName = "  "))
            assertEquals("?", expectMostRecentItem().initials)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun avatar_url_updates_on_account_change() = runTest {
        val id = accountId("u1")
        val accounts = MutableAccountReadPort()
        val vm = TopBarAvatarViewModel(
            accountRead = accounts,
            session = MutableSessionProvider(Session(accountId = id, activeCrewId = null)),
        )

        vm.state.test {
            accounts.emit(account(id, displayName = "Ana", avatarUrl = null))
            assertEquals(null, expectMostRecentItem().avatarUrl)

            accounts.emit(account(id, displayName = "Ana", avatarUrl = "https://img.test/a.png"))
            assertEquals("https://img.test/a.png", expectMostRecentItem().avatarUrl)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun session_switch_cancels_old_account_subscription_and_starts_new_one() = runTest {
        val first = accountId("u1")
        val second = accountId("u2")
        val accounts = MutableAccountReadPort()
        val session = MutableSessionProvider(Session(accountId = first, activeCrewId = null))
        val vm = TopBarAvatarViewModel(accountRead = accounts, session = session)

        vm.state.test {
            accounts.emit(account(first, displayName = "Ana", avatarUrl = "https://img.test/u1.png"))
            assertEquals("AN", expectMostRecentItem().initials)
            // The first account is being observed; the second is not yet.
            assertEquals(1, accounts.activeCount(first))
            assertEquals(0, accounts.activeCount(second))

            // Switch the active account: flatMapLatest must cancel the first subscription
            // and open one for the second.
            session.emit(Session(accountId = second, activeCrewId = null))
            assertEquals(0, accounts.activeCount(first))
            assertEquals(1, accounts.activeCount(second))

            accounts.emit(account(second, displayName = "Bob", avatarUrl = "https://img.test/u2.png"))
            val state = expectMostRecentItem()
            assertEquals("BO", state.initials)
            assertEquals("https://img.test/u2.png", state.avatarUrl)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
