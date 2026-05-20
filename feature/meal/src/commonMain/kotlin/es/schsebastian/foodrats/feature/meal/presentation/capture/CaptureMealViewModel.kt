package es.schsebastian.foodrats.feature.meal.presentation.capture

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
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
                    is Result.Err -> { println("[CaptureMealViewModel] session error: ${r.error}"); return }
                }
                val crewId = session.activeCrewId
                    ?: run { println("[CaptureMealViewModel] no active crew"); return }
                startDraft(crewId, session.accountId).also { result ->
                    if (result is Result.Err) println("[CaptureMealViewModel] startDraft error: ${result.error}")
                }
            }
            is CaptureMealIntent.PhotoTaken -> {
                update { it.copy(isCapturing = true) }
                val r = updateDraft(UpdateMealDraftCommand.SetPhoto(Plate(intent.bytes)))
                update { it.copy(isCapturing = false) }
                if (r is Result.Ok) emit(CaptureMealEffect.NavigateToCompose)
                else if (r is Result.Err) println("[CaptureMealViewModel] updateDraft error: ${r.error}")
            }
            CaptureMealIntent.OpenSettings -> emit(CaptureMealEffect.OpenAppSettings)
        }
    }
}
