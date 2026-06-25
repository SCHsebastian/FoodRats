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
import es.schsebastian.foodrats.core.domain.meal.MealPublishPolicy
import es.schsebastian.foodrats.core.domain.meal.MealUploadCoordinator
import es.schsebastian.foodrats.core.domain.meal.MealValueObjectError
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.moderation.TextModerationPort
import es.schsebastian.foodrats.core.domain.moderation.TextModerationVerdict
import es.schsebastian.foodrats.core.domain.preferences.DefaultAudiencePort
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
    // Noop default (null) keeps existing tests green; the Koin binding passes the real port.
    private val defaultAudience: DefaultAudiencePort? = null,
    private val analytics: AnalyticsPort = NoopAnalyticsTracker,
) : MviViewModel<ComposePlateState, ComposePlateIntent, ComposePlateEffect>(ComposePlateState()) {

    // Content fingerprint of the last photo we kicked a classification off for.
    // Guards against re-classifying the same plate on every draft re-emission /
    // screen re-entry while still re-running when the user re-captures.
    private var lastClassifiedFingerprint: Int? = null

    // How many meals the author has already published per crew today. The composer's daily-cap
    // gate ([MealPublishPolicy.MAX_MEALS_PER_CREW_PER_DAY]) is reached only when *every* selected
    // crew is full. Loaded once the crew set is known.
    private var perCrewCount: Map<CrewId, Int> = emptyMap()

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
                    // Slot is optional; restore whatever the draft carries (null = none chosen).
                    selectedSlot = draft?.slot,
                    canContinue = computeCanContinue(
                        dish = it.dish,
                        dishTooLong = it.dishTooLong,
                        descriptionTooLong = it.descriptionTooLong,
                        descriptionFlagged = it.descriptionWarning,
                        dishFlagged = it.dishWarning,
                        photo = draft?.plate?.photoBytes,
                        audience = draft?.audienceCrewIds ?: emptySet(),
                        dailyLimitReached = it.dailyLimitReached,
                    ),
                )
            }
        }.launchIn(viewModelScope)
        viewModelScope.launch { loadCrewsAndCounts() }
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
     * against them, and loads per-crew meal counts for the daily cap. The crew list is observed
     * live so a crew joined/left mid-compose is reflected.
     */
    private suspend fun loadCrewsAndCounts() {
        val authorId = repository.observeDraft().first()?.authorId ?: return
        crewMembership.observeMyCrews(authorId).onEach { crews ->
            update { it.copy(availableCrews = crews) }
            // Reconcile the chosen audience with the live crew set: drop crews the author
            // left and, if that empties it (or the draft never seeded one), default to all.
            // Reconcile against the DRAFT's audience (the source of truth), NOT
            // currentState.selectedCrewIds: this collector races the init observeDraft collector,
            // and reading the transient state here can see the empty *initial* selection before the
            // draft has populated it — clobbering the seeded active-crew audience to "all crews".
            val availableIds = crews.map { it.id }.toSet()
            val draftAudience = repository.observeDraft().first()?.audienceCrewIds ?: emptySet()
            val effective = draftAudience.intersect(availableIds).ifEmpty { availableIds }
            if (effective != draftAudience) {
                updateDraft(UpdateMealDraftCommand.SetAudience(effective))
                update { it.copy(selectedCrewIds = effective) }
            }
            perCrewCount = when (val r = repository.mealCountsPerCrew(availableIds, MealDay.today(clock, zone))) {
                is Result.Ok  -> r.value
                is Result.Err -> emptyMap()
            }
            recomputeLimit()
        }.launchIn(viewModelScope)
    }

    /** Recomputes whether the selected audience is fully at the per-crew daily cap. */
    private fun recomputeLimit() {
        val selected = currentState.selectedCrewIds
        val limitReached = selected.isNotEmpty() && selected.all {
            (perCrewCount[it] ?: 0) >= MealPublishPolicy.MAX_MEALS_PER_CREW_PER_DAY
        }
        update {
            it.copy(
                dailyLimitReached = limitReached,
                canContinue = computeCanContinue(
                    it.dish, it.dishTooLong, it.descriptionTooLong, it.descriptionWarning, it.dishWarning,
                    it.photoBytes, it.selectedCrewIds, limitReached,
                ),
            )
        }
    }

    override suspend fun handle(intent: ComposePlateIntent) {
        when (intent) {
            is ComposePlateIntent.DishChanged -> {
                // Inline length feedback (mirrors the description path): show the right message and gate
                // Continue AS THE USER TYPES, instead of only rejecting an over-length title on submit.
                val dishTooLong = intent.value.trim().length > DishName.MAX_LEN
                // UGC §3 HARD-BLOCK: screen the dish title and block publish if objectionable.
                val dishFlagged = textModeration.evaluate(intent.value, languageTag.first()) is
                    TextModerationVerdict.Objectionable
                update {
                    it.copy(
                        dish = intent.value,
                        dishTooLong = dishTooLong,
                        dishWarning = dishFlagged,
                        // Keep a same-field error in front; don't clobber a still-valid description error.
                        error = when {
                            dishTooLong -> MealError.Validation.TooLong
                            it.descriptionTooLong -> MealError.Validation.DescriptionTooLong
                            else -> null
                        },
                        canContinue = computeCanContinue(
                            intent.value, dishTooLong, it.descriptionTooLong, it.descriptionWarning,
                            dishFlagged, it.photoBytes, it.selectedCrewIds, it.dailyLimitReached,
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
                        error = when {
                            tooLong -> MealError.Validation.DescriptionTooLong
                            it.dishTooLong -> MealError.Validation.TooLong
                            else -> null
                        },
                        canContinue = computeCanContinue(
                            it.dish, it.dishTooLong, tooLong, warning, it.dishWarning,
                            it.photoBytes, it.selectedCrewIds, it.dailyLimitReached,
                        ),
                    )
                }
            }
            is ComposePlateIntent.SelectSlot -> {
                // Toggle: tapping the selected slot again clears it (slot is optional).
                val next = if (currentState.selectedSlot == intent.slot) null else intent.slot
                update { it.copy(selectedSlot = next) }
                updateDraft(UpdateMealDraftCommand.SetSlot(next))
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
                    recomputeLimit()
                    // Fire-and-forget: persist the new selection as the default for the next
                    // compose session. Failures are silently ignored (best-effort preference).
                    defaultAudience?.set(next)
                }
            }
            ComposePlateIntent.AllCrewsSelected -> {
                val all = currentState.availableCrews.map { it.id }.toSet()
                if (all.isNotEmpty() && all != currentState.selectedCrewIds) {
                    updateDraft(UpdateMealDraftCommand.SetAudience(all))
                    update { it.copy(selectedCrewIds = all) }
                    recomputeLimit()
                    // Persist the "all crews" choice as the new default.
                    defaultAudience?.set(all)
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
        val dish = when (val r = DishName.of(state.dish)) {
            is Result.Ok -> r.value
            is Result.Err -> {
                // Map the value-object failure to the RIGHT message: a too-long title must not surface
                // as "Tell us what you ate." (Blank) — the original bug.
                val err = when (r.error) {
                    MealValueObjectError.DishNameTooLong -> MealError.Validation.TooLong
                    else -> MealError.Validation.Blank
                }
                update { it.copy(error = err) }
                return false
            }
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
        dishTooLong: Boolean,
        descriptionTooLong: Boolean,
        descriptionFlagged: Boolean,
        dishFlagged: Boolean,
        photo: ByteArray?,
        audience: Set<CrewId>,
        dailyLimitReached: Boolean,
    ): Boolean = dish.isNotBlank() &&
        !dishTooLong &&
        !descriptionTooLong &&
        !descriptionFlagged &&
        !dishFlagged &&
        photo != null &&
        audience.isNotEmpty() &&
        !dailyLimitReached
}
