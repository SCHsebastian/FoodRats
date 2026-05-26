package es.schsebastian.foodrats.feature.meal.presentation.compose

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.location.LocationError
import es.schsebastian.foodrats.core.domain.location.LocationProvider
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealUploadCoordinator
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrElse
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import es.schsebastian.foodrats.feature.meal.domain.usecase.ClassifyDraftPlateUseCase
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
    private val uploadCoordinator: MealUploadCoordinator,
    private val locationProvider: LocationProvider,
    private val classifyPlate: ClassifyDraftPlateUseCase,
    private val clock: Clock,
    private val zone: TimeZone,
) : MviViewModel<ComposePlateState, ComposePlateIntent, ComposePlateEffect>(ComposePlateState()) {

    // Content fingerprint of the last photo we kicked a classification off for.
    // Guards against re-classifying the same plate on every draft re-emission /
    // screen re-entry while still re-running when the user re-captures.
    private var lastClassifiedFingerprint: Int? = null

    init {
        viewModelScope.launch { loadTakenSlots() }
        repository.observeDraft().onEach { draft ->
            update {
                it.copy(
                    photoBytes = draft?.plate?.photoBytes,
                    coordinates = draft?.coordinates,
                    draftIngredients = draft?.ingredients ?: emptyList(),
                    detectedIngredients = draft?.detectedIngredients ?: emptyList(),
                    canContinue = computeCanContinue(
                        dish = it.dish,
                        descriptionTooLong = it.descriptionTooLong,
                        photo = draft?.plate?.photoBytes,
                        slot = it.selectedSlot,
                        taken = it.takenSlots,
                    ),
                )
            }
        }.launchIn(viewModelScope)
    }

    /**
     * Runs on-device classification for a freshly captured plate. Idempotent per
     * photo content: a repeat call with the same bytes (screen re-entry) is a
     * no-op, but a different photo (re-capture) re-classifies and overwrites any
     * prior detection + manual edits via [UpdateMealDraftCommand.SetDetected].
     *
     * Classification is advisory — failures surface a banner but never block
     * publishing, so `canContinue` is untouched here.
     */
    fun onPhotoCaptured(bytes: ByteArray) {
        val fingerprint = bytes.contentHashCode()
        if (fingerprint == lastClassifiedFingerprint) return
        lastClassifiedFingerprint = fingerprint
        update { it.copy(classifying = true, classifierError = null) }
        viewModelScope.launch {
            when (val r = classifyPlate(bytes)) {
                is Result.Ok -> {
                    updateDraft(UpdateMealDraftCommand.SetDetected(r.value.ingredients, r.value.version))
                    update {
                        it.copy(
                            classifying = false,
                            detectedIngredients = r.value.ingredients,
                            draftIngredients = r.value.ingredients,
                        )
                    }
                }
                is Result.Err -> update { it.copy(classifying = false, classifierError = r.error) }
            }
        }
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

        update {
            it.copy(
                takenSlots = taken,
                selectedSlot = selectedSlot,
                canContinue = computeCanContinue(it.dish, it.descriptionTooLong, it.photoBytes, selectedSlot, taken),
            )
        }
        updateDraft(UpdateMealDraftCommand.SetSlot(selectedSlot))
    }

    override suspend fun handle(intent: ComposePlateIntent) {
        when (intent) {
            is ComposePlateIntent.DishChanged -> update {
                it.copy(
                    dish = intent.value,
                    error = null,
                    canContinue = computeCanContinue(
                        intent.value, it.descriptionTooLong, it.photoBytes, it.selectedSlot, it.takenSlots,
                    ),
                )
            }
            is ComposePlateIntent.DescriptionChanged -> {
                val tooLong = intent.value.trim().length > Description.MAX_LEN
                update {
                    it.copy(
                        descriptionInput = intent.value,
                        descriptionTooLong = tooLong,
                        error = if (tooLong) MealError.Validation.DescriptionTooLong else null,
                        canContinue = computeCanContinue(it.dish, tooLong, it.photoBytes, it.selectedSlot, it.takenSlots),
                    )
                }
            }
            is ComposePlateIntent.SelectSlot -> {
                update {
                    it.copy(
                        selectedSlot = intent.slot,
                        canContinue = computeCanContinue(it.dish, it.descriptionTooLong, it.photoBytes, intent.slot, it.takenSlots),
                    )
                }
                updateDraft(UpdateMealDraftCommand.SetSlot(intent.slot))
            }
            ComposePlateIntent.RequestLocation -> requestLocation()
            ComposePlateIntent.ClearLocation -> {
                update { it.copy(coordinates = null, error = null) }
                updateDraft(UpdateMealDraftCommand.SetCoordinates(null))
            }
            ComposePlateIntent.RequestConfirm -> {
                val ok = persistDraft()
                if (ok) update { it.copy(showConfirm = true) }
            }
            ComposePlateIntent.DismissConfirm -> update { it.copy(showConfirm = false) }
            ComposePlateIntent.ConfirmPublish -> {
                update { it.copy(showConfirm = false) }
                uploadCoordinator.enqueueDraftUpload()
                emit(ComposePlateEffect.UploadEnqueued)
            }
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

    /**
     * Persists the in-memory draft (dish + description) to the draft store so
     * the background coordinator can read it via `observeDraft().first()`.
     * Returns false (and surfaces an error) when validation fails.
     */
    private suspend fun persistDraft(): Boolean {
        val state = currentState
        val dish = DishName.of(state.dish).getOrElse {
            update { it.copy(error = MealError.Validation.Blank) }
            return false
        }
        val description = Description.of(state.descriptionInput).getOrElse {
            update { it.copy(error = MealError.Validation.DescriptionTooLong) }
            return false
        }
        updateDraft(UpdateMealDraftCommand.SetSlot(state.selectedSlot))
        updateDraft(UpdateMealDraftCommand.SetDish(dish))
        val r = updateDraft(UpdateMealDraftCommand.SetDescription(description))
        if (r is Result.Err) {
            update { it.copy(error = r.error) }
            return false
        }
        return true
    }

    private fun computeCanContinue(
        dish: String,
        descriptionTooLong: Boolean,
        photo: ByteArray?,
        slot: MealSlot,
        taken: Set<MealSlot>,
    ): Boolean = dish.isNotBlank() &&
        !descriptionTooLong &&
        photo != null &&
        slot !in taken
}
