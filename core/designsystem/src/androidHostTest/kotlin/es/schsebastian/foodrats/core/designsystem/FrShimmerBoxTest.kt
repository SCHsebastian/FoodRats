package es.schsebastian.foodrats.core.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.atoms.FrShimmerBox
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * Locks the loading-skeleton a11y fix: every FrShimmerBox (every skeleton in the app) must announce
 * an indeterminate progress state, with or without a caller-supplied label (atoms take primitives
 * only, so the label — if any — is resolved by the caller via `resolve(StringKey)`).
 */
@RunWith(AndroidJUnit4::class)
class FrShimmerBoxTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun shimmerBox_withoutLabel_stillReportsIndeterminateProgress() {
        rule.setContent {
            FoodRatsTheme {
                FrShimmerBox(modifier = Modifier.testTag("shimmer"))
            }
        }
        val node = rule.onNodeWithTag("shimmer").fetchSemanticsNode()
        assertEquals(
            ProgressBarRangeInfo.Indeterminate,
            node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo),
            "a bare FrShimmerBox must still announce as an indeterminate progress indicator",
        )
        assertEquals(
            LiveRegionMode.Polite,
            node.config.getOrNull(SemanticsProperties.LiveRegion),
        )
    }

    @Test
    fun shimmerBox_withLabel_exposesContentDescription() {
        rule.setContent {
            FoodRatsTheme {
                FrShimmerBox(contentDescription = "Loading")
            }
        }
        rule.onNodeWithContentDescription("Loading").assertExists()
    }
}
