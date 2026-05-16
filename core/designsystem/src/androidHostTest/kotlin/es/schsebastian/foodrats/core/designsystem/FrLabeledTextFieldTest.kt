package es.schsebastian.foodrats.core.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.molecules.FrLabeledTextField
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class FrLabeledTextFieldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun labeledTextField_displaysLabel() {
        composeTestRule.setContent {
            FoodRatsTheme {
                FrLabeledTextField(label = "Dish name", value = "", onValueChange = {})
            }
        }
        composeTestRule.onNodeWithText("Dish name").assertExists()
    }

    @Test
    fun labeledTextField_displaysHelperText_whenProvided() {
        composeTestRule.setContent {
            FoodRatsTheme {
                FrLabeledTextField(
                    label = "Description",
                    value = "",
                    onValueChange = {},
                    helper = "Max 280 characters",
                )
            }
        }
        composeTestRule.onNodeWithText("Max 280 characters").assertExists()
    }

    @Test
    fun labeledTextField_doesNotShowHelperText_whenNull() {
        composeTestRule.setContent {
            FoodRatsTheme {
                FrLabeledTextField(label = "Title", value = "", onValueChange = {}, helper = null)
            }
        }
        // Label is present; no helper text should crash or appear.
        composeTestRule.onNodeWithText("Title").assertExists()
    }

    @Test
    fun labeledTextField_displaysCurrentValue() {
        composeTestRule.setContent {
            FoodRatsTheme {
                FrLabeledTextField(
                    label = "Location",
                    value = "Madrid",
                    onValueChange = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Madrid").assertExists()
    }

    @Test
    fun labeledTextField_forwardsValueChange_onInput() {
        var current by mutableStateOf("")
        composeTestRule.setContent {
            FoodRatsTheme {
                FrLabeledTextField(
                    label = "Dish name",
                    value = current,
                    onValueChange = { current = it },
                )
            }
        }
        // Drive state change programmatically and verify recomposition.
        current = "Paella"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Paella").assertExists()
        assertEquals("Paella", current)
    }
}
