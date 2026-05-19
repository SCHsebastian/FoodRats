package es.schsebastian.foodrats.feature.feed.presentation.components

import es.schsebastian.foodrats.core.domain.meal.DailyEmote
import es.schsebastian.foodrats.core.domain.meal.Meal

data class FeedMealUi(
    val id: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val photoUrl: String,
    val score: Int,
    val dishName: String,
    val tags: List<String>,
    val publishedAtEpochMs: Long,
    val dayEmote: String,
)

fun Meal.toFeedUi(): FeedMealUi = FeedMealUi(
    id = id.value,
    authorName = author.displayName,
    authorAvatarUrl = author.avatarUrl,
    photoUrl = photoUrl,
    score = score.value,
    dishName = dish.value,
    tags = tags.map { it.label },
    publishedAtEpochMs = publishedAt.toEpochMilliseconds(),
    dayEmote = DailyEmote.forDay(day),
)
