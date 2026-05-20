package es.schsebastian.foodrats.core.designsystem

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.atoms.FrFilterChip
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class FrFilterChipTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun filterChip_announces_selectedState() {
        composeTestRule.setContent {
            FoodRatsTheme {
                FrFilterChip(label = "Burger", selected = true, onClick = {})
                FrFilterChip(label = "Fries", selected = false, onClick = {})
            }
        }
        composeTestRule.onNodeWithText("Burger").assertIsSelected()
        composeTestRule.onNodeWithText("Fries").assertIsNotSelected()
    }

    @Test
    fun filterChip_firesOnClick_whenEnabled() {
        var clicked = 0
        composeTestRule.setContent {
            FoodRatsTheme {
                FrFilterChip(label = "Sushi", selected = false, onClick = { clicked++ })
            }
        }
        composeTestRule.onNodeWithText("Sushi").performClick()
        assertEquals(1, clicked)
    }

    @Test
    fun filterChip_respectsEnabledFlag() {
        composeTestRule.setContent {
            FoodRatsTheme {
                FrFilterChip(label = "Tacos", selected = false, onClick = {})
                FrFilterChip(label = "Ramen", selected = false, onClick = {}, enabled = false)
            }
        }
        composeTestRule.onNodeWithText("Tacos").assertIsEnabled()
        composeTestRule.onNodeWithText("Ramen").assertIsNotEnabled()
    }
}
