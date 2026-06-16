package es.schsebastian.foodrats.feature.stats.presentation.components

import es.schsebastian.foodrats.core.domain.meal.DailyEmote
import es.schsebastian.foodrats.feature.stats.domain.model.HeroStats
import es.schsebastian.foodrats.feature.stats.domain.model.MealAward

/**
 * Render-ready props for `FrAwardShareCard`, mapped from a [MealAward] in the FEATURE presentation
 * layer (the design-system card can't see domain types — spec §8.1). The ViewModel decodes
 * [photoUrl] off-screen before the renderer composes the card; [scoreLabel] (via
 * `FeedStringKey.RatingSummary` equivalent) is resolved at the call site and passed through, and the
 * `awardLabel` chrome is resolved in the share-content composable.
 */
data class AwardShareCardModel(
    val mealId: String,
    val photoUrl: String,
    val dishName: String,
    val authorName: String,
    val score: Double,
    val ratingCount: Int,
    val dayEmote: String,
)

/** Maps the window's best/most-voted plate award → award share-card props. */
fun MealAward.toAwardCard(): AwardShareCardModel = AwardShareCardModel(
    mealId = mealId.value,
    photoUrl = photoUrl,
    dishName = dish.value,
    authorName = author.displayName,
    score = score,
    ratingCount = ratingCount,
    dayEmote = DailyEmote.forDay(day),
)

/**
 * Render-ready props for `FrStreakShareCard`, mapped from [HeroStats] (spec §8.1). No photo — the
 * streak count is the hero. [dayEmote] uses the today motif so the card is consistent with the rest
 * of the brand. The headline/subline chrome is resolved in the share-content composable.
 */
data class StreakShareCardModel(
    val streakDays: Int,
    val dayEmote: String,
)

/**
 * Maps the member's personal streak → streak share-card props. [todayEmote] is the day motif
 * resolved by the caller (`DailyEmote.forDay(today)`). A `Streak(0)` still maps to a valid (if
 * unexciting) model.
 */
fun HeroStats.toStreakCard(todayEmote: String): StreakShareCardModel = StreakShareCardModel(
    streakDays = personalStreak.days,
    dayEmote = todayEmote,
)
