package es.schsebastian.foodrats.core.designsystem

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.molecules.FrConfirmDialog
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class FrConfirmDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun confirmDialog_confirmAndDismissFire() {
        var confirmed = 0
        var dismissed = 0
        composeTestRule.setContent {
            FoodRatsTheme {
                FrConfirmDialog(
                    title = "Delete crew?",
                    message = "This permanently removes everyone.",
                    confirmLabel = "Delete",
                    dismissLabel = "Cancel",
                    onConfirm = { confirmed++ },
                    onDismiss = { dismissed++ },
                    destructive = true,
                )
            }
        }
        composeTestRule.onNodeWithText("Delete").assertExists()
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertEquals(1, dismissed, "tapping the dismiss action fires onDismiss")
        composeTestRule.onNodeWithText("Delete").performClick()
        assertEquals(1, confirmed, "tapping the confirm action fires onConfirm")
    }
}
