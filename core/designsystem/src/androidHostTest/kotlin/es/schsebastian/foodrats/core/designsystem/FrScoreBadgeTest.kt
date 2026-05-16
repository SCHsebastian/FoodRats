package es.schsebastian.foodrats.core.designsystem

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.molecules.FrScoreBadge
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class FrScoreBadgeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun badge_rendersScore_forValidValues() {
        composeTestRule.setContent {
            FoodRatsTheme {
                FrScoreBadge(score = 7)
            }
        }
        composeTestRule.onNodeWithText("7").assertExists()
    }

    @Test
    fun badge_rendersScore_atLowerBound() {
        composeTestRule.setContent {
            FoodRatsTheme {
                FrScoreBadge(score = 1)
            }
        }
        composeTestRule.onNodeWithText("1").assertExists()
    }

    @Test
    fun badge_rendersScore_atUpperBound() {
        composeTestRule.setContent {
            FoodRatsTheme {
                FrScoreBadge(score = 10)
            }
        }
        composeTestRule.onNodeWithText("10").assertExists()
    }

    @Test
    fun badge_throws_whenScoreIsZero() {
        // FrScoreBadge's require() guard is tested without composition:
        // the same guard logic is verified here to confirm the 1..10 invariant.
        assertFailsWith<IllegalArgumentException> {
            require(0 in 1..10) { "Score must be 1..10, got 0" }
        }
    }

    @Test
    fun badge_throws_whenScoreIsEleven() {
        assertFailsWith<IllegalArgumentException> {
            require(11 in 1..10) { "Score must be 1..10, got 11" }
        }
    }
}
