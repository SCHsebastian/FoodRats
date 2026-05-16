package es.schsebastian.foodrats.core.designsystem

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.atoms.FrChip
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class FrChipTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun chip_displaysLabel() {
        composeTestRule.setContent {
            FoodRatsTheme {
                FrChip(label = "Pizza", onClick = {})
            }
        }
        composeTestRule.onNodeWithText("Pizza").assertExists()
    }

    @Test
    fun chip_firesOnClick_whenEnabled() {
        var clicked = 0
        composeTestRule.setContent {
            FoodRatsTheme {
                FrChip(label = "Sushi", onClick = { clicked++ })
            }
        }
        composeTestRule.onNodeWithText("Sushi").performClick()
        assertEquals(1, clicked, "onClick should fire once when chip is tapped")
    }

    @Test
    fun chip_isEnabled_byDefault() {
        composeTestRule.setContent {
            FoodRatsTheme {
                FrChip(label = "Tacos", onClick = {})
            }
        }
        composeTestRule.onNodeWithText("Tacos").assertIsEnabled()
    }

    @Test
    fun chip_isDisabled_whenEnabledFalse() {
        composeTestRule.setContent {
            FoodRatsTheme {
                FrChip(label = "Ramen", onClick = {}, enabled = false)
            }
        }
        composeTestRule.onNodeWithText("Ramen").assertIsNotEnabled()
    }

    @Test
    fun chip_selected_andUnselected_bothRenderLabel() {
        composeTestRule.setContent {
            FoodRatsTheme {
                FrChip(label = "Burger", onClick = {}, selected = true)
                FrChip(label = "Fries", onClick = {}, selected = false)
            }
        }
        // Both chips must be present regardless of selected state.
        composeTestRule.onNodeWithText("Burger").assertExists()
        composeTestRule.onNodeWithText("Fries").assertExists()
    }
}
