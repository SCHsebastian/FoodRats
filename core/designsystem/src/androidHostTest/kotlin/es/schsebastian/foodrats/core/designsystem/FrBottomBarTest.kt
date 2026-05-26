package es.schsebastian.foodrats.core.designsystem

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.molecules.FrBottomBar
import es.schsebastian.foodrats.core.designsystem.molecules.FrBottomBarCapture
import es.schsebastian.foodrats.core.designsystem.molecules.FrBottomBarItem
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class FrBottomBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val tabs = listOf(
        FrBottomBarItem(FrIcons.Home, "Feed", "Feed tab"),
        FrBottomBarItem(FrIcons.Stats, "Stats", "Stats tab"),
    )

    @Test
    fun bottomBar_tab_firesOnSelectWithIndex() {
        var selected = -1
        composeTestRule.setContent {
            FoodRatsTheme {
                FrBottomBar(tabs = tabs, selectedIndex = 0, onSelect = { selected = it })
            }
        }
        composeTestRule.onNodeWithText("Stats").performClick()
        assertEquals(1, selected, "tapping the second tab should report index 1")
    }

    @Test
    fun bottomBar_capture_firesOnClick() {
        var captured = 0
        composeTestRule.setContent {
            FoodRatsTheme {
                FrBottomBar(
                    tabs = tabs,
                    selectedIndex = 0,
                    onSelect = {},
                    capture = FrBottomBarCapture(FrIcons.Camera, "Capture meal", onClick = { captured++ }),
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Capture meal").performClick()
        assertEquals(1, captured, "tapping the capture button should fire its onClick")
    }
}
