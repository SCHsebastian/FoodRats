package es.schsebastian.foodrats.feature.feed.domain.usecase

import es.schsebastian.foodrats.core.domain.account.BlockedAccountsPort
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.feed.domain.error.FeedError
import es.schsebastian.foodrats.feature.feed.domain.model.FeedDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ObserveFeedUseCase(
    private val activeCrew: ActiveCrewProvider,
    private val mealRead: MealReadPort,
    private val session: SessionProvider,
    private val blockedAccounts: BlockedAccountsPort,
) {
    /**
     * Live set of accounts the signed-in viewer has blocked (UGC compliance §5). Derived from the
     * session (no parallel state); emits `emptySet()` while signed-out or with nothing blocked so the
     * filter is a no-op rather than hiding everything.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val blockedFlow: Flow<Set<AccountId>> =
        session.current
            .map { it?.accountId }
            .distinctUntilChanged()
            .flatMapLatest { viewer ->
                if (viewer == null) flowOf(emptySet()) else blockedAccounts.observeBlocked(viewer)
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(day: Flow<FeedDay>): Flow<Result<List<MealWithRatings>, FeedError>> =
        combine(activeCrew.current, day, blockedFlow) { crewId, d, blocked -> Triple(crewId, d, blocked) }
            .flatMapLatest { (crewId, d, blocked) ->
                if (crewId == null) {
                    flowOf(Result.failure(FeedError.Session.NoActiveCrew))
                } else {
                    mealRead.observeFeed(crewId, d.day).map { r ->
                        when (r) {
                            is Result.Ok  -> Result.success(
                                // Blocked authors' meals vanish reactively — block updates re-emit
                                // blockedFlow, which re-runs this combine (UGC compliance §5).
                                r.value.filterNot { it.meal.author.accountId in blocked },
                            )
                            is Result.Err -> Result.failure(r.error.toFeedError())
                        }
                    }
                }
            }
}

private fun MealReadError.toFeedError(): FeedError.Read = when (this) {
    MealReadError.Unauthorized -> FeedError.Read.Unauthorized
    MealReadError.CrewNotFound -> FeedError.Read.CrewNotFound
    MealReadError.Unavailable  -> FeedError.Read.Unavailable
}
