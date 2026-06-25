package es.schsebastian.foodrats.feature.stats.domain.model

import es.schsebastian.foodrats.core.domain.crew.CrewScoreStyle
import es.schsebastian.foodrats.core.domain.cuisine.CuisinePassport
import es.schsebastian.foodrats.core.domain.meal.IngredientBingo
import es.schsebastian.foodrats.feature.stats.domain.error.StatsError

data class StatsSnapshot(
    val hero: HeroStats,
    val week: WindowStats,
    val month: WindowStats,
    val historic: WindowStats?,
    /**
     * Non-null when the Historic tab's 365-day read failed (the rest of the snapshot is still valid).
     * The current-window read failing surfaces as a top-level `Result.Err` instead; this is the
     * historic-only failure channel so a swallowed Historic error no longer leaves the tab spinning.
     */
    val historicError: StatsError? = null,
    /**
     * The signed-in member's cuisine passport (roadmap §2.2): every catalog cuisine as a collected
     * or locked cell, derived from the cuisines STAMPED on the member's OWN confirmed meals over the
     * loaded window. `null` only until the cuisine catalog has emitted (renders nothing then).
     */
    val cuisinePassport: CuisinePassport? = null,
    /**
     * The signed-in member's ingredient bingo (roadmap §2.3): every catalog ingredient as a collected
     * or locked Pokédex cell, derived from the CONFIRMED `Meal.ingredients` of the member's OWN meals
     * over the loaded window (AI `detectedIngredients` are excluded). `null` only until the ingredient
     * catalog has emitted (renders nothing then).
     */
    val ingredientBingo: IngredientBingo? = null,
    /**
     * The active crew's chosen Score display vocabulary (C8b). Defaults to [CrewScoreStyle.Stars]
     * for pre-C8 crews (no stored field) so the leaderboard renders identically to before C8b when
     * no style has been set. Plumbed from [CrewWelcomePort.observeScoreStyle] via
     * [ObserveStatsUseCase] — presentation maps it to [FrScoreStyle] when reducing into [StatsState].
     */
    val scoreStyle: CrewScoreStyle = CrewScoreStyle.Stars,
)
