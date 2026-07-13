package es.schsebastian.foodrats.feature.achievements.presentation

import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.achievements.domain.error.AchievementError
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementId
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementStatus
import es.schsebastian.foodrats.feature.achievements.i18n.AchievementStringKey

/**
 * Single source of truth for the achievements screen (spec §8.1). [statuses] hold every catalog row
 * with persisted unlock dates overlaid; the screen partitions them into earned (`unlockedAtEpochMs
 * != null`) and locked. [selected] backs the tapped-badge detail sheet.
 */
data class AchievementsState(
    val statuses: List<AchievementStatus> = emptyList(),
    val selected: AchievementStatus? = null,
    val error: AchievementError? = null,
    val isLoading: Boolean = true,
    /**
     * Non-null while the unlock celebration overlay is showing (spec §8.3).
     *
     * BUG FIX (2026-07-12): this used to be a one-shot [MviEffect] collected into a plain
     * Composable `remember` in `AchievementsScreen`, which the manifest's un-configured
     * `MainActivity` recreation (no `android:configChanges`) wiped on rotation — the underlying
     * effect had already been drained from its channel, so the reward feedback was lost for good.
     * Living in [AchievementsState] instead makes it survive recomposition/rotation like the rest
     * of MVI state (the same `ViewModelStoreOwner` holds the ViewModel across a config change);
     * cleared explicitly via [AchievementsIntent.DismissCelebration].
     */
    val celebration: AchievementStringKey? = null,
) : MviState

sealed interface AchievementsIntent : MviIntent {
    data class SelectBadge(val id: AchievementId) : AchievementsIntent
    data object DismissDetail : AchievementsIntent
    data object DismissError : AchievementsIntent
    /** Dismisses the unlock celebration overlay (see [AchievementsState.celebration]). */
    data object DismissCelebration : AchievementsIntent
}

/**
 * No leaves today — the unlock celebration moved to [AchievementsState.celebration] (see its kdoc
 * for why). Kept as the ViewModel's effect type parameter in case a genuinely one-shot,
 * non-restorable effect is needed later.
 */
sealed interface AchievementsEffect : MviEffect
