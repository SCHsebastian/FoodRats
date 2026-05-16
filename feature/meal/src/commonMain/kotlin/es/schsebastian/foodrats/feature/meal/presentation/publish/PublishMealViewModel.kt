package es.schsebastian.foodrats.feature.meal.presentation.publish

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.meal.domain.usecase.ObserveMealDraftUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.PublishMealUseCase
import es.schsebastian.foodrats.feature.notifications.domain.usecase.ScheduleStreakNudgeUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch

class PublishMealViewModel(
    private val observeDraft: ObserveMealDraftUseCase,
    private val publishMeal: PublishMealUseCase,
    private val scheduleStreakNudge: ScheduleStreakNudgeUseCase,
) : MviViewModel<PublishMealState, PublishMealIntent, PublishMealEffect>(PublishMealState()) {

    init {
        observeDraft().onEach { d -> update { it.copy(draft = d) } }.launchIn(viewModelScope)
    }

    override suspend fun handle(intent: PublishMealIntent) {
        when (intent) {
            PublishMealIntent.Load    -> { val d = observeDraft().first(); update { it.copy(draft = d) } }
            PublishMealIntent.Publish -> {
                val draft = currentState.draft ?: return
                update { it.copy(isPublishing = true) }
                val r = publishMeal(draft)
                update { it.copy(isPublishing = false, error = if (r is Result.Err) r.error else null) }
                if (r is Result.Ok) {
                    // Fire-and-forget streak nudge; title/body are hardcoded for MVP.
                    // TODO: resolve from i18n via StringResolver injected into the scheduler.
                    viewModelScope.launch {
                        scheduleStreakNudge(
                            title = "Don't break the streak",
                            body  = "Post your meal before midnight!",
                        )
                    }
                    emit(PublishMealEffect.Published)
                }
            }
        }
    }
}
