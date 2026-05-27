package es.schsebastian.foodrats.core.designsystem

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.molecules.FrVoteBars
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrVoteBarsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun voteBars_rendersOneLabelPerScore() {
        composeTestRule.setContent {
            FoodRatsTheme { FrVoteBars(votes = mapOf(8 to 3, 9 to 1)) }
        }
        composeTestRule.onNodeWithText("1").assertExists()
        composeTestRule.onNodeWithText("8").assertExists()
        composeTestRule.onNodeWithText("10").assertExists()
    }
}
