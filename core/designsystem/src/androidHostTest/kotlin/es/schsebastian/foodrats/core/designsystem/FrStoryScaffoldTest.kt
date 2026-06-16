package es.schsebastian.foodrats.core.designsystem

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrStoryScaffold
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * Locks the overlay-action contract added for the recap share CTA (w3-recap-share-cta): a click
 * inside the [FrStoryScaffold.action] slot is consumed by the action and does NOT advance/rewind the
 * story, while a tap on the bare surface still drives the gesture tap-zones. This is the regression
 * the shareable-cards presentation task was blocked on — an in-scene button used to be swallowed by
 * the full-size tap-zone Row.
 */
@RunWith(AndroidJUnit4::class)
class FrStoryScaffoldTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun clicking_the_action_slot_does_not_advance_the_story() {
        var advances = 0
        var prevs = 0
        var shareClicks = 0
        rule.setContent {
            FoodRatsTheme {
                FrStoryScaffold(
                    modifier = Modifier.fillMaxSize(),
                    segmentCount = 3,
                    currentIndex = 1,
                    currentProgress = 0.5f,
                    onPrev = { prevs++ },
                    onNext = { advances++ },
                    onClose = {},
                    action = { FrButton(label = "Share", onClick = { shareClicks++ }) },
                    scene = {},
                )
            }
        }

        rule.onNodeWithText("Share").performClick()

        assertEquals(1, shareClicks, "the action button must receive the click")
        assertEquals(0, advances, "a click in the action slot must NOT advance the story")
        assertEquals(0, prevs, "a click in the action slot must NOT rewind the story")
    }

    @Test
    fun tapping_the_right_region_outside_the_action_advances() {
        var advances = 0
        rule.setContent {
            FoodRatsTheme {
                FrStoryScaffold(
                    modifier = Modifier.fillMaxSize(),
                    segmentCount = 3,
                    currentIndex = 0,
                    currentProgress = 0f,
                    onPrev = {},
                    onNext = { advances++ },
                    onClose = {},
                    action = { FrButton(label = "Share", onClick = {}) },
                    scene = {},
                )
            }
        }

        // Tap the upper-right quadrant: inside the right (advance) tap-zone, clear of the
        // bottom-anchored action slot.
        rule.onRoot().performTouchInput {
            click(Offset(width * 0.8f, height * 0.2f))
        }

        assertEquals(1, advances, "a tap on the bare right region must advance the story")
    }

    @Test
    fun composes_without_an_action_slot() {
        rule.setContent {
            FoodRatsTheme {
                FrStoryScaffold(
                    modifier = Modifier.fillMaxSize(),
                    segmentCount = 2,
                    currentIndex = 0,
                    currentProgress = 0.3f,
                    onPrev = {},
                    onNext = {},
                    onClose = {},
                    scene = {},
                )
            }
        }
        rule.onRoot().assertExists()
    }
}
