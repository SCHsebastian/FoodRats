package es.schsebastian.foodrats.feature.feed.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** §12: `FeedMealUi.toPlateCard(scoreLabel)` maps fields straight through; null score preserved. */
class FeedMealUiShareTest {

    private fun feedMeal(averageScore: Double? = 8.4, ratingCount: Int = 5) = FeedMealUi(
        mealId = "meal-1",
        authorId = "u-author",
        authorName = "Chef Ana",
        authorAvatarUrl = "https://a/avatar.png",
        photoUrl = "https://signed/plate.jpg",
        dishName = "Lasagna",
        description = "best ever",
        slot = MealSlotUi.Lunch,
        publishedAtEpochMs = 0L,
        publishedHour = 12,
        publishedMinute = 0,
        dayEmote = "🔥",
        averageScore = averageScore,
        ratingCount = ratingCount,
        votes = emptyList(),
        viewerRating = null,
        canRate = false,
    )

    @Test fun maps_core_fields_and_score_label_straight_through() {
        val model = feedMeal().toPlateCard(scoreLabel = "8.4 ★ · 5")
        assertEquals("meal-1", model.mealId)
        assertEquals("https://signed/plate.jpg", model.photoUrl)
        assertEquals("Lasagna", model.dishName)
        assertEquals("Chef Ana", model.authorName)
        assertEquals("🔥", model.dayEmote)
        assertEquals("8.4 ★ · 5", model.scoreLabel)
    }

    @Test fun preserves_null_score_label_when_no_ratings() {
        val model = feedMeal(averageScore = null, ratingCount = 0).toPlateCard(scoreLabel = null)
        assertNull(model.scoreLabel)
    }
}
