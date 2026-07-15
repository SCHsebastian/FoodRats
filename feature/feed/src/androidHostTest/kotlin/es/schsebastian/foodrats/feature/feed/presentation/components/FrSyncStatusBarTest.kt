package es.schsebastian.foodrats.feature.feed.presentation.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrSyncStatusBarTest {
    @get:Rule val rule = createComposeRule()

    @Test fun renders_nothing_when_idle() {
        rule.setContent {
            FoodRatsTheme {
                FrSyncStatusBar(pending = 0, failed = 0, onRetry = {}, onDismiss = {})
            }
        }
        rule.onNodeWithText("Retry").assertDoesNotExist()
        rule.onNodeWithText("Dismiss").assertDoesNotExist()
    }

    @Test fun shows_pending_pill_with_count() {
        rule.setContent {
            FoodRatsTheme {
                FrSyncStatusBar(pending = 3, failed = 0, onRetry = {}, onDismiss = {})
            }
        }
        rule.onNodeWithText("3 waiting to sync").assertIsDisplayed()
    }

    @Test fun shows_failed_tile_with_retry_and_dismiss_callbacks() {
        var retried = false
        var dismissed = false
        rule.setContent {
            FoodRatsTheme {
                FrSyncStatusBar(
                    pending = 0,
                    failed = 2,
                    onRetry = { retried = true },
                    onDismiss = { dismissed = true },
                )
            }
        }
        rule.onNodeWithText("2 failed to sync").assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()
        rule.onNodeWithText("Dismiss").performClick()
        assertTrue(retried)
        assertTrue(dismissed)
    }

    @Test fun shows_both_pending_and_failed_rows_stacked() {
        var retried = 0
        rule.setContent {
            FoodRatsTheme {
                FrSyncStatusBar(pending = 5, failed = 1, onRetry = { retried++ }, onDismiss = {})
            }
        }
        rule.onNodeWithText("5 waiting to sync").assertIsDisplayed()
        rule.onNodeWithText("1 failed to sync").assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, retried)
    }
}
