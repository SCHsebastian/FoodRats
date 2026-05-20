package es.schsebastian.foodrats.core.designsystem

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.atoms.FrAvatar
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrAvatarTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_initials_when_imageUrl_is_null() {
        rule.setContent { FoodRatsTheme { FrAvatar(initials = "se", imageUrl = null) } }
        rule.onNodeWithText("SE", useUnmergedTree = true).assertExists()
    }

    @Test
    fun renders_initials_when_imageUrl_is_blank() {
        rule.setContent { FoodRatsTheme { FrAvatar(initials = "se", imageUrl = "  ") } }
        rule.onNodeWithText("SE", useUnmergedTree = true).assertExists()
    }

    @Test
    fun renders_initials_as_fallback_while_image_is_loading() {
        // In Robolectric, Coil never resolves the remote URL, so the composable stays
        // in the loading state. The spec requires loading/error slots to show initials,
        // so "SE" must be visible even when an imageUrl is provided.
        rule.setContent { FoodRatsTheme { FrAvatar(initials = "se", imageUrl = "https://x/a.jpg") } }
        rule.onNodeWithText("SE", useUnmergedTree = true).assertExists()
    }

    @Test
    fun composable_does_not_crash_with_imageUrl_present() {
        // Smoke test: the root node exists and layout succeeds — no crash on composition.
        rule.setContent { FoodRatsTheme { FrAvatar(initials = "se", imageUrl = "https://example.com/avatar.jpg") } }
        rule.onRoot().assertExists()
    }
}
