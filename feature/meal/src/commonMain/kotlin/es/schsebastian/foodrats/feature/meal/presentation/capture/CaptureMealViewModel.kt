package es.schsebastian.foodrats.feature.meal.presentation.capture

import es.schsebastian.foodrats.core.domain.crew.CrewMembershipPort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.meal.domain.model.Plate
import es.schsebastian.foodrats.feature.meal.i18n.MealStringKey
import es.schsebastian.foodrats.feature.meal.domain.usecase.StartMealDraftUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftCommand
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftUseCase
import kotlinx.coroutines.flow.first

class CaptureMealViewModel(
    private val startDraft: StartMealDraftUseCase,
    private val updateDraft: UpdateMealDraftUseCase,
    private val sessionProvider: SessionProvider,
    private val crewMembership: CrewMembershipPort,
) : MviViewModel<CaptureMealState, CaptureMealIntent, CaptureMealEffect>(CaptureMealState()) {

    override suspend fun handle(intent: CaptureMealIntent) {
        when (intent) {
            CaptureMealIntent.Start -> {
                val session = when (val r = sessionProvider.requireCurrent()) {
                    is Result.Ok  -> r.value
                    is Result.Err -> { update { it.copy(error = MealStringKey.CaptureSessionError) }; return }
                }
                // Default the audience to ALL the author's crews; the composer lets the user
                // narrow it before publishing. No crews → nothing to publish to.
                val crewIds = crewMembership.observeMyCrews(session.accountId).first().map { it.id }.toSet()
                if (crewIds.isEmpty()) { update { it.copy(error = MealStringKey.CaptureNoCrews) }; return }
                update { it.copy(error = null) }
                startDraft(session.accountId, crewIds).also { result ->
                    if (result is Result.Err) update { it.copy(error = MealStringKey.CaptureDraftFailed) }
                }
            }
            is CaptureMealIntent.PhotoTaken -> {
                update { it.copy(isCapturing = true, error = null) }
                val r = updateDraft(UpdateMealDraftCommand.SetPhoto(Plate(intent.bytes)))
                update { it.copy(isCapturing = false) }
                if (r is Result.Ok) emit(CaptureMealEffect.NavigateToCompose)
                else if (r is Result.Err) update { it.copy(error = MealStringKey.CapturePhotoFailed) }
            }
            CaptureMealIntent.OpenSettings -> emit(CaptureMealEffect.OpenAppSettings)
        }
    }
}
