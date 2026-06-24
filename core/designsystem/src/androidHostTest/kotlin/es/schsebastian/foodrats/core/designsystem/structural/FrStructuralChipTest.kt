package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Locks the selectable-chip semantics (WCAG 4.1.2 / 1.4.1): a tappable chip mirrors its `selected`
 * state into semantics so TalkBack/VoiceOver announce "selected" instead of conveying the choice by
 * fill color alone. Regression guard for slot/audience pickers reading as plain unnamed buttons.
 */
@RunWith(AndroidJUnit4::class)
class FrStructuralChipTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun selectedChip_announcesSelectedState() {
        composeTestRule.setContent {
            FoodRatsTheme(darkTheme = true) {
                FrStructuralChip(label = "Lunch", selected = true, onClick = {})
            }
        }
        composeTestRule.onNode(isSelected()).assertExists()
    }

    @Test
    fun unselectedChip_announcesNotSelected() {
        composeTestRule.setContent {
            FoodRatsTheme(darkTheme = true) {
                FrStructuralChip(label = "Lunch", selected = false, onClick = {})
            }
        }
        composeTestRule.onNode(isSelected()).assertDoesNotExist()
    }

    @Test
    fun nonInteractiveChip_hasNoSelectionState() {
        composeTestRule.setContent {
            FoodRatsTheme(darkTheme = true) {
                // No onClick → a passive label chip (e.g. the "Analyzing…" overlay) carries no toggle role.
                FrStructuralChip(label = "Analyzing", selected = false)
            }
        }
        composeTestRule.onNode(isSelected()).assertDoesNotExist()
    }
}
