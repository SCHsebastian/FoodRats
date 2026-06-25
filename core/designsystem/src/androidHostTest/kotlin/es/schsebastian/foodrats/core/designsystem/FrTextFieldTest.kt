package es.schsebastian.foodrats.core.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * Locks the editing-buffer contract of [FrTextField] (see its KDoc): the cursor stays where the
 * user is typing across the hoisted-state round-trip, and only genuine external value changes
 * replace the field's text. The regression these guard is "typing runs backwards" with a
 * predictive on-screen keyboard.
 */
@RunWith(AndroidJUnit4::class)
class FrTextFieldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun typingCharByChar_throughHoistedState_isNotReversed() {
        var current by mutableStateOf("")
        composeTestRule.setContent {
            FoodRatsTheme {
                FrTextField(value = current, onValueChange = { current = it })
            }
        }
        val field = composeTestRule.onNode(hasSetTextAction())
        // Each insert appends at the cursor. If an echo reset the cursor to 0 (the bug), these would
        // accumulate in reverse ("aloh") instead of in order.
        "hola".forEach { c ->
            field.performTextInput(c.toString())
            composeTestRule.waitForIdle()
        }
        assertEquals("hola", current)
    }

    @Test
    fun insertingMidString_respectsCursor() {
        var current by mutableStateOf("ac")
        composeTestRule.setContent {
            FoodRatsTheme {
                FrTextField(value = current, onValueChange = { current = it })
            }
        }
        val field = composeTestRule.onNode(hasSetTextAction())
        field.performTextInputSelection(TextRange(1)) // between 'a' and 'c'
        field.performTextInput("b")
        composeTestRule.waitForIdle()
        assertEquals("abc", current)
    }

    @Test
    fun externalClear_resetsTheField() {
        var current by mutableStateOf("draft text")
        composeTestRule.setContent {
            FoodRatsTheme {
                FrTextField(value = current, onValueChange = { current = it })
            }
        }
        composeTestRule.onNodeWithText("draft text").assertExists()
        // e.g. clear-on-send: the hoisted value is reset externally.
        current = ""
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("draft text").assertDoesNotExist()
    }

    @Test
    fun externalPrefill_isAdopted() {
        var current by mutableStateOf("")
        composeTestRule.setContent {
            FoodRatsTheme {
                FrTextField(value = current, onValueChange = { current = it })
            }
        }
        current = "Madrid" // e.g. profile loaded after first composition
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Madrid").assertExists()
    }
}
