package es.schsebastian.foodrats.feature.feed.presentation.detail

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealRatingPort
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrElse
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.feed.domain.model.FeedDay
import es.schsebastian.foodrats.feature.feed.domain.usecase.ObserveFeedUseCase
import es.schsebastian.foodrats.feature.feed.presentation.components.toFeedUi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class MealDetailViewModel(
    private val mealId: String,
    private val dayIso: String,
    observeFeed: ObserveFeedUseCase,
    private val ratingPort: MealRatingPort,
    private val activeCrew: ActiveCrewProvider,
    private val session: SessionProvider,
    private val clock: Clock,
    private val zone: TimeZone,
) : MviViewModel<MealDetailState, MealDetailIntent, MealDetailEffect>(MealDetailState()) {

    init {
        val parsedDay = runCatching { LocalDate.parse(dayIso) }.getOrNull()
        if (parsedDay == null) {
            update { it.copy(isLoading = false, notFound = true) }
        } else {
            val feedDay = FeedDay(MealDay(parsedDay, zone))
            viewModelScope.launch {
                observeFeed(flowOf(feedDay)).collect { r ->
                    when (r) {
                        is Result.Ok -> {
                            val viewerId = session.current.first()?.accountId
                            val todayMealDay = MealDay.today(clock, zone)
                            val match = if (viewerId == null) null
                                        else r.value.firstOrNull { it.meal.id.value == mealId }
                                            ?.toFeedUi(viewerId, todayMealDay)
                            update {
                                it.copy(
                                    isLoading = false,
                                    meal = match,
                                    notFound = match == null,
                                    error = null,
                                )
                            }
                        }
                        is Result.Err -> update {
                            it.copy(isLoading = false, error = r.error)
                        }
                    }
                }
            }
        }
    }

    override suspend fun handle(intent: MealDetailIntent) = when (intent) {
        is MealDetailIntent.RateMeal     -> rateMeal(intent.score)
        MealDetailIntent.DismissError    -> update { it.copy(error = null, rateError = null) }
    }

    private suspend fun rateMeal(scoreRaw: Int) {
        val crewId = activeCrew.current.first() ?: return
        val parsedMealId = MealId.of(mealId).getOrElse { return }
        val score = Score.of(scoreRaw).getOrElse { return }
        update { it.copy(pendingRate = true, rateError = null) }
        val r = ratingPort.rate(crewId, parsedMealId, score)
        update {
            when (r) {
                is Result.Ok  -> it.copy(pendingRate = false)
                is Result.Err -> it.copy(pendingRate = false, rateError = r.error)
            }
        }
    }
}
