package es.schsebastian.foodrats.feature.feed.presentation.components

import es.schsebastian.foodrats.core.domain.meal.DailyEmote
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.crew.BlindVotingPolicy
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
    /** Signed URL of the FULL plate image — used by the detail screen. */
    val photoUrl: String,
    /**
     * Signed URL of the lightweight server thumbnail — used by feed cards (smaller + faster).
     * Empty when the pipeline hasn't produced one yet; callers fall back to [photoUrl] via
     * [feedImageUrl].
     */
    val thumbnailUrl: String = "",
    /**
     * Base64 ThumbHash (the instant blur placeholder), or null until the server pipeline writes it.
     * Decoded to a placeholder bitmap in the row/detail composables.
     */
    val thumbHash: String? = null,
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
    /**
     * Blind-voting: when `true` the viewer must not yet see who cooked this meal, so the row
     * renders a placeholder name + generic avatar instead of [authorName]/[authorAvatarUrl].
     * Decided by [BlindVotingPolicy.shouldMaskAuthor] in [toFeedUi]; the placeholder label is
     * resolved in the row (i18n stays in the composable, like [slot]).
     */
    val authorMasked: Boolean = false,
    /**
     * Reactions (the daily-emote react) on this meal. [reactionCount] is the badge number and
     * [viewerReacted] highlights the react button as toggled-on. The displayed glyph is [dayEmote]
     * (the meal-day's [DailyEmote], identical for every crew member). Defaults are the "no
     * reactions observed yet" state; the ViewModel merges live data via [withReactions].
     */
    val reactionCount: Int = 0,
    val viewerReacted: Boolean = false,
) {
    /** Returns a copy carrying the latest observed reaction read-model values. */
    fun withReactions(count: Int, viewerReacted: Boolean): FeedMealUi =
        copy(reactionCount = count, viewerReacted = viewerReacted)

    /**
     * The URL a FEED card should load: the lightweight thumbnail when the pipeline has produced
     * one, otherwise the full plate (pre-pipeline meals + the few-seconds window before the
     * thumbnail exists). The detail screen always loads [photoUrl] (full resolution).
     */
    val feedImageUrl: String
        get() = thumbnailUrl.ifBlank { photoUrl }
}

/**
 * Render-ready props for [es.schsebastian.foodrats.core.designsystem.templates.FrPlateShareCard],
 * mapped from a [FeedMealUi] in the FEATURE presentation layer (the design-system card can't see
 * domain types — spec §8.1). The ViewModel decodes [photoUrl] into an `ImageBitmap` (off-screen, via
 * `PlateImageDecoder`) before the renderer composes the card; [dayEmote] is already the per-day
 * brand motif. [scoreLabel] is built at the call site via `FeedStringKey.RatingSummary` (or null
 * when there are no ratings) and passed straight through.
 */
data class PlateShareCardModel(
    val mealId: String,
    val photoUrl: String,
    val dishName: String,
    val authorName: String,
    val scoreLabel: String?,
    val dayEmote: String,
)

/** Maps this feed row → the plate share-card props. [scoreLabel] is resolved by the caller. */
fun FeedMealUi.toPlateCard(scoreLabel: String?): PlateShareCardModel = PlateShareCardModel(
    mealId = mealId,
    photoUrl = photoUrl,
    dishName = dishName,
    authorName = authorName,
    scoreLabel = scoreLabel,
    dayEmote = dayEmote,
)

fun MealWithRatings.toFeedUi(
    viewerId: AccountId,
    today: MealDay,
    ingredientNames: List<String> = emptyList(),
    blindVoting: Boolean = false,
): FeedMealUi {
    val viewer = ratingBy(viewerId)
    val isAuthor = meal.author.accountId == viewerId
    val daysSince = today.daysSince(meal.day)
    val windowOpen = daysSince in 0..1
    val publishedLocal = meal.publishedAt.toLocalDateTime(today.zone)
    // Reveal after the viewer rates OR the rating window closes; the pure policy only takes the
    // three inputs, so window-close reveal is gated here by treating a closed window as "voted".
    val viewerHasVoted = viewer != null || !windowOpen
    val authorMasked = BlindVotingPolicy.shouldMaskAuthor(
        blindVoting = blindVoting,
        isAuthor = isAuthor,
        viewerHasVoted = viewerHasVoted,
    )
    return FeedMealUi(
        mealId = meal.id.value,
        authorId = meal.author.accountId.value,
        authorName = meal.author.displayName,
        authorAvatarUrl = meal.author.avatarUrl,
        photoUrl = meal.photoUrl,
        thumbnailUrl = meal.thumbnailUrl,
        thumbHash = meal.thumbHash,
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
        authorMasked = authorMasked,
    )
}
