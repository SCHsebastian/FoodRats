package es.schsebastian.foodrats.feature.meal.presentation.compose

import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.FoodTag
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrElse
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftCommand
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftUseCase

class ComposePlateViewModel(
    private val updateDraft: UpdateMealDraftUseCase,
) : MviViewModel<ComposePlateState, ComposePlateIntent, ComposePlateEffect>(ComposePlateState()) {

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
