package es.schsebastian.foodrats.feature.feed.presentation.components

import es.schsebastian.foodrats.core.domain.meal.DailyEmote
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import kotlinx.datetime.toLocalDateTime

data class RaterVoteUi(
    val raterName: String,
    val raterAvatarUrl: String?,
    val score: Int,
)

/** Presentation mirror of [MealSlot] so the row never imports a domain type. */
enum class MealSlotUi {
    Breakfast, Lunch, Dinner;

    fun labelKey(): FeedStringKey = when (this) {
        Breakfast -> FeedStringKey.SlotBreakfast
        Lunch -> FeedStringKey.SlotLunch
        Dinner -> FeedStringKey.SlotDinner
    }
}

private fun MealSlot.toUi(): MealSlotUi = when (this) {
    MealSlot.Breakfast -> MealSlotUi.Breakfast
    MealSlot.Lunch -> MealSlotUi.Lunch
    MealSlot.Dinner -> MealSlotUi.Dinner
}

data class FeedMealUi(
    val mealId: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val photoUrl: String,
    val dishName: String,
    val description: String,
    val slot: MealSlotUi,
    val publishedAtEpochMs: Long,
    /** Local hour-of-day (0..23) the meal was published, in the feed's zone. */
    val publishedHour: Int,
    /** Local minute-of-hour (0..59) the meal was published, in the feed's zone. */
    val publishedMinute: Int,
    val dayEmote: String,
    val averageScore: Double?,
    val ratingCount: Int,
    val votes: List<RaterVoteUi>,
    val viewerRating: Int?,
    val canRate: Boolean,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Resolved, localized ingredient display names. Empty unless resolved by the caller. */
    val ingredients: List<String> = emptyList(),
)

fun MealWithRatings.toFeedUi(
    viewerId: AccountId,
    today: MealDay,
    ingredientNames: List<String> = emptyList(),
): FeedMealUi {
    val viewer = ratingBy(viewerId)
    val isAuthor = meal.author.accountId == viewerId
    val daysSince = today.daysSince(meal.day)
    val windowOpen = daysSince in 0..1
    val publishedLocal = meal.publishedAt.toLocalDateTime(today.zone)
    return FeedMealUi(
        mealId = meal.id.value,
        authorId = meal.author.accountId.value,
        authorName = meal.author.displayName,
        authorAvatarUrl = meal.author.avatarUrl,
        photoUrl = meal.photoUrl,
        dishName = meal.dish.value,
        description = meal.description.value,
        slot = meal.slot.toUi(),
        publishedAtEpochMs = meal.publishedAt.toEpochMilliseconds(),
        publishedHour = publishedLocal.hour,
        publishedMinute = publishedLocal.minute,
        dayEmote = DailyEmote.forDay(meal.day),
        averageScore = averageScore,
        ratingCount = ratingCount,
        votes = ratings
            .sortedByDescending { it.ratedAt.toEpochMilliseconds() }
            .map { RaterVoteUi(it.raterDisplayName, it.raterAvatarUrl, it.score.value) },
        viewerRating = viewer?.score?.value,
        canRate = !isAuthor && viewer == null && windowOpen,
        latitude = meal.coordinates?.latitude,
        longitude = meal.coordinates?.longitude,
        ingredients = ingredientNames,
    )
}
