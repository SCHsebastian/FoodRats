package es.schsebastian.foodrats.core.designsystem

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.atoms.FrSparkline
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrSparklineTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sparkline_composesWithData() {
        composeTestRule.setContent {
            FoodRatsTheme { FrSparkline(data = listOf(7.4f, 8f, 8.6f, 7f, 9.2f)) }
        }
        composeTestRule.onRoot().assertExists()
    }

    @Test
    fun sparkline_composesWithEmptyData() {
        composeTestRule.setContent {
            FoodRatsTheme { FrSparkline(data = emptyList()) }
        }
        composeTestRule.onRoot().assertExists()
    }

    @Test
    fun sparkline_composesWithSinglePoint() {
        composeTestRule.setContent {
            FoodRatsTheme { FrSparkline(data = listOf(5f)) }
        }
        composeTestRule.onRoot().assertExists()
    }
}
