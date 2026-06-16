package es.schsebastian.foodrats.core.designsystem

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.templates.FrAwardShareCard
import es.schsebastian.foodrats.core.designsystem.templates.FrPlateShareCard
import es.schsebastian.foodrats.core.designsystem.templates.FrStreakShareCard
import es.schsebastian.foodrats.core.designsystem.templates.ShareCardFormat
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrShareCardTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun plate_card_renders_dish_author_score_and_brand() {
        rule.setContent {
            FoodRatsTheme {
                FrPlateShareCard(
                    plate = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).asImageBitmap(),
                    dishName = "Smoked brisket tacos",
                    authorName = "Sebastián",
                    scoreLabel = "8.4 ★ · 5",
                    dayEmote = "🌮",
                    footerBrand = "FoodRats",
                    format = ShareCardFormat.Story,
                )
            }
        }
        rule.onNodeWithText("Smoked brisket tacos", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Sebastián", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("8.4 ★ · 5", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("FoodRats", useUnmergedTree = true).assertExists()
    }

    @Test
    fun plate_card_without_photo_or_score_still_renders_chrome() {
        rule.setContent {
            FoodRatsTheme {
                FrPlateShareCard(
                    plate = null,
                    dishName = "Weeknight ragù",
                    authorName = "Anika",
                    scoreLabel = null,
                    dayEmote = "🍝",
                    footerBrand = "FoodRats",
                    format = ShareCardFormat.Square,
                )
            }
        }
        rule.onRoot().assertExists()
        rule.onNodeWithText("Weeknight ragù", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("FoodRats", useUnmergedTree = true).assertExists()
    }

    @Test
    fun award_card_renders_award_banner_and_dish() {
        rule.setContent {
            FoodRatsTheme {
                FrAwardShareCard(
                    plate = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).asImageBitmap(),
                    awardLabel = "Best meal",
                    dishName = "Charred miso aubergine",
                    authorName = "Reggie",
                    scoreLabel = "9.1 ★ · 6",
                    dayEmote = "🍆",
                    footerBrand = "FoodRats",
                    format = ShareCardFormat.Story,
                )
            }
        }
        rule.onNodeWithText("Best meal", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Charred miso aubergine", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Reggie", useUnmergedTree = true).assertExists()
    }

    @Test
    fun streak_card_renders_day_count_headline_and_subline() {
        rule.setContent {
            FoodRatsTheme {
                FrStreakShareCard(
                    streakDays = 14,
                    headline = "14-day streak 🔥",
                    subline = "Keep it cooking",
                    dayEmote = "🔥",
                    footerBrand = "FoodRats",
                    format = ShareCardFormat.Story,
                )
            }
        }
        rule.onNodeWithText("14", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("14-day streak 🔥", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Keep it cooking", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("FoodRats", useUnmergedTree = true).assertExists()
    }

    @Test
    fun streak_card_with_a_single_day_still_renders() {
        rule.setContent {
            FoodRatsTheme {
                FrStreakShareCard(
                    streakDays = 1,
                    headline = "1-day streak 🔥",
                    subline = "Keep it cooking",
                    dayEmote = "🔥",
                    footerBrand = "FoodRats",
                    format = ShareCardFormat.Square,
                )
            }
        }
        rule.onRoot().assertExists()
        rule.onNodeWithText("1", useUnmergedTree = true).assertExists()
    }
}
