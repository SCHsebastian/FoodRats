package es.schsebastian.foodrats.feature.meal.presentation.capture

import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.CaptureSource
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
import es.schsebastian.foodrats.core.domain.crew.CrewMembershipPort
import es.schsebastian.foodrats.core.domain.location.Coordinates
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.PlateSource
import es.schsebastian.foodrats.core.domain.preferences.DefaultAudiencePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.core.presentation.photopicker.PhotoMetadata
import es.schsebastian.foodrats.core.presentation.photopicker.PhotoSource
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.model.Plate
import es.schsebastian.foodrats.feature.meal.i18n.MealStringKey
import es.schsebastian.foodrats.feature.meal.domain.usecase.StartMealDraftUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftCommand
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftUseCase
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

class CaptureMealViewModel(
    private val startDraft: StartMealDraftUseCase,
    private val updateDraft: UpdateMealDraftUseCase,
    private val sessionProvider: SessionProvider,
    private val crewMembership: CrewMembershipPort,
    // Noop default (null) keeps existing tests green; the Koin binding passes the real port.
    private val defaultAudience: DefaultAudiencePort? = null,
    // Device zone for interpreting an EXIF capture timestamp (gallery prefill only). Defaulted so
    // existing tests that don't care about prefill keep compiling; the Koin binding passes the
    // app-wide zone (the same one StartMealDraftUseCase/ComposePlateViewModel use).
    private val zone: TimeZone = TimeZone.UTC,
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
                // Default the audience to the crew the user is currently viewing — the composer is
                // launched from a crew's feed, so a meal should land where they're looking. That active
                // crew is a far stronger signal than any persisted preference (the prior "last-used
                // default" silently posted to whatever crew was used last, even from another crew's feed).
                // Fall back to the saved default (intersected with current memberships), then ALL crews,
                // when there's no active crew or it's no longer a membership.
                val audienceCrewIds = session.activeCrewId
                    ?.takeIf { it in allCrewIds }
                    ?.let { setOf(it) }
                    ?: run {
                        val savedDefault = defaultAudience?.defaultAudience?.first()
                        savedDefault
                            ?.intersect(allCrewIds)
                            ?.takeIf { it.isNotEmpty() }
                            ?: allCrewIds
                    }
                update { it.copy(error = null) }
                startDraft(session.accountId, audienceCrewIds).also { result ->
                    if (result is Result.Err) update { it.copy(error = MealStringKey.CaptureDraftFailed) }
                    else analytics.track(AnalyticsEvent.MealCaptureStarted(CaptureSource.UNKNOWN))
                }
            }
            is CaptureMealIntent.PhotoTaken -> {
                // Each intent runs in its own coroutine (MviViewModel.onIntent launches per
                // intent), so a duplicate PhotoTaken dispatched while the first photo is still
                // being persisted would interleave at the suspension point below and double-save
                // the draft / double-emit NavigateToCompose. Drop re-entries while capturing.
                if (currentState.isCapturing) return
                update { it.copy(isCapturing = true, error = null) }
                val plateSource = intent.source.toPlateSource()
                val r = updateDraft(UpdateMealDraftCommand.SetPhoto(Plate(intent.bytes, source = plateSource)))
                if (r is Result.Ok && plateSource == PlateSource.Gallery) {
                    // Best-effort EXIF prefill: a captured timestamp suggests a MealSlot, GPS
                    // suggests Coordinates — only for fields the user hasn't already set. Camera
                    // picks carry no metadata (live shot, nothing to prefill from). Never surfaces
                    // an error and never blocks the NavigateToCompose effect below.
                    applyExifPrefill(r.value, intent.metadata)
                }
                update { it.copy(isCapturing = false) }
                if (r is Result.Ok) emit(CaptureMealEffect.NavigateToCompose)
                else if (r is Result.Err) update { it.copy(error = MealStringKey.CapturePhotoFailed) }
            }
            CaptureMealIntent.OpenSettings -> emit(CaptureMealEffect.OpenAppSettings)
        }
    }

    private fun PhotoSource.toPlateSource(): PlateSource = when (this) {
        PhotoSource.Camera -> PlateSource.Camera
        PhotoSource.Gallery -> PlateSource.Gallery
    }

    /**
     * Applies the EXIF-derived slot/coordinates suggestions to [draft] (already carrying the fresh
     * photo). Both are gated on the corresponding field being unset, so a value the user already
     * chose is never clobbered. Every failure mode (missing metadata, an unparseable timestamp, an
     * out-of-range coordinate pair, a `saveDraft` error) is swallowed — this is advisory prefill,
     * never a blocking step.
     */
    private suspend fun applyExifPrefill(draft: MealDraft, metadata: PhotoMetadata?) {
        if (metadata == null) return
        if (draft.slot == null) {
            metadata.takenAtEpochMs?.let { epochMs ->
                runCatching { Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone).hour }
                    .getOrNull()
                    ?.let { hour -> updateDraft(UpdateMealDraftCommand.SetSlot(MealSlot.forHour(hour))) }
            }
        }
        if (draft.coordinates == null) {
            val lat = metadata.latitude
            val lon = metadata.longitude
            if (lat != null && lon != null) {
                (Coordinates.of(lat, lon) as? Result.Ok)?.value?.let { coords ->
                    updateDraft(UpdateMealDraftCommand.SetCoordinates(coords))
                }
            }
        }
    }
}
