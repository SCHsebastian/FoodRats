package es.schsebastian.foodrats.feature.stats.presentation.stats

import es.schsebastian.foodrats.core.designsystem.molecules.FrScoreStyle
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.stats.domain.error.StatsError
import es.schsebastian.foodrats.feature.stats.domain.model.StatsSnapshot
import es.schsebastian.foodrats.feature.stats.domain.model.Tab

data class StatsState(
    val selectedTab: Tab = Tab.Week,
    val snapshot: StatsSnapshot? = null,
    val historicLoading: Boolean = false,
    val historicError: StatsError? = null,
    val error: StatsError? = null,
    val isRefreshing: Boolean = false,
    val epoch: Int = 0,
    val isUploadActive: Boolean = false,
    /** True while a share card (award or streak) is rasterizing; shows a spinner on the button. */
    val isPreparingShare: Boolean = false,
    /** Transient share-outcome toast; cleared via [StatsIntent.DismissShareOutcome] (spec §10). */
    val shareOutcome: ShareOutcomeUi? = null,
    /**
     * Active crew's chosen Score display vocabulary (C8b). Defaults to [FrScoreStyle.Stars] for
     * pre-C8 crews. Drives the leaderboard award cards so they render Stars/Emoji/Numeric to match
     * the feed and meal-detail screens. Mapped from [StatsSnapshot.scoreStyle] on each emission.
     */
    val scoreStyle: FrScoreStyle = FrScoreStyle.Stars,
) : MviState

/** Presentation mirror of `StoryShareOutcome` → which toast the screen shows (spec §10). */
enum class ShareOutcomeUi { Succeeded, OpenedSheet, Failed }

sealed interface StatsIntent : MviIntent {
    data class SelectTab(val tab: Tab) : StatsIntent
    data object Refresh : StatsIntent
    data object DismissError : StatsIntent

    /** Share an award plate (best meal) to Instagram Stories; [mealId] is the award's meal id. */
    data class ShareAwardTapped(val mealId: String) : StatsIntent

    /** Share the member's personal streak to Instagram Stories. */
    data object ShareStreakTapped : StatsIntent
    data object DismissShareOutcome : StatsIntent
}

sealed interface StatsEffect : MviEffect
