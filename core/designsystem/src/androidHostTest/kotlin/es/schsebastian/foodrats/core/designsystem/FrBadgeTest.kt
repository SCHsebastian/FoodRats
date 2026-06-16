package es.schsebastian.foodrats.core.designsystem

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.atoms.FrBadge
import es.schsebastian.foodrats.core.designsystem.atoms.FrBadgeTier
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrBadgeTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_title_and_caption_for_earned_badge() {
        rule.setContent {
            FoodRatsTheme {
                FrBadge(
                    icon = FrIcons.Trophy,
                    title = "First Plate",
                    earned = true,
                    progressFraction = 1f,
                    caption = "Earned 2026-05-04",
                )
            }
        }
        rule.onNodeWithText("First Plate", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Earned 2026-05-04", useUnmergedTree = true).assertExists()
    }

    @Test
    fun renders_progress_caption_for_locked_badge() {
        rule.setContent {
            FoodRatsTheme {
                FrBadge(
                    icon = FrIcons.Restaurant,
                    title = "Home Cook",
                    earned = false,
                    progressFraction = 0.6f,
                    tier = FrBadgeTier.Silver,
                    caption = "30 / 50",
                )
            }
        }
        rule.onNodeWithText("Home Cook", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("30 / 50", useUnmergedTree = true).assertExists()
    }

    @Test
    fun composes_without_caption() {
        rule.setContent {
            FoodRatsTheme {
                FrBadge(
                    icon = FrIcons.Moon,
                    title = "Night Owl",
                    earned = false,
                    progressFraction = 0f,
                )
            }
        }
        rule.onRoot().assertExists()
        rule.onNodeWithText("Night Owl", useUnmergedTree = true).assertExists()
    }
}
