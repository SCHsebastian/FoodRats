package es.schsebastian.foodrats.feature.feed.presentation.feed

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealUploadProgressPort
import es.schsebastian.foodrats.core.domain.meal.MealUploadStatus
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrElse
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.feed.domain.model.FeedDay
import es.schsebastian.foodrats.feature.feed.domain.usecase.ObserveFeedUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.RateMealUseCase
import es.schsebastian.foodrats.feature.feed.presentation.components.toFeedUi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import es.schsebastian.foodrats.core.domain.meal.MealDay as DomainMealDay

class FeedViewModel(
    observeFeed: ObserveFeedUseCase,
    private val rateMeal: RateMealUseCase,
    private val activeCrew: ActiveCrewProvider,
    private val session: SessionProvider,
    private val clock: Clock,
    private val zone: TimeZone,
    uploadProgress: MealUploadProgressPort,
) : MviViewModel<FeedState, FeedIntent, FeedEffect>(
    FeedState(
        day = FeedDay.today(clock.now().toLocalDateTime(zone).date, zone),
        today = clock.now().toLocalDateTime(zone).date,
        canGoPrev = true,
    ),
) {

    private val today = clock.now().toLocalDateTime(zone).date

    init {
        viewModelScope.launch {
            observeFeed(
                state.map { it.day }
                    .filterNotNull()
                    .distinctUntilChanged(),
            ).collect { r ->
                when (r) {
                    is Result.Ok -> {
                        val viewerId = session.current.first()?.accountId
                        val todayMealDay = DomainMealDay.today(clock, zone)
                        val uis = if (viewerId == null) emptyList()
                                  else r.value.map { it.toFeedUi(viewerId, todayMealDay) }
                        update { it.copy(isLoading = false, meals = uis, error = null) }
                    }
                    is Result.Err -> update { it.copy(isLoading = false, error = r.error) }
                }
            }
        }
        uploadProgress.status
            .map { it is MealUploadStatus.Uploading }
            .distinctUntilChanged()
            .onEach { active -> update { it.copy(isUploadActive = active) } }
            .launchIn(viewModelScope)
    }

    override suspend fun handle(intent: FeedIntent) = when (intent) {
        FeedIntent.PrevDay      -> { navigatePrev(); Unit }
        FeedIntent.NextDay      -> { navigateNext(); Unit }
        FeedIntent.DismissError -> update { it.copy(error = null, rateError = null) }
        is FeedIntent.RateMeal  -> rate(intent.mealId, intent.score)
    }

    private suspend fun rate(mealIdRaw: String, scoreRaw: Int) {
        val crewId = activeCrew.current.first() ?: return
        val raterId = session.current.first()?.accountId ?: return
        val mealId = MealId.of(mealIdRaw).getOrElse { return }
        val score = Score.of(scoreRaw).getOrElse { return }
        update { it.copy(pendingRateMealId = mealIdRaw, rateError = null) }
        val r = rateMeal(crewId, mealId, raterId, score)
        update {
            when (r) {
                is Result.Ok  -> it.copy(pendingRateMealId = null)
                is Result.Err -> it.copy(pendingRateMealId = null, rateError = r.error)
            }
        }
    }

    private fun navigatePrev() {
        val currentDay = currentState.day ?: return
        val candidate = currentDay.previous()
        if (!FeedDay.isWithinWindow(candidate.day.date, today)) {
            update { it.copy(canGoPrev = false) }
            return
        }
        update { it.copy(
            day = candidate, isLoading = true,
            canGoNext = candidate.day.date < today,
            canGoPrev = FeedDay.isWithinWindow(candidate.previous().day.date, today),
        ) }
    }

    private fun navigateNext() {
        val currentDay = currentState.day ?: return
        val candidate = currentDay.next()
        if (candidate.day.date > today) { update { it.copy(canGoNext = false) }; return }
        update { it.copy(
            day = candidate, isLoading = true,
            canGoNext = candidate.day.date < today,
            canGoPrev = FeedDay.isWithinWindow(candidate.previous().day.date, today),
        ) }
    }
}
