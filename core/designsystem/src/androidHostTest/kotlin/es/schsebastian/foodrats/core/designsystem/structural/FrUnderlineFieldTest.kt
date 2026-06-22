package es.schsebastian.foodrats.core.designsystem.structural

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
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * Locks the editing-buffer contract of the state-based [FrUnderlineField] (see its KDoc). The
 * regression these guard is "typing runs backwards / earlier letters reappear" on a predictive
 * on-screen keyboard when the hoisted value echoes back a frame late through an MVI StateFlow.
 */
@RunWith(AndroidJUnit4::class)
class FrUnderlineFieldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun typingCharByChar_throughHoistedState_isNotReversed() {
        var current by mutableStateOf("")
        composeTestRule.setContent {
            FoodRatsTheme(darkTheme = true) {
                FrUnderlineField(value = current, onValueChange = { current = it })
            }
        }
        val field = composeTestRule.onNode(hasSetTextAction())
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
            FoodRatsTheme(darkTheme = true) {
                FrUnderlineField(value = current, onValueChange = { current = it })
            }
        }
        val field = composeTestRule.onNode(hasSetTextAction())
        field.performTextInputSelection(TextRange(1)) // between 'a' and 'c'
        field.performTextInput("b")
        composeTestRule.waitForIdle()
        assertEquals("abc", current)
    }

    @Test
    fun externalPrefill_whileUnfocused_isAdopted() {
        var current by mutableStateOf("")
        composeTestRule.setContent {
            FoodRatsTheme(darkTheme = true) {
                FrUnderlineField(value = current, onValueChange = { current = it })
            }
        }
        current = "Madrid" // e.g. profile loaded after first composition, field not yet focused
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Madrid").assertExists()
    }

    @Test
    fun externalClear_whileUnfocused_resetsTheField() {
        var current by mutableStateOf("draft text")
        composeTestRule.setContent {
            FoodRatsTheme(darkTheme = true) {
                FrUnderlineField(value = current, onValueChange = { current = it })
            }
        }
        composeTestRule.onNodeWithText("draft text").assertExists()
        current = "" // clear-on-send
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("draft text").assertDoesNotExist()
    }

    /**
     * The core fix: a STALE hoisted value delivered while the field is focused (the conflated-StateFlow
     * lagging echo) must NOT roll the on-screen buffer backward. The legacy controlled API did exactly
     * that; the state-based field gates external adoption on `!focused`.
     */
    @Test
    fun staleEcho_whileFocused_doesNotRollBackTheBuffer() {
        var current by mutableStateOf("")
        composeTestRule.setContent {
            FoodRatsTheme(darkTheme = true) {
                FrUnderlineField(value = current, onValueChange = { current = it })
            }
        }
        val field = composeTestRule.onNode(hasSetTextAction())
        field.performTextInput("hello") // focuses the field; buffer == "hello"
        composeTestRule.waitForIdle()

        // Simulate a lagging echo: the parent momentarily holds an older value while the field stays
        // focused. The buffer must keep "hello" rather than snap back to "he".
        current = "he"
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("hello").assertExists()
        composeTestRule.onNodeWithText("he").assertDoesNotExist()
    }
}
