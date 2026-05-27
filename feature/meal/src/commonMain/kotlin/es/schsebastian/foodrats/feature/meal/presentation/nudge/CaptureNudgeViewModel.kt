package es.schsebastian.foodrats.feature.meal.presentation.nudge

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

data class CaptureNudgeState(
    /**
     * Whether the signed-in account already has a meal in the active crew's feed today.
     * Defaults to `true` so the capture nudge ring stays hidden while we're still resolving
     * (or when there's no active crew) — better to under-nag than to flash a spurious ring.
     */
    val hasPostedToday: Boolean = true,
) : MviState

sealed interface CaptureNudgeIntent : MviIntent
sealed interface CaptureNudgeEffect : MviEffect

/**
 * Feeds the capture-button "post today" nudge ring in the Main bottom bar. Reactively derives
 * whether the user has posted today from [MealReadPort.observeFeed], so the ring clears the
 * moment their meal lands in the feed. Lives in :feature:meal (the posting bounded context),
 * mirroring how :feature:auth's `TopBarAvatarViewModel` feeds the same scaffold.
 *
 * Best-effort: a read error or "no active crew" reports `hasPostedToday = true` (no nag).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CaptureNudgeViewModel(
    mealRead: MealReadPort,
    activeCrew: ActiveCrewProvider,
    session: SessionProvider,
    clock: Clock,
    zone: TimeZone,
) : MviViewModel<CaptureNudgeState, CaptureNudgeIntent, CaptureNudgeEffect>(CaptureNudgeState()) {

    init {
        val accountIdFlow = session.current.map { it?.accountId }.distinctUntilChanged()
        viewModelScope.launch {
            combine(accountIdFlow, activeCrew.current) { accountId, crewId -> accountId to crewId }
                .flatMapLatest { (accountId, crewId) ->
                    if (accountId == null || crewId == null) {
                        flowOf(true)
                    } else {
                        mealRead.observeFeed(crewId, MealDay.today(clock, zone)).map { result ->
                            when (result) {
                                is Result.Ok -> result.value.any { it.meal.author.accountId == accountId }
                                is Result.Err -> true
                            }
                        }
                    }
                }
                .distinctUntilChanged()
                .onEach { posted -> update { it.copy(hasPostedToday = posted) } }
                .collect {}
        }
    }

    override suspend fun handle(intent: CaptureNudgeIntent) = Unit
}
