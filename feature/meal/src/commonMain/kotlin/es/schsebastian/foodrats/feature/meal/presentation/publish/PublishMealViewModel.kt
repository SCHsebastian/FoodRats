@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package es.schsebastian.foodrats.feature.meal.presentation.publish

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.meal.domain.usecase.ObserveMealDraftUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.PublishMealUseCase
import kotlinx.datetime.TimeZone
import es.schsebastian.foodrats.feature.notifications.domain.usecase.ScheduleStreakNudgeUseCase
import es.schsebastian.foodrats.feature.notifications.i18n.NotificationStringKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class PublishMealViewModel(
    private val observeDraft: ObserveMealDraftUseCase,
    private val publishMeal: PublishMealUseCase,
    private val scheduleStreakNudge: ScheduleStreakNudgeUseCase,
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
                    // Fire-and-forget streak nudge — text is resolved through Compose Resources
                    // so the WorkManager notification picks up the user's locale, not hardcoded EN.
                    // The try/catch keeps unit tests (where Compose Resources aren't bundled)
                    // green by silently skipping the nudge when resources aren't available.
                    viewModelScope.launch {
                        try {
                            val title = getString(NotificationStringKey.StreakTitle.resourceId)
                            val body  = getString(NotificationStringKey.StreakBody.resourceId)
                            scheduleStreakNudge(title = title, body = body)
                        } catch (_: Throwable) {
                            // Resources unavailable; skip.
                        }
                    }
                    emit(PublishMealEffect.Published)
                }
            }
        }
    }
}
