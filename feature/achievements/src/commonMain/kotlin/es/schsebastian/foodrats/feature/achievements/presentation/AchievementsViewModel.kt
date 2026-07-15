package es.schsebastian.foodrats.feature.achievements.presentation

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.achievement.AchievementProgressPort
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementStatus
import es.schsebastian.foodrats.feature.achievements.domain.usecase.ObserveAchievementsUseCase
import kotlinx.coroutines.launch

/**
 * MVI ViewModel for the achievements screen (spec §8.2). Single source of truth: everything lives in
 * [AchievementsState], read via `currentState`, mutated via `update { it.copy(...) }` — no parallel
 * `MutableStateFlow` (the `FeedViewModel` rule).
 *
 * The ONLY side-effecting step is [persistAndCelebrate]: it writes newly-met unlocks exactly once,
 * and ONLY after the persist `Result` resolves `Ok` does it fire the `achievement_unlocked` analytics
 * event and set [AchievementsState.celebration] — never before persistence, never inside a use case
 * (CHARTER §9).
 *
 * [analytics] defaults to [NoopAnalyticsTracker] so existing-style tests stay green; the Koin module
 * passes the real port via an explicit `viewModel { … analytics = get() }`.
 */
class AchievementsViewModel(
    observeAchievements: ObserveAchievementsUseCase,
    private val progress: AchievementProgressPort,
    private val clock: Clock,
    private val analytics: AnalyticsPort = NoopAnalyticsTracker,
) : MviViewModel<AchievementsState, AchievementsIntent, AchievementsEffect>(AchievementsState()) {

    init {
        viewModelScope.launch {
            observeAchievements().collect { result ->
                when (result) {
                    is Result.Ok -> {
                        update { it.copy(statuses = result.value.statuses, error = null, isLoading = false) }
                        persistAndCelebrate(result.value.accountId, result.value.statuses, result.value.newlyUnlocked)
                    }
                    is Result.Err -> update { it.copy(error = result.error, isLoading = false) }
                }
            }
        }
    }

    /** Persist newly-met unlocks ONCE; celebrate + track only on `Ok`. */
    private suspend fun persistAndCelebrate(
        accountId: AccountId,
        statuses: List<AchievementStatus>,
        newlyUnlocked: Map<String, Long>,
    ) {
        if (newlyUnlocked.isEmpty()) return
        val newlyMet = statuses.filter { it.achievement.id.value in newlyUnlocked.keys }
        if (newlyMet.isEmpty()) return
        val now = clock.now().toEpochMilliseconds()
        val write = progress.recordUnlocks(accountId, newlyMet.associate { it.achievement.id.value to now })
        if (write is Result.Ok) {
            newlyMet.forEach { status ->
                analytics.track(AnalyticsEvent.AchievementUnlocked(status.achievement.id.value))
            }
            // BUG FIX (2026-07-12): celebration lives in state, not a one-shot effect — see
            // AchievementsState.celebration's kdoc. When several unlock in the same snapshot the last
            // one wins, matching the previous effect-channel behavior (the screen's collector
            // overwrote its local `celebration` var on every drained item with no display delay
            // between them, so only the final title was ever actually shown).
            newlyMet.lastOrNull()?.let { status ->
                update { it.copy(celebration = status.achievement.titleKey) }
            }
        }
        // on Err: leave them met-but-locked; the next snapshot retries the (idempotent) write.
    }

    override suspend fun handle(intent: AchievementsIntent) = when (intent) {
        is AchievementsIntent.SelectBadge ->
            update { s -> s.copy(selected = s.statuses.firstOrNull { it.achievement.id == intent.id }) }
        AchievementsIntent.DismissDetail -> update { it.copy(selected = null) }
        AchievementsIntent.DismissError -> update { it.copy(error = null) }
        AchievementsIntent.DismissCelebration -> update { it.copy(celebration = null) }
    }
}
