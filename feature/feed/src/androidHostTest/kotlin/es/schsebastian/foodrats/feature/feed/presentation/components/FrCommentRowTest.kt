package es.schsebastian.foodrats.feature.feed.presentation.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrCommentRowTest {
    @get:Rule val rule = createComposeRule()

    @Test fun renders_displayName_when_resolved() {
        rule.setContent {
            FoodRatsTheme {
                FrCommentRow(
                    displayName = "Sebas",
                    avatarUrl = null,
                    text = "Hola",
                    relative = RelativeTimestamp(FeedStringKey.CommentsRelativeJustNow, 0),
                )
            }
        }
        rule.onNodeWithText("Sebas").assertIsDisplayed()
        rule.onNodeWithText("Hola").assertIsDisplayed()
    }

    @Test fun renders_deleted_label_when_isDeleted() {
        rule.setContent {
            FoodRatsTheme {
                FrCommentRow(
                    displayName = "Sebas",
                    avatarUrl = null,
                    text = "Hola",
                    relative = RelativeTimestamp(FeedStringKey.CommentsRelativeJustNow, 0),
                    isDeleted = true,
                )
            }
        }
        // "Deleted user" comes from feed_deleted_author / values/strings.xml
        rule.onNodeWithText("Deleted user").assertIsDisplayed()
    }

    @Test fun renders_placeholder_when_loading() {
        rule.setContent {
            FoodRatsTheme {
                FrCommentRow(
                    displayName = "Sebas",
                    avatarUrl = null,
                    text = "Hola",
                    relative = RelativeTimestamp(FeedStringKey.CommentsRelativeJustNow, 0),
                    loading = true,
                )
            }
        }
        rule.onNodeWithText("…").assertIsDisplayed()
    }
}
