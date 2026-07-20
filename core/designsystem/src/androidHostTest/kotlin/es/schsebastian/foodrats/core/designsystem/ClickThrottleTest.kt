package es.schsebastian.foodrats.core.designsystem

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.atoms.ClickThrottleWindow
import es.schsebastian.foodrats.core.designsystem.atoms.rememberThrottledClick
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClickThrottleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tapsWithinWindow_fireOnce() {
        val timeSource = TestTimeSource()
        var clickCount = 0
        composeTestRule.setContent {
            Button(onClick = rememberThrottledClick({ clickCount++ }, timeSource)) { Text("Go") }
        }
        composeTestRule.onNodeWithText("Go").performClick()
        timeSource += ClickThrottleWindow - 1.milliseconds
        composeTestRule.onNodeWithText("Go").performClick()
        assertEquals(1, clickCount, "a re-tap inside the window should be dropped")
    }

    @Test
    fun tapAfterWindow_firesAgain() {
        val timeSource = TestTimeSource()
        var clickCount = 0
        composeTestRule.setContent {
            Button(onClick = rememberThrottledClick({ clickCount++ }, timeSource)) { Text("Go") }
        }
        composeTestRule.onNodeWithText("Go").performClick()
        timeSource += ClickThrottleWindow
        composeTestRule.onNodeWithText("Go").performClick()
        assertEquals(2, clickCount, "a tap after the window elapses should fire again")
    }
}
