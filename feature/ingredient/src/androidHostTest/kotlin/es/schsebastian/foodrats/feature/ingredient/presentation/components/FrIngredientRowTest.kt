package es.schsebastian.foodrats.feature.ingredient.presentation.components

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class FrIngredientRowTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun click_invokes_toggle() {
        var toggled = false
        rule.setContent {
            FoodRatsTheme {
                FrIngredientRow(
                    name = "Tomato",
                    iconKey = null,
                    selected = false,
                    enabled = true,
                    onToggle = { toggled = true },
                )
            }
        }
        rule.onNodeWithText("Tomato").assertHasClickAction().performClick()
        assertTrue(toggled)
    }

    @Test
    fun disabled_row_does_not_toggle() {
        var toggled = false
        rule.setContent {
            FoodRatsTheme {
                FrIngredientRow(
                    name = "Onion",
                    iconKey = null,
                    selected = false,
                    enabled = false,
                    onToggle = { toggled = true },
                )
            }
        }
        rule.onNodeWithText("Onion").assertIsDisplayed().performClick()
        assertFalse(toggled)
    }
}
