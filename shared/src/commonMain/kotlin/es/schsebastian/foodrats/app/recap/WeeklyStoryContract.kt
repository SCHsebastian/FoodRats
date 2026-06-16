package es.schsebastian.foodrats.app.recap

import es.schsebastian.foodrats.core.domain.analytics.DigestStorySource
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState

/**
 * Single source of truth for the weekly-recap story player. [recap] is null until the underlying
 * stats/achievements reads emit; [currentIndex] is the active scene; [isPaused] freezes the
 * auto-advance clock (press-and-hold); [isLoading] gates the initial spinner; [failed] is true when
 * the stats read errored (e.g. no active crew) so the player can dismiss gracefully.
 */
data class WeeklyStoryState(
    val recap: WeeklyRecap? = null,
    val currentIndex: Int = 0,
    val isPaused: Boolean = false,
    val isLoading: Boolean = true,
    val failed: Boolean = false,
    /** True while the current scene's share card is rasterizing (decode + render); shows a spinner. */
    val isPreparingShare: Boolean = false,
    /** Transient share-outcome toast; cleared via [WeeklyStoryIntent.DismissShareOutcome]. */
    val shareOutcome: ShareOutcomeUi? = null,
) : MviState {
    val currentScene: RecapScene? get() = recap?.scenes?.getOrNull(currentIndex)
    val sceneCount: Int get() = recap?.sceneCount ?: 0
    val isLastScene: Boolean get() = recap != null && currentIndex >= recap.sceneCount - 1

    /** Whether the active scene can be shared as a story card (drives the in-scene share CTA). */
    val canShareCurrentScene: Boolean get() = currentScene?.isShareable() == true
}

/** Presentation mirror of `StoryShareOutcome` → which toast the player shows (spec §10). */
enum class ShareOutcomeUi { Succeeded, OpenedSheet, Failed }

sealed interface WeeklyStoryIntent : MviIntent {
    /** Tap the right region, or the auto-advance timer elapsed. */
    data object Advance : WeeklyStoryIntent

    /** Tap the left region. */
    data object Back : WeeklyStoryIntent

    /** Press-and-hold began — pause the auto-advance clock. */
    data object Pause : WeeklyStoryIntent

    /** Hold released — resume the auto-advance clock. */
    data object Resume : WeeklyStoryIntent

    /** The close (X) affordance, or back-press. */
    data object Close : WeeklyStoryIntent

    /** Share the current scene as a story card (only the shareable scenes show the CTA). */
    data object ShareScene : WeeklyStoryIntent

    /** Dismiss the transient share-outcome toast. */
    data object DismissShareOutcome : WeeklyStoryIntent
}

sealed interface WeeklyStoryEffect : MviEffect {
    /** Leave the player (advancing past the last scene, or an explicit close). */
    data object Dismiss : WeeklyStoryEffect
}

/** Marks how the player was opened, for the open-analytics event. */
typealias StoryOpenSource = DigestStorySource
