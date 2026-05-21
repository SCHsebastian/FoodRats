package es.schsebastian.foodrats.feature.meal.presentation.compose

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.location.LocationError
import es.schsebastian.foodrats.core.domain.location.LocationProvider
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrElse
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftCommand
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class ComposePlateViewModel(
    private val updateDraft: UpdateMealDraftUseCase,
    private val repository: MealRepository,
    private val activeCrew: ActiveCrewProvider,
    private val locationProvider: LocationProvider,
    private val clock: Clock,
    private val zone: TimeZone,
) : MviViewModel<ComposePlateState, ComposePlateIntent, ComposePlateEffect>(ComposePlateState()) {

    init {
        viewModelScope.launch { loadTakenSlots() }
        repository.observeDraft().onEach { draft ->
            update {
                it.copy(
                    photoBytes = draft?.plate?.photoBytes,
                    coordinates = draft?.coordinates,
                )
            }
        }.launchIn(viewModelScope)
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
        updateDraft(UpdateMealDraftCommand.SetSlot(selectedSlot))
    }

    override suspend fun handle(intent: ComposePlateIntent) {
        when (intent) {
            is ComposePlateIntent.DishChanged -> update { it.copy(dish = intent.value, error = null) }
            is ComposePlateIntent.DescriptionChanged -> {
                val tooLong = intent.value.trim().length > Description.MAX_LEN
                update {
                    it.copy(
                        descriptionInput = intent.value,
                        descriptionTooLong = tooLong,
                        error = if (tooLong) MealError.Validation.DescriptionTooLong else null,
                    )
                }
            }
            is ComposePlateIntent.SelectSlot -> {
                update { it.copy(selectedSlot = intent.slot) }
                updateDraft(UpdateMealDraftCommand.SetSlot(intent.slot))
            }
            ComposePlateIntent.RequestLocation -> requestLocation()
            ComposePlateIntent.ClearLocation -> {
                update { it.copy(coordinates = null, error = null) }
                updateDraft(UpdateMealDraftCommand.SetCoordinates(null))
            }
            ComposePlateIntent.Continue -> persistAndAdvance()
        }
    }

    private suspend fun requestLocation() {
        if (currentState.locating) return
        update { it.copy(locating = true, error = null) }
        when (val r = locationProvider.current()) {
            is Result.Ok  -> {
                updateDraft(UpdateMealDraftCommand.SetCoordinates(r.value))
                update { it.copy(locating = false, coordinates = r.value, error = null) }
            }
            is Result.Err -> {
                val mealErr: MealError = when (r.error) {
                    LocationError.PermissionDenied -> MealError.Location.PermissionDenied
                    LocationError.Unavailable      -> MealError.Location.Unavailable
                    LocationError.Timeout          -> MealError.Location.Timeout
                }
                update { it.copy(locating = false, error = mealErr) }
            }
        }
    }

    private suspend fun persistAndAdvance() {
        val state = currentState
        val dish = DishName.of(state.dish).getOrElse { return failWith(MealError.Validation.Blank) }
        val description = Description.of(state.descriptionInput)
            .getOrElse { return failWith(MealError.Validation.DescriptionTooLong) }
        updateDraft(UpdateMealDraftCommand.SetSlot(state.selectedSlot))
        updateDraft(UpdateMealDraftCommand.SetDish(dish))
        val r = updateDraft(UpdateMealDraftCommand.SetDescription(description))
        if (r is Result.Ok) emit(ComposePlateEffect.NavigateToPublish)
        else if (r is Result.Err) update { it.copy(error = r.error) }
    }

    private fun failWith(err: MealError) { update { it.copy(error = err) } }
}
