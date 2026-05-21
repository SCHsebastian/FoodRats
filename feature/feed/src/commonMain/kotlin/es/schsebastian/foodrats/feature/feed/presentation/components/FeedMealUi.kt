package es.schsebastian.foodrats.feature.feed.presentation.components

import es.schsebastian.foodrats.core.domain.meal.DailyEmote
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.model.AccountId

data class RaterVoteUi(
    val raterName: String,
    val raterAvatarUrl: String?,
    val score: Int,
)

data class FeedMealUi(
    val mealId: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val photoUrl: String,
    val dishName: String,
    val description: String,
    val publishedAtEpochMs: Long,
    val dayEmote: String,
    val averageScore: Double?,
    val ratingCount: Int,
    val votes: List<RaterVoteUi>,
    val viewerRating: Int?,
    val canRate: Boolean,
)

fun MealWithRatings.toFeedUi(viewerId: AccountId, today: MealDay): FeedMealUi {
    val viewer = ratingBy(viewerId)
    val isAuthor = meal.author.accountId == viewerId
    val daysSince = today.daysSince(meal.day)
    val windowOpen = daysSince in 0..1
    return FeedMealUi(
        mealId = meal.id.value,
        authorId = meal.author.accountId.value,
        authorName = meal.author.displayName,
        authorAvatarUrl = meal.author.avatarUrl,
        photoUrl = meal.photoUrl,
        dishName = meal.dish.value,
        description = meal.description.value,
        publishedAtEpochMs = meal.publishedAt.toEpochMilliseconds(),
        dayEmote = DailyEmote.forDay(meal.day),
        averageScore = averageScore,
        ratingCount = ratingCount,
        votes = ratings
            .sortedByDescending { it.ratedAt.toEpochMilliseconds() }
            .map { RaterVoteUi(it.raterDisplayName, it.raterAvatarUrl, it.score.value) },
        viewerRating = viewer?.score?.value,
        canRate = !isAuthor && viewer == null && windowOpen,
    )
}
