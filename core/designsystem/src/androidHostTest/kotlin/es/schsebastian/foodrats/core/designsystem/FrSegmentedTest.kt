package es.schsebastian.foodrats.core.designsystem

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.molecules.FrSegmented
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class FrSegmentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun segmented_rendersAllOptions() {
        composeTestRule.setContent {
            FoodRatsTheme {
                FrSegmented(options = listOf("Day", "Week", "Month"), selectedIndex = 0, onSelect = {})
            }
        }
        composeTestRule.onNodeWithText("Day").assertExists()
        composeTestRule.onNodeWithText("Week").assertExists()
        composeTestRule.onNodeWithText("Month").assertExists()
    }

    @Test
    fun segmented_firesOnSelectWithIndex() {
        var selected = -1
        composeTestRule.setContent {
            FoodRatsTheme {
                FrSegmented(options = listOf("Day", "Week", "Month"), selectedIndex = 0, onSelect = { selected = it })
            }
        }
        composeTestRule.onNodeWithText("Month").performClick()
        assertEquals(2, selected, "tapping the third segment should report index 2")
    }
}
