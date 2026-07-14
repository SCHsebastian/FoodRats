package es.schsebastian.foodrats.core.designsystem

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.atoms.FrOfflineBanner
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrOfflineBannerTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun hidden_when_not_visible() {
        rule.setContent {
            FoodRatsTheme {
                FrOfflineBanner(visible = false, message = "You're offline")
            }
        }
        rule.onNodeWithText("You're offline").assertDoesNotExist()
    }

    @Test
    fun visible_shows_message() {
        rule.setContent {
            FoodRatsTheme {
                FrOfflineBanner(visible = true, message = "You're offline")
            }
        }
        rule.onNodeWithText("You're offline").assertIsDisplayed()
    }

    @Test
    fun reflects_the_message_it_is_given() {
        rule.setContent {
            FoodRatsTheme {
                FrOfflineBanner(visible = true, message = "No connection — changes will sync later")
            }
        }
        rule.onNodeWithText("No connection — changes will sync later").assertIsDisplayed()
    }
}
