package es.schsebastian.foodrats.feature.feed.presentation.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrFeedDayHeaderTest {
    @get:Rule val rule = createComposeRule()

    @Test fun renders_primary_and_secondary_lines() {
        rule.setContent {
            FoodRatsTheme {
                FrFeedDayHeader(
                    primaryLabel = "Today",
                    secondaryLabel = "2026-05-19",
                    sortKey = "2026-05-19",
                    canGoPrev = true,
                    canGoNext = false,
                    onPrev = {},
                    onNext = {},
                )
            }
        }
        rule.onNodeWithText("Today").assertIsDisplayed()
        rule.onNodeWithText("2026-05-19").assertIsDisplayed()
    }

    @Test fun omits_secondary_line_when_blank() {
        rule.setContent {
            FoodRatsTheme {
                FrFeedDayHeader(
                    primaryLabel = "2026-05-10",
                    secondaryLabel = "",
                    sortKey = "2026-05-10",
                    canGoPrev = true,
                    canGoNext = true,
                    onPrev = {},
                    onNext = {},
                )
            }
        }
        rule.onNodeWithText("2026-05-10").assertIsDisplayed()
    }
}
