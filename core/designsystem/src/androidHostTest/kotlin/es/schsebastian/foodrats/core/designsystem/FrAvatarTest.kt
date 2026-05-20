package es.schsebastian.foodrats.core.designsystem

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.atoms.FrAvatar
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

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
    fun does_not_render_initials_when_imageUrl_present() {
        rule.setContent { FoodRatsTheme { FrAvatar(initials = "se", imageUrl = "https://x/a.jpg") } }
        // Coil in Robolectric won't load the image, but the initials text node must not appear
        // because the image variant replaces the text composable entirely.
        val tree = rule.onRoot().printToString()
        assertTrue(!tree.contains("SE"), "Expected no initials when imageUrl is present, got:\n$tree")
    }
}
