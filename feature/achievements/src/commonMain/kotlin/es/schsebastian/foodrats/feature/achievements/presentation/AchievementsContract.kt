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
) : MviState

sealed interface AchievementsIntent : MviIntent {
    data class SelectBadge(val id: AchievementId) : AchievementsIntent
    data object DismissDetail : AchievementsIntent
    data object DismissError : AchievementsIntent
}

sealed interface AchievementsEffect : MviEffect {
    /** Drives the unlock celebration overlay at the screen (spec §8.3). */
    data class Unlocked(val titleKey: AchievementStringKey) : AchievementsEffect
}
