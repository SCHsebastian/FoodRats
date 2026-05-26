package es.schsebastian.foodrats.core.designsystem

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.atoms.FrCard
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class FrCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun card_rendersContent() {
        composeTestRule.setContent {
            FoodRatsTheme {
                FrCard { FrText(text = "Card body") }
            }
        }
        composeTestRule.onNodeWithText("Card body").assertExists()
    }

    @Test
    fun card_firesOnClick_whenInteractive() {
        var clicked = 0
        composeTestRule.setContent {
            FoodRatsTheme {
                FrCard(onClick = { clicked++ }) { FrText(text = "Tap me") }
            }
        }
        composeTestRule.onNodeWithText("Tap me").performClick()
        assertEquals(1, clicked, "onClick should fire once when the card is tapped")
    }
}
