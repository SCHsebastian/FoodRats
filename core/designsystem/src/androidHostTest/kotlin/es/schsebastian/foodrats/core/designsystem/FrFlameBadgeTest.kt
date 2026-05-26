package es.schsebastian.foodrats.core.designsystem

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.atoms.FrFlameBadge
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrFlameBadgeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun flameBadge_showsDayCount() {
        composeTestRule.setContent {
            FoodRatsTheme { FrFlameBadge(days = 7) }
        }
        composeTestRule.onNodeWithText("7").assertExists()
    }

    @Test
    fun flameBadge_rendersNothing_whenDaysZero() {
        composeTestRule.setContent {
            FoodRatsTheme { FrFlameBadge(days = 0) }
        }
        composeTestRule.onAllNodesWithText("0").assertCountEquals(0)
    }
}
