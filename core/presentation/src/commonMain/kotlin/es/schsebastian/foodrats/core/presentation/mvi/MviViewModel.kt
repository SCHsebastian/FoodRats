package es.schsebastian.foodrats.core.presentation.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class MviViewModel<S : MviState, I : MviIntent, E : MviEffect>(
    initial: S,
) : ViewModel() {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<S> = _state.asStateFlow()
    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    protected val currentState: S get() = _state.value

    /**
     * Tag for the auto-instrumented intent → state → effect trace. Defaults to
     * `"MVI/<concrete-class-simpleName>"`; override for a stable name across
     * refactors or to group several VMs under one tag. Mute a single VM with
     * `FrLog.disable("MVI/FooViewModel")`.
     */
    protected open val logTag: String get() = "${FrLog.Tags.Mvi}/${this::class.simpleName ?: "Unknown"}"

    fun onIntent(intent: I) {
        FrLog.d(logTag) { "intent $intent" }
        viewModelScope.launch { handle(intent) }
    }

    protected abstract suspend fun handle(intent: I)

    protected fun update(reducer: (S) -> S) {
        _state.update(reducer)
        FrLog.d(logTag) { "state ${_state.value}" }
    }

    protected suspend fun emit(effect: E) {
        FrLog.d(logTag) { "effect $effect" }
        _effects.send(effect)
    }

    override fun onCleared() {
        super.onCleared()
        _effects.close()
    }
}
