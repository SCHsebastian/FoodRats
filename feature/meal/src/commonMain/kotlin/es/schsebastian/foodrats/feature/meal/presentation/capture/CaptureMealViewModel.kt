package es.schsebastian.foodrats.feature.meal.presentation.capture

import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.CaptureSource
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
import es.schsebastian.foodrats.core.domain.crew.CrewMembershipPort
import es.schsebastian.foodrats.core.domain.preferences.DefaultAudiencePort
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
    // Noop default (null) keeps existing tests green; the Koin binding passes the real port.
    private val defaultAudience: DefaultAudiencePort? = null,
    private val analytics: AnalyticsPort = NoopAnalyticsTracker,
) : MviViewModel<CaptureMealState, CaptureMealIntent, CaptureMealEffect>(CaptureMealState()) {

    override suspend fun handle(intent: CaptureMealIntent) {
        when (intent) {
            CaptureMealIntent.Start -> {
                val session = when (val r = sessionProvider.requireCurrent()) {
                    is Result.Ok  -> r.value
                    is Result.Err -> { update { it.copy(error = MealStringKey.CaptureSessionError) }; return }
                }
                val allCrewIds = crewMembership.observeMyCrews(session.accountId).first().map { it.id }.toSet()
                if (allCrewIds.isEmpty()) { update { it.copy(error = MealStringKey.CaptureNoCrews) }; return }
                // Seed the audience from the persisted default (intersected with current memberships),
                // falling back to ALL crews when no preference has been saved or the saved set no
                // longer intersects the user's current crews (e.g. they left every saved crew).
                val savedDefault = defaultAudience?.defaultAudience?.first()
                val audienceCrewIds = savedDefault
                    ?.intersect(allCrewIds)
                    ?.takeIf { it.isNotEmpty() }
                    ?: allCrewIds
                update { it.copy(error = null) }
                startDraft(session.accountId, audienceCrewIds).also { result ->
                    if (result is Result.Err) update { it.copy(error = MealStringKey.CaptureDraftFailed) }
                    else analytics.track(AnalyticsEvent.MealCaptureStarted(CaptureSource.UNKNOWN))
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
