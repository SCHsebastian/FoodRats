package es.schsebastian.foodrats.feature.meal.presentation.publish

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.notifications.StreakNotificationPort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.meal.domain.usecase.ObserveMealDraftUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.PublishMealUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

class PublishMealViewModel(
    private val observeDraft: ObserveMealDraftUseCase,
    private val publishMeal: PublishMealUseCase,
    private val streakNotifications: StreakNotificationPort,
    private val clock: Clock,
    private val zone: TimeZone,
) : MviViewModel<PublishMealState, PublishMealIntent, PublishMealEffect>(PublishMealState()) {

    init {
        observeDraft().onEach { draft ->
            val isToday = draft?.day?.let { it == MealDay.today(clock, zone) } ?: true
            update { it.copy(draft = draft, isToday = isToday) }
        }.launchIn(viewModelScope)
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
                    // Fire-and-forget streak nudge — the StreakNotificationPort adapter owns
                    // the i18n lookup + try/catch fallback, keeping presentation concerns
                    // out of this ViewModel.
                    viewModelScope.launch { streakNotifications.scheduleStreakNudge() }
                    emit(PublishMealEffect.Published)
                }
            }
        }
    }
}
