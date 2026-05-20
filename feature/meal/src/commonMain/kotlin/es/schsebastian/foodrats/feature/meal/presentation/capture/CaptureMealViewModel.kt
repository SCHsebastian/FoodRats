package es.schsebastian.foodrats.feature.meal.presentation.capture

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.Plate
import es.schsebastian.foodrats.feature.meal.domain.usecase.StartMealDraftUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftCommand
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftUseCase

class CaptureMealViewModel(
    private val startDraft: StartMealDraftUseCase,
    private val updateDraft: UpdateMealDraftUseCase,
    private val sessionProvider: SessionProvider,
) : MviViewModel<CaptureMealState, CaptureMealIntent, CaptureMealEffect>(CaptureMealState()) {

    override suspend fun handle(intent: CaptureMealIntent) {
        when (intent) {
            CaptureMealIntent.Start -> {
                val session = when (val r = sessionProvider.requireCurrent()) {
                    is Result.Ok  -> r.value
                    is Result.Err -> { update { it.copy(error = MealError.Read.Unauthorized) }; return }
                }
                val crewId = session.activeCrewId
                    ?: run { update { it.copy(error = MealError.Read.CrewNotFound) }; return }
                startDraft(crewId, session.accountId).also { result ->
                    if (result is Result.Err) update { it.copy(error = result.error) }
                }
            }
            is CaptureMealIntent.PhotoTaken -> {
                update { it.copy(isCapturing = true) }
                val r = updateDraft(UpdateMealDraftCommand.SetPhoto(Plate(intent.bytes)))
                update { it.copy(isCapturing = false, error = if (r is Result.Err) r.error else null) }
                if (r is Result.Ok) emit(CaptureMealEffect.NavigateToCompose)
            }
            CaptureMealIntent.OpenSettings -> emit(CaptureMealEffect.OpenAppSettings)
        }
    }
}
