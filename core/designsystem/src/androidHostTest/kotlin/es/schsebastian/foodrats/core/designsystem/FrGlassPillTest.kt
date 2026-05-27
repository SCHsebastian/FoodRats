package es.schsebastian.foodrats.core.designsystem

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.atoms.FrGlassPill
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class FrGlassPillTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun glassPill_firesOnClick() {
        var clicked = 0
        composeTestRule.setContent {
            FoodRatsTheme {
                FrGlassPill(icon = FrIcons.Back, onClick = { clicked++ }, contentDescription = "Back")
            }
        }
        composeTestRule.onNodeWithContentDescription("Back").assertIsEnabled().performClick()
        assertEquals(1, clicked, "onClick should fire once when the pill is tapped")
    }
}
