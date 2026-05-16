package es.schsebastian.foodrats.core.designsystem

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class FrButtonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun button_displaysLabel() {
        composeTestRule.setContent {
            FoodRatsTheme {
                FrButton(label = "Save Meal", onClick = {})
            }
        }
        composeTestRule.onNodeWithText("Save Meal").assertExists()
    }

    @Test
    fun button_firesOnClick_whenEnabled() {
        var clickCount = 0
        composeTestRule.setContent {
            FoodRatsTheme {
                FrButton(label = "Submit", onClick = { clickCount++ })
            }
        }
        composeTestRule.onNodeWithText("Submit").performClick()
        assertEquals(1, clickCount, "onClick should fire exactly once on tap")
    }

    @Test
    fun button_doesNotFireOnClick_whenDisabled() {
        var clickCount = 0
        composeTestRule.setContent {
            FoodRatsTheme {
                FrButton(label = "Submit", onClick = { clickCount++ }, enabled = false)
            }
        }
        composeTestRule.onNodeWithText("Submit").assertIsNotEnabled()
        assertEquals(0, clickCount, "onClick should not fire for a disabled button")
    }

    @Test
    fun button_isEnabled_byDefault() {
        composeTestRule.setContent {
            FoodRatsTheme {
                FrButton(label = "Post", onClick = {})
            }
        }
        composeTestRule.onNodeWithText("Post").assertIsEnabled()
    }

    @Test
    fun secondaryVariant_displaysLabel() {
        composeTestRule.setContent {
            FoodRatsTheme {
                FrButton(label = "Cancel", onClick = {}, variant = FrButtonVariant.Secondary)
            }
        }
        composeTestRule.onNodeWithText("Cancel").assertExists()
    }

    @Test
    fun ghostVariant_displaysLabel() {
        composeTestRule.setContent {
            FoodRatsTheme {
                FrButton(label = "Skip", onClick = {}, variant = FrButtonVariant.Ghost)
            }
        }
        composeTestRule.onNodeWithText("Skip").assertExists()
    }
}
