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
class FrUploadQueueBarTest {
    @get:Rule val rule = createComposeRule()

    @Test fun renders_nothing_when_idle() {
        rule.setContent {
            FoodRatsTheme {
                FrUploadQueueBar(pending = 0, failed = 0, onRetry = {}, onDismiss = {})
            }
        }
        rule.onNodeWithText("Publishing…").assertDoesNotExist()
    }

    @Test fun shows_publishing_pill_when_upload_active_even_with_zero_queued() {
        rule.setContent {
            FoodRatsTheme {
                FrUploadQueueBar(pending = 0, failed = 0, uploading = true, onRetry = {}, onDismiss = {})
            }
        }
        rule.onNodeWithText("Publishing…").assertIsDisplayed()
    }

    @Test fun shows_pending_count_when_drafts_queued() {
        rule.setContent {
            FoodRatsTheme {
                FrUploadQueueBar(pending = 2, failed = 0, onRetry = {}, onDismiss = {})
            }
        }
        rule.onNodeWithText("2 waiting to publish").assertIsDisplayed()
    }

    @Test fun failed_row_dispatches_retry_and_dismiss() {
        var retried = false
        var dismissed = false
        rule.setContent {
            FoodRatsTheme {
                FrUploadQueueBar(
                    pending = 0,
                    failed = 1,
                    onRetry = { retried = true },
                    onDismiss = { dismissed = true },
                )
            }
        }
        rule.onNodeWithText("1 failed to post").assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()
        rule.onNodeWithText("Dismiss").performClick()
        assertTrue(retried)
        assertTrue(dismissed)
    }

    @Test fun sync_bar_shows_pending_and_failed_rows() {
        var retried = 0
        rule.setContent {
            FoodRatsTheme {
                FrSyncStatusBar(pending = 3, failed = 1, onRetry = { retried++ }, onDismiss = {})
            }
        }
        rule.onNodeWithText("3 waiting to sync").assertIsDisplayed()
        rule.onNodeWithText("1 failed to sync").assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, retried)
    }
}
