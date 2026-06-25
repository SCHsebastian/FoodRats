package es.schsebastian.foodrats.feature.crew.presentation.picker

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.usecase.CreateCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.ObserveMyCrewsUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.RequestToJoinCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SwitchActiveCrewUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CrewPickerViewModel(
    private val session: SessionProvider,
    private val observeMyCrews: ObserveMyCrewsUseCase,
    private val createCrew: CreateCrewUseCase,
    private val requestToJoin: RequestToJoinCrewUseCase,
    private val switchActive: SwitchActiveCrewUseCase,
    private val analytics: AnalyticsPort = NoopAnalyticsTracker,
) : MviViewModel<CrewPickerState, CrewPickerIntent, CrewPickerEffect>(CrewPickerState()) {

    init {
        viewModelScope.launch {
            // A null session here means the user is signed out / the session was lost. Without this,
            // the `?: return@launch` left isLoading=true forever → a permanent skeleton with no error
            // and no retry (A3). Surface a typed session error and end the skeleton; the root nav
            // routes to SignIn.
            val account = session.current.first()?.accountId ?: run {
                update { it.copy(isLoading = false, error = CrewError.Session.NotSignedIn) }
                return@launch
            }
            observeMyCrews(account).collect { r ->
                when (r) {
                    is Result.Ok  -> update { it.copy(crews = r.value, isLoading = false) }
                    is Result.Err -> update { it.copy(error = r.error, isLoading = false) }
                }
            }
        }
    }

    override suspend fun handle(intent: CrewPickerIntent): Unit = when (intent) {
        CrewPickerIntent.ToggleCreateForm     -> update { it.copy(showCreateForm = !it.showCreateForm, showJoinForm = false, error = null) }
        CrewPickerIntent.ToggleJoinForm       -> update { it.copy(showJoinForm = !it.showJoinForm, showCreateForm = false, error = null) }
        is CrewPickerIntent.CreateInputChanged -> update { it.copy(createInput = intent.value) }
        is CrewPickerIntent.JoinInputChanged   -> update { it.copy(joinInput = intent.value) }
        CrewPickerIntent.SubmitCreate         -> doCreate()
        CrewPickerIntent.SubmitJoin           -> doJoin()
        is CrewPickerIntent.PickCrew          -> {
            switchActive(intent.crewId)
            analytics.track(AnalyticsEvent.CrewSwitched(intent.crewId))
            emit(CrewPickerEffect.CrewSelected(intent.crewId))
        }
        CrewPickerIntent.DismissError         -> update { it.copy(error = null) }
    }

    private suspend fun doCreate() {
        val state = currentState
        val account = session.current.first()?.accountId
            ?: return update { it.copy(error = CrewError.Session.NotSignedIn) }
        update { it.copy(isCreating = true, error = null) }
        when (val r = createCrew(state.createInput, account)) {
            is Result.Ok  -> {
                update { it.copy(isCreating = false, showCreateForm = false, createInput = "") }
                analytics.track(AnalyticsEvent.CrewCreated(r.value.id))
                switchActive(r.value.id)
                emit(CrewPickerEffect.CrewSelected(r.value.id))
            }
            is Result.Err -> update { it.copy(isCreating = false, error = r.error) }
        }
    }

    private suspend fun doJoin() {
        val state = currentState
        val account = session.current.first()?.accountId
            ?: return update { it.copy(error = CrewError.Session.NotSignedIn) }
        update { it.copy(isJoining = true, error = null) }
        // No instant join — this files a request the crew owner must approve. We don't switch the
        // active crew or navigate; the screen shows a "request sent" confirmation and the user lands
        // in the crew (via the live crew-list sync) once the owner approves.
        when (val r = requestToJoin(state.joinInput, account)) {
            is Result.Ok  -> {
                update { it.copy(isJoining = false, showJoinForm = false, joinInput = "") }
                emit(CrewPickerEffect.JoinRequested)
            }
            is Result.Err -> update { it.copy(isJoining = false, error = r.error) }
        }
    }
}
