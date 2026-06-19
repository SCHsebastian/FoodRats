package es.schsebastian.foodrats.app.connectivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * App-chrome ViewModel that projects [ConnectivityPort.isOnline] into a UI-ready
 * [StateFlow] for the root NavHost's offline banner (offline-first §P1-T2).
 *
 * There is no intent/effect machinery here — the banner is a pure read of one
 * upstream signal, so this is a thin holder rather than an `MviViewModel` (mirrors
 * how a plain projection avoids the MVI ceremony for a single derived field). The
 * exposed flow is a read-only projection of the port, NOT a parallel mutable state:
 * it holds no `MutableStateFlow` and never writes — the port is the single source of
 * truth. We assume online ([initialValue] = `true`) until the port emits, so the
 * banner is hidden by default and only appears on a confirmed offline reading.
 */
class ConnectivityViewModel(
    connectivity: ConnectivityPort,
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = connectivity.isOnline()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
        )
}
