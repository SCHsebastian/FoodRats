package es.schsebastian.foodrats.core.designsystem

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.atoms.FrStoryProgressBar
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrStoryProgressBarTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_with_content_description() {
        rule.setContent {
            FoodRatsTheme {
                FrStoryProgressBar(
                    segmentCount = 5,
                    currentIndex = 2,
                    currentProgress = 0.4f,
                    contentDescription = "Story progress",
                )
            }
        }
        rule.onNodeWithContentDescription("Story progress").assertExists()
    }

    @Test
    fun composes_with_a_single_segment_and_no_description() {
        rule.setContent {
            FoodRatsTheme {
                FrStoryProgressBar(segmentCount = 1, currentIndex = 0, currentProgress = 1f)
            }
        }
        rule.onRoot().assertExists()
    }

    @Test
    fun clamps_out_of_range_index_and_progress_without_crashing() {
        rule.setContent {
            FoodRatsTheme {
                // index past the end and progress > 1 must coerce, not throw.
                FrStoryProgressBar(
                    segmentCount = 3,
                    currentIndex = 9,
                    currentProgress = 5f,
                    contentDescription = "Clamped",
                )
            }
        }
        rule.onNodeWithContentDescription("Clamped").assertExists()
    }
}
