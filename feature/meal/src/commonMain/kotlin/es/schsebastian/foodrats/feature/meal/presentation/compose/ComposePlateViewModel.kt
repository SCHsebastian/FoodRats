package es.schsebastian.foodrats.feature.meal.presentation.compose

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.FoodTag
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrElse
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftCommand
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class ComposePlateViewModel(
    private val updateDraft: UpdateMealDraftUseCase,
    private val repository: MealRepository,
    private val activeCrew: ActiveCrewProvider,
    private val clock: Clock,
    private val zone: TimeZone,
) : MviViewModel<ComposePlateState, ComposePlateIntent, ComposePlateEffect>(ComposePlateState()) {

    init {
        viewModelScope.launch { loadTakenSlots() }
    }

    private suspend fun loadTakenSlots() {
        val crewId = activeCrew.current.first() ?: return
        val now = clock.now().toLocalDateTime(zone)
        val today = MealDay.today(clock, zone)
        val defaultSlot = MealSlot.defaultForHour(now.hour)

        val taken = when (val r = repository.takenSlotsFor(crewId, today)) {
            is Result.Ok  -> r.value
            is Result.Err -> emptySet()
        }

        val selectedSlot = if (defaultSlot !in taken) {
            defaultSlot
        } else {
            MealSlot.entries.firstOrNull { it !in taken } ?: defaultSlot
        }

        update { it.copy(takenSlots = taken, selectedSlot = selectedSlot) }
    }

    override suspend fun handle(intent: ComposePlateIntent) {
        when (intent) {
            is ComposePlateIntent.DishChanged  -> update { it.copy(dish = intent.value, error = null) }
            is ComposePlateIntent.ScoreChanged -> update { it.copy(score = intent.value, error = null) }
            is ComposePlateIntent.TagToggled   -> update {
                val tags = it.selectedTags.toMutableSet().also { s ->
                    if (intent.tag in s) s.remove(intent.tag) else s.add(intent.tag)
                }
                it.copy(selectedTags = tags)
            }
            is ComposePlateIntent.SelectSlot   -> {
                update { it.copy(selectedSlot = intent.slot) }
                val draft = repository.observeDraft().first() ?: return
                repository.saveDraft(draft.copy(slot = intent.slot))
            }
            ComposePlateIntent.Continue -> persistAndAdvance()
        }
    }

    private suspend fun persistAndAdvance() {
        val state = currentState
        val score = state.score?.let { Score.of(it).getOrElse { return failWith(MealError.Validation.OutOfRange) } }
            ?: return failWith(MealError.Validation.OutOfRange)
        val dish = DishName.of(state.dish).getOrElse { return failWith(MealError.Validation.Blank) }
        val tags = state.selectedTags.map { raw ->
            FoodTag.Curated.entries.firstOrNull { it.label == raw }
                ?: FoodTag.custom(raw).getOrElse { return failWith(MealError.Validation.Blank) }
        }
        updateDraft(UpdateMealDraftCommand.SetScore(score))
        updateDraft(UpdateMealDraftCommand.SetDish(dish))
        val r = updateDraft(UpdateMealDraftCommand.SetTags(tags))
        if (r is Result.Ok) emit(ComposePlateEffect.NavigateToPublish)
        else if (r is Result.Err) update { it.copy(error = r.error) }
    }

    private fun failWith(err: MealError) { update { it.copy(error = err) } }
}
