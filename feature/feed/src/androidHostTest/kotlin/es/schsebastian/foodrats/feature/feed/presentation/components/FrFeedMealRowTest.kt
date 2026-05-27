package es.schsebastian.foodrats.feature.feed.presentation.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrFeedMealRowTest {
    @get:Rule val rule = createComposeRule()

    private fun ui(
        averageScore: Double? = 7.4,
        ratingCount: Int = 12,
        viewerRating: Int? = 8,
    ) = FeedMealUi(
        mealId = "m1",
        authorId = "u1",
        authorName = "Author",
        authorAvatarUrl = null,
        photoUrl = "",
        dishName = "Pasta",
        description = "",
        slot = MealSlotUi.Lunch,
        publishedAtEpochMs = 0L,
        publishedHour = 12,
        publishedMinute = 5,
        dayEmote = "🍝",
        averageScore = averageScore,
        ratingCount = ratingCount,
        votes = emptyList(),
        viewerRating = viewerRating,
        canRate = false,
    )

    @Test fun renders_dish_author_slot_time_and_score_summary() {
        rule.setContent { FoodRatsTheme { FrFeedMealRow(ui = ui(), onClick = {}) } }
        rule.onNodeWithText("Pasta").assertIsDisplayed()
        rule.onNodeWithText("Author").assertIsDisplayed()
        rule.onNodeWithText("Lunch").assertIsDisplayed()           // feed_slot_lunch
        rule.onNodeWithText("12:05").assertIsDisplayed()           // feed_time_of_day
        rule.onNodeWithText("7.4 ★ · 12 votes").assertIsDisplayed() // feed_rating_summary_votes
    }

    @Test fun renders_your_vote_line_when_viewer_rated() {
        rule.setContent { FoodRatsTheme { FrFeedMealRow(ui = ui(viewerRating = 9), onClick = {}) } }
        rule.onNodeWithText("Your vote: 9 ★").assertIsDisplayed() // feed_your_vote
    }

    @Test fun renders_no_votes_yet_when_unrated() {
        rule.setContent {
            FoodRatsTheme {
                FrFeedMealRow(ui = ui(averageScore = null, ratingCount = 0, viewerRating = null), onClick = {})
            }
        }
        rule.onNodeWithText("No votes yet").assertIsDisplayed() // feed_no_votes_yet
    }
}
