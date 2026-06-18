package es.schsebastian.foodrats.core.presentation.mvi

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MviViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private data class CounterState(val value: Int = 0) : MviState
    private sealed interface CounterIntent : MviIntent {
        data object Increment : CounterIntent
        data object EmitDone  : CounterIntent
    }
    private sealed interface CounterEffect : MviEffect { data object Done : CounterEffect }

    private class CounterVm : MviViewModel<CounterState, CounterIntent, CounterEffect>(CounterState()) {
        override suspend fun handle(intent: CounterIntent) = when (intent) {
            CounterIntent.Increment -> update { it.copy(value = it.value + 1) }
            CounterIntent.EmitDone  -> emit(CounterEffect.Done)
        }
    }

    @Test fun intent_updates_state() = runTest {
        val vm = CounterVm()
        vm.state.test {
            assertEquals(0, awaitItem().value)
            vm.onIntent(CounterIntent.Increment)
            assertEquals(1, expectMostRecentItem().value)
        }
    }

    @Test fun intent_emits_effect() = runTest {
        val vm = CounterVm()
        vm.effects.test {
            vm.onIntent(CounterIntent.EmitDone)
            assertEquals(CounterEffect.Done, awaitItem())
        }
    }
}
