package es.schsebastian.foodrats.feature.meal.presentation.compose

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
import es.schsebastian.foodrats.core.domain.crew.CrewMembershipPort
import es.schsebastian.foodrats.core.domain.location.LocationError
import es.schsebastian.foodrats.core.domain.location.LocationProvider
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealUploadCoordinator
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.moderation.TextModerationPort
import es.schsebastian.foodrats.core.domain.moderation.TextModerationVerdict
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrElse
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import es.schsebastian.foodrats.feature.meal.domain.usecase.ClassifyDraftPlateUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftCommand
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class ComposePlateViewModel(
    private val updateDraft: UpdateMealDraftUseCase,
    private val repository: MealRepository,
    private val crewMembership: CrewMembershipPort,
    private val uploadCoordinator: MealUploadCoordinator,
    private val locationProvider: LocationProvider,
    private val classifyPlate: ClassifyDraftPlateUseCase,
    private val clock: Clock,
    private val zone: TimeZone,
    // UGC compliance §3 — advisory description filter. Noop default keeps existing tests green; the
    // Koin binding passes the real on-device port + active-language tag explicitly.
    private val textModeration: TextModerationPort = TextModerationPort { _, _ -> TextModerationVerdict.Clean },
    private val languageTag: Flow<String> = flowOf("en"),
    private val analytics: AnalyticsPort = NoopAnalyticsTracker,
) : MviViewModel<ComposePlateState, ComposePlateIntent, ComposePlateEffect>(ComposePlateState()) {

    // Content fingerprint of the last photo we kicked a classification off for.
    // Guards against re-classifying the same plate on every draft re-emission /
    // screen re-entry while still re-running when the user re-captures.
    private var lastClassifiedFingerprint: Int? = null

    // Taken slots per crew across ALL the author's crews. The disabled set shown in the
    // picker is the intersection over the *selected* crews: a slot is "used up" only when
    // it's already posted in every crew the plate would go to. Loaded once the crew set is known.
    private var perCrewTaken: Map<CrewId, Set<MealSlot>> = emptyMap()
    // Auto-pick the default slot (avoiding taken) only on first load; later audience
    // changes recompute the disabled set but don't yank a slot the user explicitly chose,
    // unless that slot has since become taken everywhere.
    private var slotInitialized = false

    init {
        analytics.track(AnalyticsEvent.MealComposerOpened)
        repository.observeDraft().onEach { draft ->
            update {
                it.copy(
                    photoBytes = draft?.plate?.photoBytes,
                    coordinates = draft?.coordinates,
                    draftIngredients = draft?.ingredients ?: emptyList(),
                    detectedIngredients = draft?.detectedIngredients ?: emptyList(),
                    selectedCrewIds = draft?.audienceCrewIds ?: emptySet(),
                    canContinue = computeCanContinue(
                        dish = it.dish,
                        descriptionTooLong = it.descriptionTooLong,
                        descriptionFlagged = it.descriptionWarning,
                        dishFlagged = it.dishWarning,
                        photo = draft?.plate?.photoBytes,
                        slot = it.selectedSlot,
                        taken = it.takenSlots,
                        audience = draft?.audienceCrewIds ?: emptySet(),
                    ),
                )
            }
        }.launchIn(viewModelScope)
        viewModelScope.launch { loadCrewsAndSlots() }
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
            val startedAt = clock.now()
            when (val r = classifyPlate(bytes)) {
                is Result.Ok -> {
                    // Advisory telemetry: only when the classifier actually ran (kill-switch off
                    // yields an empty version). detected≠confirmed, so `detected_count` here is the
                    // model's raw suggestion count, before the user edits it in the picker.
                    if (r.value.version.isNotEmpty()) {
                        analytics.track(
                            AnalyticsEvent.PlateClassified(
                                detectedCount = r.value.ingredients.size,
                                latencyMs = (clock.now() - startedAt).inWholeMilliseconds,
                                classifierVersion = r.value.version,
                            ),
                        )
                    }
                    // Detected ≠ confirmed: stamp ONLY the detected set. The confirmed
                    // `draftIngredients` stays driven by observeDraft and remains empty
                    // until the user confirms in the picker — detections are just its seed.
                    updateDraft(UpdateMealDraftCommand.SetDetected(r.value.ingredients, r.value.dishSlug, r.value.version))
                    update {
                        it.copy(
                            classifying = false,
                            detectedIngredients = r.value.ingredients,
                        )
                    }
                }
                is Result.Err -> update { it.copy(classifying = false, classifierError = r.error) }
            }
        }
    }

    /**
     * Loads the author's crews (the audience options), reconciles the selected audience
     * against them, and loads per-crew taken slots. The crew list is observed live so a
     * crew joined/left mid-compose is reflected.
     */
    private suspend fun loadCrewsAndSlots() {
        val authorId = repository.observeDraft().first()?.authorId ?: return
        crewMembership.observeMyCrews(authorId).onEach { crews ->
            update { it.copy(availableCrews = crews) }
            // Reconcile the chosen audience with the live crew set: drop crews the author
            // left and, if that empties it (or the draft never seeded one), default to all.
            val availableIds = crews.map { it.id }.toSet()
            val current = currentState.selectedCrewIds
            val effective = current.intersect(availableIds).ifEmpty { availableIds }
            if (effective != current) {
                updateDraft(UpdateMealDraftCommand.SetAudience(effective))
                update { it.copy(selectedCrewIds = effective) }
            }
            perCrewTaken = when (val r = repository.takenSlotsPerCrew(availableIds, MealDay.today(clock, zone))) {
                is Result.Ok  -> r.value
                is Result.Err -> emptyMap()
            }
            recomputeTaken()
        }.launchIn(viewModelScope)
    }

    /** Recomputes the disabled-slot set from the current audience and refreshes the selected slot. */
    private suspend fun recomputeTaken() {
        val selected = currentState.selectedCrewIds
        val taken: Set<MealSlot> =
            if (selected.isEmpty()) emptySet()
            else selected.map { perCrewTaken[it] ?: emptySet() }.reduce { acc, s -> acc intersect s }

        val slot = if (!slotInitialized) {
            slotInitialized = true
            val default = MealSlot.defaultForHour(clock.now().toLocalDateTime(zone).hour)
            if (default !in taken) default else MealSlot.entries.firstOrNull { it !in taken } ?: default
        } else {
            val cur = currentState.selectedSlot
            if (cur !in taken) cur else MealSlot.entries.firstOrNull { it !in taken } ?: cur
        }

        update {
            it.copy(
                takenSlots = taken,
                selectedSlot = slot,
                canContinue = computeCanContinue(
                    it.dish, it.descriptionTooLong, it.descriptionWarning, it.dishWarning,
                    it.photoBytes, slot, taken, it.selectedCrewIds,
                ),
            )
        }
        updateDraft(UpdateMealDraftCommand.SetSlot(slot))
    }

    override suspend fun handle(intent: ComposePlateIntent) {
        when (intent) {
            is ComposePlateIntent.DishChanged -> {
                // UGC §3 HARD-BLOCK: screen the dish title and block publish if objectionable.
                val dishFlagged = textModeration.evaluate(intent.value, languageTag.first()) is
                    TextModerationVerdict.Objectionable
                update {
                    it.copy(
                        dish = intent.value,
                        dishWarning = dishFlagged,
                        error = null,
                        canContinue = computeCanContinue(
                            intent.value, it.descriptionTooLong, it.descriptionWarning, dishFlagged,
                            it.photoBytes, it.selectedSlot, it.takenSlots, it.selectedCrewIds,
                        ),
                    )
                }
            }
            is ComposePlateIntent.DescriptionChanged -> {
                val tooLong = intent.value.trim().length > Description.MAX_LEN
                // UGC §3 HARD-BLOCK: screen the description and block publish if objectionable.
                val warning = textModeration.evaluate(intent.value, languageTag.first()) is
                    TextModerationVerdict.Objectionable
                update {
                    it.copy(
                        descriptionInput = intent.value,
                        descriptionTooLong = tooLong,
                        descriptionWarning = warning,
                        error = if (tooLong) MealError.Validation.DescriptionTooLong else null,
                        canContinue = computeCanContinue(it.dish, tooLong, warning, it.dishWarning, it.photoBytes, it.selectedSlot, it.takenSlots, it.selectedCrewIds),
                    )
                }
            }
            is ComposePlateIntent.SelectSlot -> {
                update {
                    it.copy(
                        selectedSlot = intent.slot,
                        canContinue = computeCanContinue(
                            it.dish, it.descriptionTooLong, it.descriptionWarning, it.dishWarning,
                            it.photoBytes, intent.slot, it.takenSlots, it.selectedCrewIds,
                        ),
                    )
                }
                updateDraft(UpdateMealDraftCommand.SetSlot(intent.slot))
            }
            is ComposePlateIntent.CrewToggled -> {
                val sel = currentState.selectedCrewIds
                val next = when {
                    intent.crewId in sel && sel.size > 1 -> sel - intent.crewId
                    intent.crewId in sel                 -> sel // keep at least one crew selected
                    else                                 -> sel + intent.crewId
                }
                if (next != sel) {
                    updateDraft(UpdateMealDraftCommand.SetAudience(next))
                    update { it.copy(selectedCrewIds = next) }
                    recomputeTaken()
                }
            }
            ComposePlateIntent.AllCrewsSelected -> {
                val all = currentState.availableCrews.map { it.id }.toSet()
                if (all.isNotEmpty() && all != currentState.selectedCrewIds) {
                    updateDraft(UpdateMealDraftCommand.SetAudience(all))
                    update { it.copy(selectedCrewIds = all) }
                    recomputeTaken()
                }
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
     *
     * Defense-in-depth (M1): re-evaluates the text moderation verdict on dish + description before
     * returning true, regardless of [canContinue] state. This prevents a future [canContinue]
     * recompute race from opening a publish hole — the coordinator will never receive a draft whose
     * text is Objectionable because [persistDraft] refuses to write it.
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
        if (state.selectedCrewIds.isEmpty()) {
            update { it.copy(error = MealError.Publish.NoCrewSelected) }
            return false
        }
        // Re-assert moderation verdict as defense-in-depth: canContinue already blocks the button,
        // but a subsequent canContinue recompute (e.g. audience change) could clear the flag before
        // the confirm dialog fires. Re-running here guarantees objectionable text can never reach
        // the upload coordinator, even if the button were somehow enabled.
        val lang = languageTag.first()
        if (textModeration.evaluate(dish.value, lang) is TextModerationVerdict.Objectionable) {
            update { it.copy(dishWarning = true, canContinue = false) }
            return false
        }
        if (textModeration.evaluate(state.descriptionInput, lang) is TextModerationVerdict.Objectionable) {
            update { it.copy(descriptionWarning = true, canContinue = false) }
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
        descriptionFlagged: Boolean,
        dishFlagged: Boolean,
        photo: ByteArray?,
        slot: MealSlot,
        taken: Set<MealSlot>,
        audience: Set<CrewId>,
    ): Boolean = dish.isNotBlank() &&
        !descriptionTooLong &&
        !descriptionFlagged &&
        !dishFlagged &&
        photo != null &&
        slot !in taken &&
        audience.isNotEmpty()
}
