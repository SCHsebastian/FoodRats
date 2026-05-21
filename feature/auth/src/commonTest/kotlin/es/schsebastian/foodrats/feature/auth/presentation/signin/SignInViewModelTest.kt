package es.schsebastian.foodrats.feature.auth.presentation.signin

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.notifications.TokenRegistrationError
import es.schsebastian.foodrats.core.domain.notifications.TokenRegistrationPort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError
import es.schsebastian.foodrats.feature.auth.domain.test.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private object NoopTokenRegistrationPort : TokenRegistrationPort {
    override suspend fun registerCurrentDeviceToken(): Result<Unit, TokenRegistrationError> =
        Result.success(Unit)
}

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val sampleSession = Session(accountId = (AccountId.of("uid-1") as Result.Ok).value, activeCrewId = null)

    @Test fun ok_path_emits_signedIn_effect() = runTest {
        val vm = SignInViewModel(FakeAuthRepository(Result.success(sampleSession)), NoopTokenRegistrationPort)
        vm.effects.test {
            vm.onIntent(SignInIntent.ContinueWithGoogle)
            assertEquals(SignInEffect.SignedIn, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(SignInState(isLoading = false, error = null), vm.state.value)
    }

    @Test fun error_path_keeps_error_on_state_no_effect() = runTest {
        val err = AuthError.GoogleSignIn.NetworkUnavailable
        val vm = SignInViewModel(FakeAuthRepository(Result.failure(err)), NoopTokenRegistrationPort)
        vm.onIntent(SignInIntent.ContinueWithGoogle)
        assertEquals(SignInState(isLoading = false, error = err), vm.state.value)
    }

    @Test fun loading_then_resolved() = runTest {
        val vm = SignInViewModel(FakeAuthRepository(Result.success(sampleSession)), NoopTokenRegistrationPort)
        vm.state.test {
            assertEquals(SignInState(), awaitItem())          // initial idle state
            vm.onIntent(SignInIntent.ContinueWithGoogle)
            // With UnconfinedTestDispatcher the coroutine runs inline; consume all
            // intermediate states and assert the final resolved state.
            val final = cancelAndConsumeRemainingEvents()
                .filterIsInstance<app.cash.turbine.Event.Item<SignInState>>()
                .lastOrNull()?.value
                ?: vm.state.value
            assertEquals(false, final.isLoading)
            assertNull(final.error)
        }
    }

    @Test fun dismissError_clears_error() = runTest {
        val err = AuthError.GoogleSignIn.UnknownClientFailure
        val vm = SignInViewModel(FakeAuthRepository(Result.failure(err)), NoopTokenRegistrationPort)
        vm.onIntent(SignInIntent.ContinueWithGoogle)
        assertIs<AuthError>(vm.state.value.error)
        vm.onIntent(SignInIntent.DismissError)
        assertNull(vm.state.value.error)
    }

    @Test fun email_validation_blocks_submit() = runTest {
        val repo = FakeAuthRepository(Result.success(sampleSession))
        val vm = SignInViewModel(repo, NoopTokenRegistrationPort)
        vm.onIntent(SignInIntent.UpdateEmail("not-an-email"))
        vm.onIntent(SignInIntent.UpdatePassword("longenough"))
        vm.onIntent(SignInIntent.SubmitEmail)
        assertEquals(AuthError.EmailPassword.InvalidEmail, vm.state.value.emailError)
        // Repo should not have been called.
        assertNull(repo.lastEmail)
    }

    @Test fun password_too_short_blocks_submit() = runTest {
        val repo = FakeAuthRepository(Result.success(sampleSession))
        val vm = SignInViewModel(repo, NoopTokenRegistrationPort)
        vm.onIntent(SignInIntent.UpdateEmail("a@b.co"))
        vm.onIntent(SignInIntent.UpdatePassword("12345"))
        vm.onIntent(SignInIntent.SubmitEmail)
        assertEquals(AuthError.EmailPassword.WeakPassword, vm.state.value.passwordError)
        assertNull(repo.lastEmail)
    }

    @Test fun email_signin_ok_emits_signedIn() = runTest {
        val repo = FakeAuthRepository(
            signInResult = Result.failure(AuthError.GoogleSignIn.UnknownClientFailure),
            emailSignInResult = Result.success(sampleSession),
        )
        val vm = SignInViewModel(repo, NoopTokenRegistrationPort)
        vm.onIntent(SignInIntent.UpdateEmail("rat@foodrats.app"))
        vm.onIntent(SignInIntent.UpdatePassword("supersecret"))
        vm.effects.test {
            vm.onIntent(SignInIntent.SubmitEmail)
            assertEquals(SignInEffect.SignedIn, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("rat@foodrats.app", repo.lastEmail)
        assertEquals("signIn", repo.lastMode)
    }

    @Test fun toggle_mode_to_signup_routes_to_signup() = runTest {
        val repo = FakeAuthRepository(
            signInResult = Result.failure(AuthError.GoogleSignIn.UnknownClientFailure),
            emailSignUpResult = Result.success(sampleSession),
        )
        val vm = SignInViewModel(repo, NoopTokenRegistrationPort)
        vm.onIntent(SignInIntent.ToggleMode)
        assertEquals(SignInMode.SignUp, vm.state.value.mode)
        vm.onIntent(SignInIntent.UpdateEmail("new@rat.com"))
        vm.onIntent(SignInIntent.UpdatePassword("supersecret"))
        vm.effects.test {
            vm.onIntent(SignInIntent.SubmitEmail)
            assertEquals(SignInEffect.SignedIn, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("signUp", repo.lastMode)
    }

    @Test fun wrong_credentials_maps_to_passwordError() = runTest {
        val repo = FakeAuthRepository(
            signInResult = Result.failure(AuthError.GoogleSignIn.UnknownClientFailure),
            emailSignInResult = Result.failure(AuthError.EmailPassword.WrongCredentials),
        )
        val vm = SignInViewModel(repo, NoopTokenRegistrationPort)
        vm.onIntent(SignInIntent.UpdateEmail("rat@foodrats.app"))
        vm.onIntent(SignInIntent.UpdatePassword("wrongpass"))
        vm.onIntent(SignInIntent.SubmitEmail)
        assertEquals(AuthError.EmailPassword.WrongCredentials, vm.state.value.passwordError)
        assertNull(vm.state.value.error)
    }
}
